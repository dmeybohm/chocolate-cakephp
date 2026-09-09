# Alternative to the `cake2Enabled` / `cake3Enabled` boolean soup

Status: idea, not scheduled. Written 2026-09-08.

## The problem

Version knowledge in the plugin is split across two representations that
don't talk to each other:

- `TemplatesDir` is a sealed hierarchy (`CakeTwoTemplatesDir`,
  `CakeThreeTemplatesDir`, `CakeFourTemplatesDir`) that carries a
  per-version `elementDirName`. `ViewVariableTypeProvider` recovers the
  version number by pattern-matching on it.
- `Settings` exposes two booleans, `cake2Enabled` and `cake3Enabled`, and
  every caller re-derives what those booleans mean.

There are 57 sites checking the booleans. They fall into three kinds:

1. **Accumulate per version.** `Classes.kt` and
   `allTemplatePathsFromTopSourceDirectory` in `CakePaths.kt`:
   `if (cake2Enabled) add X; if (cake3Enabled) add Y`. Eleven near-identical
   function bodies in `Classes.kt` alone.
2. **Probe per version.** `templatesDirectoryOfViewFile` and
   `appOrSrcDirectoryFromSourceFile` in `CakePaths.kt`: walk up the
   directory tree and try each version's directory shape at each level.
   The Cake 2 plugin and theme special cases are inlined into the same loop
   that handles Cake 3 and 4.
3. **Gate a feature.** Fourteen sites in `model/` do
   `if (!settings.cake3Enabled) return`. What is actually meant is "this
   feature needs the ORM", not "the user ticked the Cake 3 box".

The deeper issue is that `cake3Enabled` conflates two different questions:
which directory layouts to probe, and which language features exist. When
Cake 6 arrives, the only options are a third boolean or a reinterpretation
of the second one.

## The alternative: make each version a value

Introduce a `CakeFlavor` interface. Each flavor owns the facts that are
currently scattered, and `Settings` hands out a list of the enabled ones.

```kotlin
sealed interface CakeFlavor {
    // Directory layout
    val appDirectory: String
    val templateExtension: String
    fun mainTemplateDirs(topDir: VirtualFile): List<TemplatesDir>
    fun templatesDirIfMatches(child: VirtualFile, parent: VirtualFile?, fileName: String): TemplatesDir?
    fun topSourceDirIfMatches(dir: VirtualFile): TopSourceDirectory?

    // Class model
    val helperParentClass: String
    val componentParentClass: String
    val modelParentClass: String
    fun helperTypeNames(fieldName: String): List<String>
    fun controllerTypeNames(name: String): List<String>
    val viewClassName: String

    // Capabilities, so feature gates say what they need rather than which version
    val hasOrm: Boolean
}
```

Each flavor is built from the same `SettingsState` that drives the booleans
today, so nothing changes in the persisted settings format:

```kotlin
class Cake2(private val s: SettingsState) : CakeFlavor {
    override val appDirectory get() = s.cake2AppDirectory
    override val templateExtension get() = s.cake2TemplateExtension
    override val hasOrm = false
    override val helperParentClass = "\\AppHelper"
    override val viewClassName = "\\AppView"
    override fun helperTypeNames(fieldName: String) = listOf("\\${fieldName}Helper")
    override fun controllerTypeNames(name: String) = listOf("\\${name}Controller")

    override fun mainTemplateDirs(topDir: VirtualFile) =
        listOfNotNull(findRelativeFile(topDir, "View")?.let(::CakeTwoTemplatesDir))

    override fun templatesDirIfMatches(child: VirtualFile, parent: VirtualFile?, fileName: String): TemplatesDir? {
        if (!fileName.endsWith(templateExtension)) return null
        // The app/View, plugin View, and Themed/MyTheme special cases live
        // here, and nowhere else.
        ...
    }
}

class Cake3Plus(private val s: SettingsState) : CakeFlavor {
    override val appDirectory get() = s.appDirectory
    override val hasOrm = true
    override val helperParentClass = "\\Cake\\View\\Helper"
    override val viewClassName get() = "${s.appNamespace}\\View\\AppView"

    override fun helperTypeNames(fieldName: String) =
        listOf(
            "\\Cake\\View\\Helper\\${fieldName}Helper",
            "${s.appNamespace}\\View\\Helper\\${fieldName}Helper",
        ) + s.pluginConfigs.map { "${it.namespace}\\View\\Helper\\${fieldName}Helper" }

    override fun mainTemplateDirs(topDir: VirtualFile) = listOfNotNull(
        findRelativeFile(topDir.parent, "templates")?.let(::CakeFourTemplatesDir),
        findRelativeFile(topDir, "Template")?.let(::CakeThreeTemplatesDir),
    )
    ...
}
```

