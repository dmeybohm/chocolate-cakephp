# Plugin notation in table locator calls

## Context

CakePHP names a plugin's table with a `Plugin.Table` alias, as in
`$this->fetchTable('TestPlugin.Articles')`. The plugin does not understand this form anywhere.
`fetchTable()`, `getTableLocator()->get()` and `TableRegistry::get()` share the same code, so they
share the same gaps:

| Feature | `'Articles'` today | `'TestPlugin.Articles'` today |
|---|---|---|
| Go to declaration | The app table and the same-named table in every configured plugin (`getPossibleTableClasses`) | Nothing. It looks up a class literally named `TestPlugin.ArticlesTable` |
| Return type | App namespace only, so a plugin-only table gets a class that does not exist | `\App\Model\Table\TestPlugin.ArticlesTable` |
| Completion | All `Table` subclasses, plugin ones included | Inserts the bare class name, so choosing a plugin table loses the prefix |

The dotted type is worse than an unknown one. `TableLocatorTypeProvider` packs types into
`#key.method.type` signatures and splits them on `.` (lines 105 and 182), so a dot inside the type
corrupts the signature for chained calls such as `->find()`.

The `className` option has the same problem. `['className' => 'TestPlugin.Articles']` goes through
`absoluteClassName()` and becomes `\TestPlugin.Articles`.

### Why it looks supported

`features/2025/10/add-fetch-table-goto-declaration-handler.md` says plugin notation works. That
parsing (`c22bae8`) is only on `origin/fetch-table-goto-declaration-in-plugins` and never reached
`main`. It covered go to declaration only and matched plugins with a namespace substring, so this
plan replaces it rather than merging it.

The tests named `test goto declaration works with plugin table classes` in cake4 and cake5
`FetchTableTest`, and the equivalent at `cake3/GetTableLocatorTest.kt:311`, only assert that the
result is not null. They pass when nothing is found, and they name an `Articles` table the plugin
fixture does not have.

## Goals

- `Plugin.Table` resolves to that plugin's table for go to declaration, return types and everything
  chained from them, on Cake 3, 4 and 5.
- A bare name means what the framework says it means, verified by assumption tests.
- Completion inserts a name that loads the table it displayed.
- The `className` option accepts the same forms as the alias.

Non-goals:

- Cake 2. `ClassRegistry::init()` and `loadModel()` take a different path, and the table locator
  code is gated on `cake3Enabled`.
- `fetchTable()` with no arguments, and callers other than controllers such as commands and cells.
- `loadModel()` on Cake 3 and 4, `contain()` strings and association `className` values.
- `getPossibleTableClasses` behavior for its other callers. See "Resolution" below.

## Assumption tests

These run first, because they decide the resolution rules. Per the
[assumption suite](cakephp-behavior-probes.md), each is written as the assumption the plugin would
like to make, and a failure says what the framework does instead. The vendor trees are not installed
in this checkout and none of these have been run, so every expected result below is unverified.

Add `TableLocatorTest` to `cake3`, `cake4` and `cake5` under `src/test/php`, with the same method
names on each. Cake 2 has no equivalent. Add a section to the README catalogue, relied on by
`TableLocatorTypeProvider`, `TableLocatorGotoDeclarationHandler` and
`TableLocatorCompletionContributor`.

Each test calls the real locator and asserts on `get_class()`, `getAlias()` or `getRegistryAlias()`.
The `fetchTable()` tests apply to Cake 4 and 5 only, because Cake 3 does not have the method.