`Settings` exposes one derived property. The list order is the search order,
made explicit instead of being an emergent property of which `if` comes first
in each of twenty functions:

```kotlin
val flavors: List<CakeFlavor>
    get() = listOfNotNull(
        if (cake3Enabled) Cake3Plus(state) else null,
        if (cake2Enabled) Cake2(state) else null,
    )

val ormFlavor: CakeFlavor? get() = flavors.firstOrNull { it.hasOrm }
```

## What each kind of call site becomes

### Accumulation collapses to a `flatMap`

`viewHelperTypeFromFieldName` and `getViewHelperSubclasses` in `Classes.kt`
are twelve lines each today with duplicated if-blocks:

```kotlin
fun viewHelperTypeFromFieldName(settings: Settings, fieldName: String): PhpType =
    settings.flavors.flatMap { it.helperTypeNames(fieldName) }.toPhpType()

fun PhpIndex.getViewHelperSubclasses(settings: Settings): Collection<PhpClass> =
    settings.flavors.flatMapTo(mutableSetOf()) { getAllSubclassesRecursively(it.helperParentClass) }
```

### Probing becomes a walk that asks each flavor in turn

`templatesDirectoryOfViewFile` is 68 lines today:

```kotlin
fun templatesDirectoryOfViewFile(project: Project, settings: Settings, file: VirtualFile): TemplatesDir? {
    val projectDir = project.guessProjectDir() ?: return null
    return generateSequence(file.parent) { it.parent }
        .takeWhile { it != projectDir }
        .firstNotNullOfOrNull { child ->
            settings.flavors.firstNotNullOfOrNull { it.templatesDirIfMatches(child, child.parent, file.name) }
        }
}
```

### Feature gates say what they need

The fourteen `if (!settings.cake3Enabled) return` lines in `model/` become:

```kotlin
val flavor = settings.ormFlavor ?: return
```

## Why this is better than just tidier

- Adding a version means adding a class and deciding which capabilities it
  declares. No existing call site changes.
- "Which layouts to probe" and "which features exist" become separate
  questions with separate answers.
- Search order is written down once.
- It finishes a design the codebase already started. `TemplatesDir` already
  carries per-version facts on the view side; flavors are the controller-side
  and settings-side twin of that type. The four version test bases under
  `src/test/kotlin/.../test/cake{2,3,4,5}/` are effectively one fixture per
  flavor.

## Costs and open questions

- `Cake3Plus` still has to probe for both `templates` and `Template`, so the
  3-versus-4 distinction stays inside one flavor. If that ever bothers us it
  splits cleanly into `Cake3` and `Cake4Plus`, because the interface is
  already per-flavor.
- The view-variable cache and index keys embed a version number.
  `ViewVariableTypeProvider` currently derives it with a `when` over the
  `TemplatesDir` subclasses. A `versionTag` on the flavor would be the
  natural home, but the index key format must stay stable or the index
  version must be bumped.
- Sites that need the *specific* template dir flavor (element directory
  name, extension) would keep using `TemplatesDir`. The two hierarchies
  should probably reference each other rather than merge, since one
  `CakeFlavor` can own several `TemplatesDir` kinds.
- Migration can be incremental: introduce `flavors` alongside the booleans,
  convert `Classes.kt` first (lowest risk, highest duplication), then the
  `model/` gates, then `CakePaths.kt` last since it holds the Cake 2 theme
  and plugin logic.