| # | Assumption | Test method | What the plugin does with it |
|---|---|---|---|
| 1 | `Probe.Widgets` loads the plugin's class, with an app table of the same name present | `testPluginAliasLoadsPluginTable` | The basic rule |
| 2 | A bare `Widgets` loads the app's class, with a plugin table of the same name present | `testBareAliasLoadsAppTable` | Bare names stop reaching plugins |
| 3 | A bare `Gadgets`, only in a plugin, is a generic `Cake\ORM\Table` and does not reach the plugin | `testBareAliasDoesNotReachPluginTable` | Go to declaration finds nothing for it |
| 4 | A bare alias inside a plugin controller's `fetchTable()` still means the app's table | `testPluginControllerBareFetchTableLoadsAppTable` | Resolution ignores the calling file |
| 5 | `Probe.AppOnly`, missing from the plugin, is a generic table and does not fall back to the app | `testPluginAliasDoesNotFallBackToAppTable` | Differs from templates and elements, which do fall back |
| 6 | `Other.Widgets` from a `Probe` controller loads only from `Other` | `testPluginControllerLoadsOtherPluginTable` | No special case for cross-plugin calls |
| 7 | `Acme/Vendored.Things` loads `Acme\Vendored\Model\Table\ThingsTable` | `testVendorPrefixedAliasMapsSlashesToNamespace` | The name can derive a namespace |
| 8 | A table in an autoloadable plugin that was never loaded with `addPlugin()` still resolves | `testUnloadedPluginTableResolvesByNamespace` | The namespace comes from the name, so plugins missing from settings can work |
| 9 | `probe.Widgets` with the wrong case is a generic table | `testPluginAliasIsCaseSensitive` | Plugin name match stays exact, as in `findPluginConfigByName` |
| 10 | `Probe.Sub/Nested` and `Sub/Nested` load tables from a `Sub` namespace | `testSlashInTableNameSelectsSubNamespace` | Names map `/` to `\` |
| 11 | `Probe.Sub.Widgets` splits on the first dot and is a generic table | `testAliasSplitsOnFirstDotOnly` | Further dots produce no type |
| 12 | `get('Probe.Widgets')` has alias `Widgets` and registry alias `Probe.Widgets` | `testPluginAliasSetsShortAlias` | Confirms the two names are distinct |
| 13 | `get('Widgets')` after `get('Probe.Widgets')` returns the app's table, not the cached plugin one | `testBareAndPluginAliasesAreCachedSeparately` | Order of calls can't change a type |
| 14 | `get('Foo', ['className' => 'Probe.Widgets'])` loads the plugin's class with alias `Foo` | `testClassNameOptionAcceptsPluginNotation` | Option uses the alias rules |
| 15 | `get('Foo', ['className' => WidgetsTable::class])` loads that class | `testClassNameOptionAcceptsFullyQualifiedClass` | Existing behavior is right |
| 16 | `className` wins over a `Plugin.Table` alias | `testClassNameOptionWinsOverAlias` | Keeps the second parameter's priority |
| 17 | A fully qualified class string as the alias loads that class | `testFullyQualifiedAliasLoadsClass` | Handle `\` in the first parameter |
| 18 | A plugin table's entity class is the plugin's | `testPluginTableUsesPluginEntity` | `tableToEntityClass()` is right for plugin tables |
| 19 | A plugin table with no plugin entity uses `Cake\ORM\Entity`, even if the app has one of that name | `testPluginTableDoesNotFallBackToAppEntity` | `->get()` on a table with no entity class gets no app entity type |
| 20 | `TableRegistry::get('Probe.Widgets')` on Cake 3 loads the plugin's class | `testStaticRegistryGetLoadsPluginTable` | Static form behaves the same; Cake 3 only |

### Fixtures

In each version's app, in that version's layout:

- `Probe\Model\Table`: `WidgetsTable`, `GadgetsTable`, `Sub\NestedTable`
- `Probe\Model\Entity\Widget`
- `Other\Model\Table\WidgetsTable`
- `App\Model\Table`: `WidgetsTable`, `AppOnlyTable`, `Sub\NestedTable`
- `App\Model\Entity\Gadget`, for test 19
- `Acme\Vendored`, a plugin at `plugins/Acme/Vendored`, loaded as `Acme/Vendored`, with `ThingsTable`
- `Unloaded`, autoloaded by `composer.json` and never added, with `ThingsTable`

Each class has a distinct name in its namespace, so the assertion on `get_class()` is enough, and
none needs a database table. Locators need a connection to attach, so each `tests/bootstrap.php`
gets an in-memory SQLite `default` and `test` datasource if it lacks one. New autoload namespaces
need a `composer dump-autoload` in the image.

## Design

### Resolution

Add one pure function to `cake/CakeModel.kt`:

```kotlin
fun tableClassFqnFromAlias(settings: Settings, alias: String): String?
```

It uses only settings, never the index, so `getType()` can call it. Provisional rules, each tied to
the tests above:

1. Empty or too long: `null`.
2. Contains `\`: the absolute class name (17).
3. Split on the first `.` (11). Without a dot, the namespace is `settings.appNamespace` (2, 3, 4).
4. With a dot, the namespace is the configured namespace from `findPluginConfigByName()` if there is
   one, and otherwise the plugin part with `/` replaced by `\` (7, 8).
5. A table part containing another `.` returns `null` (11), so no dot ever reaches a signature.
6. Replace `/` with `\` in the table part (10), and return
   `<namespace>\Model\Table\<table>Table`.

A configured namespace wins over the derived one so that an explicit setting is respected, which
matches `ConfiguredMemberService.resolveLoad`. Unlike that method, an unknown plugin does not fall
back to searching every namespace (5).

`getPossibleTableClasses` stays as it is. `ContainGotoDeclarationHandler`,
`ControllerFieldTypeProvider` and `AssociatedTableTypeProvider` use it for association and field
names, which are not table locator aliases. Line 221 of `TableLocatorTypeProvider` also keeps it,
because there it handles field chains such as `$this->Movies->Articles->get()`, not aliases.

### Type provider

`getTableClassFromFirstParam` calls `tableClassFqnFromAlias`, and returns no type when it returns
`null`. `getTableClassFromSecondParam` applies the same function to string values, while `::class`
values keep using `getStringifiedClassOrNull()`. Entity types need no change if tests 18 and 19 hold,
apart from a test.

### Go to declaration

`TableLocatorGotoDeclarationHandler` replaces its `getPossibleTableClasses` call with
`getClassesByFQN(tableClassFqnFromAlias(...))`. Move the choice between the two parameters out of the
type provider into a shared function, so that navigating from the alias of
`fetchTable('Foo', ['className' => 'Probe.Widgets'])` reaches the same class the type names.

This is a behavior change: a bare name no longer navigates to a plugin's table. It belongs in the
changelog.

### Completion

Build the lookup string from the class FQN, following `CellCompletionContributor`:

- Match against the whole literal up to the caret with `withPrefixMatcher()`, because the default
  prefix stops at `.` and `/`.
- App tables complete as `Widgets`, and configured plugin tables as `Probe.Widgets`, using
  `effectivePluginName()`.
- Tables in a sub-namespace complete as `Sub/Nested`.
- Tables outside the app and configured plugin namespaces are left out, since a bare name would not
  load them and their plugin name is unknown. Cell completion does the same.

`completeMethodCallWithParameterFromClasses` has only this one caller, but it stays as it is and the
contributor adds its own elements, because the name no longer comes from `klass.name`. If it ends up
unused, remove it in the same commit.

Verify that `CompleteStringParameter` replaces the full typed prefix, including the dot.

## Implementation steps

1. Assumption fixtures and `TableLocatorTest` on Cake 5, then 4 and 3. Run them with
   `scripts/run-cakephp-assumption-tests.sh test 3 4 5 -- --filter TableLocator`, record the results
   in the README catalogue, and revise the resolution rules here for any that differ.
2. `tableClassFqnFromAlias`, with the type provider and its `className` handling. Cake 5 tests first.
3. Go to declaration.
4. Completion.
5. Cake 4 and 3 plugin tests, then the full Gradle suite.
6. `CHANGELOG.md`, and a correction in `add-fetch-table-goto-declaration-handler.md` pointing here.

Commit after each step.

## Plugin tests

Plugin fixtures need one addition, a `TestPlugin\Model\Entity\PluginItem`. `RatingsTable` in the
Cake 5 app and plugin already covers a name that exists in both; check the same for the Cake 3 and 4
fixtures and add what is missing.

Replace the empty tests above with ones that assert the target, using `TestPlugin.PluginItems`, in
`cake3/GetTableLocatorTest` and cake4 and cake5 `FetchTableTest`.

Add these on Cake 5 first, then 4 and 3, using `getTableLocator()->get()` where Cake 3 has no
`fetchTable()`:

- Go to declaration: `TestPlugin.PluginItems` reaches the plugin's file. `TestPlugin.Ratings` reaches
  only the plugin's, and bare `Ratings` only the app's. Bare `PluginItems` and `Unknown.PluginItems`
  find nothing. The `className` option form navigates to its class.
- Types: `fetchTable('TestPlugin.PluginItems')->` completes the plugin table's methods.
  `->find()` and `->get()` chains keep the right type and entity, which also covers the signature
  corruption.
- A plugin configured with an explicit `pluginName` different from its namespace's last segment
  resolves through the configured namespace. An unconfigured `Acme/Vendored.Things` resolves through
  the derived namespace.
- Malformed aliases give no type and don't throw: `TestPlugin.`, `.PluginItems`, `A.B.C`.
- Completion: the list holds `Ratings` and `TestPlugin.Ratings`. Choosing the second inserts it in
  full. After typing `TestPlugin.`, the plugin tables are offered and the result has no doubled
  prefix.

## Open questions

Whether test 8 holds decides how far unconfigured plugins go. If an unloaded plugin's table does not
resolve at runtime, the derived namespace stays, because the plugin can't tell which plugins are
loaded, but go to declaration may then offer a class the framework would not load.

## Implementation Progress

### Session #1

Plan written, on branch `fetch-table-plugin-notation`. No code or tests have been written yet.
