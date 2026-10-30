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
- `fetchTable()` with no arguments, and callers other than controllers and cells, such as commands.
- `loadModel()` on Cake 3 and 4, `contain()` strings and association `className` values.
- `getPossibleTableClasses` behavior for its other callers. See "Resolution" below.
- Falling back to `Cake\ORM\Entity` for a table with no entity class. `tableToEntityClass()` builds
  the name without checking that the class exists, for app and plugin tables alike. This is an
  existing gap, and this feature leaves it as it is.

## Assumption tests

These run first, because they decide the resolution rules. Per the
[assumption suite](cakephp-behavior-probes.md), each is written as the assumption the plugin would
like to make, and a failure says what the framework does instead. All are implemented and pass. The
table below records what the suite verified, and Session #2 lists where it differed from the plan.

Add `TableLocatorTest` to `cake3`, `cake4` and `cake5` under `src/test/php`, with the same method
names on each. Cake 2 has no equivalent. Add a section to the README catalogue, relied on by
`TableLocatorTypeProvider`, `TableLocatorGotoDeclarationHandler` and
`TableLocatorCompletionContributor`.

Each test asserts on `get_class()`, `getAlias()` or `getRegistryAlias()`. Most call the real locator
directly. Tests 4, 6, 22 and 23 are about the caller, so a direct locator call can't establish them:
they call a method on `Probe\Controller\ThingsController` or `Probe\View\Cell\GadgetCell`, and that
method makes the call from inside the class. The `fetchTable()` tests apply to Cake 4 and 5 only,
because Cake 3 does not have the method, so test 6 uses `$this->getTableLocator()->get()` on Cake 3.

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
| 9 | `probe.Cased` with a lowercase plugin part is a generic table on Cake 4 and 5, but resolves on Cake 3, which camelizes the alias | `testLowercasePluginAliasResolution` | Plugin name match stays exact, as in `findPluginConfigByName`. The Cake 3 case is not modelled |
| 10 | `Probe.Sub/Nested` and `Sub/Nested` load tables from a `Sub` namespace | `testSlashInTableNameSelectsSubNamespace` | Names map `/` to `\` |
| 11 | `Probe.Sub.Widgets` splits on the first dot and is a generic table | `testAliasSplitsOnFirstDotOnly` | Further dots produce no type |
| 12 | `get('Probe.Widgets')` has alias `Widgets` and registry alias `Probe.Widgets` | `testPluginAliasSetsShortAlias` | Confirms the two names are distinct |
| 13 | `get('Widgets')` after `get('Probe.Widgets')` returns the app's table, not the cached plugin one | `testBareAndPluginAliasesAreCachedSeparately` | Order of calls can't change a type |
| 14 | `get('Foo', ['className' => 'Probe.Widgets'])` loads the plugin's class with alias `Foo` | `testClassNameOptionAcceptsPluginNotation` | Option uses the alias rules |
| 15 | `get('Foo', ['className' => WidgetsTable::class])` loads that class | `testClassNameOptionAcceptsFullyQualifiedClass` | Existing behavior is right |
| 16 | `className` wins over a `Plugin.Table` alias | `testClassNameOptionWinsOverAlias` | Keeps the second parameter's priority |
| 17 | A fully qualified class string as the alias loads that class | `testFullyQualifiedAliasLoadsClass` | Handle `\` in the first parameter |
| 18 | A plugin table's entity class is the plugin's | `testPluginTableUsesPluginEntity` | `tableToEntityClass()` is right for plugin tables |
| 19 | A plugin table with no plugin entity uses `Cake\ORM\Entity`, even if the app has one of that name | `testPluginTableDoesNotFallBackToAppEntity` | A plugin table never gets the app's entity type. The fallback to `Cake\ORM\Entity` is a non-goal |
| 20 | `TableRegistry::get('Probe.Widgets')` on Cake 3 loads the plugin's class | `testStaticRegistryGetLoadsPluginTable` | Static form behaves the same; Cake 3 only |
| 21 | `.Widgets`, with an empty plugin part, loads the app's table | `testEmptyPluginSegmentLoadsAppTable` | Rule 5 below treats it as a bare alias |
| 22 | A bare alias inside a plugin cell's `fetchTable()` still means the app's table | `testPluginCellBareFetchTableLoadsAppTable` | Cells follow the same rules as controllers; Cake 4 and 5 only |
| 23 | `Other.Widgets` inside a `Probe` cell's `fetchTable()` loads only from `Other` | `testPluginCellLoadsOtherPluginTable` | No special case for cross-plugin calls in cells; Cake 4 and 5 only |

### Fixtures

In each version's app, in that version's layout:

- `Probe\Model\Table`: `WidgetsTable`, `GadgetsTable`, `CasedTable`, `Sub\NestedTable`. No other test
  loads `CasedTable`, because PHP ignores case for a class that is already loaded
- `Probe\Model\Entity\Widget`
- `Other\Model\Table\WidgetsTable`
- `App\Model\Table`: `WidgetsTable`, `AppOnlyTable`, `Sub\NestedTable`
- `App\Model\Entity\Gadget`, for test 19
- `Acme\Vendored`, a plugin at `plugins/Acme/Vendored`, loaded as `Acme/Vendored`, with `ThingsTable`
- `Unloaded`, autoloaded by `composer.json` and never added, with `ThingsTable`
- A method on the existing `ThingsController`, and on the existing `GadgetCell` on Cake 4 and 5, that
  loads the alias it is given

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

It uses only settings, never the index, so `getType()` can call it. The rules, each tied to the tests
above:

1. Empty or too long: `null`.
2. Contains `\`: the absolute class name (17).
3. Split on the first `.` (11). Without a dot, the namespace is `settings.appNamespace` (2, 3, 4).
4. With a dot, the namespace is the configured namespace from `findPluginConfigByName()` if there is
   one, and otherwise the plugin part with `/` replaced by `\` (7, 8).
5. An empty plugin part is the bare alias, so `.Widgets` means `Widgets` (21).
6. An empty table part, or a table part containing another `.` (11), returns `null`, so no dot ever
   reaches a signature.
7. Replace `/` with `\` in the table part (10), and return
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
values keep using `getStringifiedClassOrNull()`. Entity typing does not change.

The resolver is pure, so it still names a class for a table that does not exist: bare `Gadgets`
(test 3), `Probe.AppOnly` (test 5) and misspelled names. The framework gives a generic table for
these. Generic `Table` methods still complete alongside the nonexistent class, as the plugin tests
show on Cake 5, so the type provider does not defer an existence check to `complete()`.

Known limitation: if the app has `App\Model\Entity\Gadget`, then `fetchTable('Gadgets')->get(1)` is
typed as that entity for a table that does not exist.

Aliases are not camelized. Cake 3 camelizes an alias before resolving it, so `probe.Cased`, `appOnly`
and `app_only` load tables there and generic tables on Cake 4 and 5. Matching this needs the plugin to
tell Cake 3 from later versions, which the settings don't do, and camelizing on every version would
give wrong answers on Cake 4 and 5. Those Cake 3 aliases get no accurate type or navigation.

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

Plugin fixtures need a `TestPlugin\Model\Entity\PluginItem` and an `App\Model\Entity\Rating`, with no
plugin `Rating` entity. `RatingsTable` in the Cake 5 app and plugin already covers a name that exists
in both; check the same for the Cake 3 and 4 fixtures and add what is missing.

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
- Cells: in `CellFetchTableTest` on Cake 5 and `CellTest` on Cake 4, cells share the table locator
  code, so cover it from both an app cell and a plugin cell under `TestPlugin\View\Cell`.
  `TestPlugin.PluginItems` gives the plugin's type and navigates to it, bare `Ratings` gives only
  the app's, and completion offers `TestPlugin.PluginItems`. On Cake 3, cover the same cases
  through `$this->getTableLocator()->get()`, because its cells have no `fetchTable()`. The Cake 3
  stub `Cell` (`cake3/vendor/cakephp.php:53`) lacks that method, so it gains `LocatorAwareTrait`, as
  the real class has.
- Malformed aliases give no type and don't throw: `TestPlugin.` and `A.B.C`. `.Ratings` follows
  rule 5 and gives the app's table.
- Missing tables: `fetchTable('PluginItems')->` and `fetchTable('TestPlugin.Missing')->` offer generic
  `Table` methods. A failure here triggers the `complete()` step in "Type provider".
- Entities: `fetchTable('TestPlugin.Ratings')->get(1)` is not typed as `App\Model\Entity\Rating`.
- Completion: the list holds `Ratings` and `TestPlugin.Ratings`. Choosing the second inserts it in
  full. After typing `TestPlugin.`, the plugin tables are offered and the result has no doubled
  prefix.

## Open questions

None. Test 8 settled the one question from planning: a table in a plugin that was never loaded still
resolves, so the derived namespace matches the framework for plugins missing from settings.

## Implementation Progress

### Session #1

Plan written, on branch `fetch-table-plugin-notation`. No code or tests have been written yet.

### Session #2

Step 1 is done. `TableLocatorTest` passes on Cake 3, 4 and 5, with results in the README catalogue.
Steps 2 to 6 have not started.

Two results differed from the plan:

- `.Widgets` loads the app's table on all three versions, because `App::className()` uses the app
  namespace when the plugin part is empty. Rule 5 treats it as a bare alias, and the test is now
  `testEmptyPluginSegmentLoadsAppTable`.
- `probe.Cased` resolves on Cake 3 and is a generic table on Cake 4 and 5. Cake 3's
  `TableLocator::_getClassName()` runs `Inflector::camelize()` on the alias first. The test is now
  `testLowercasePluginAliasResolution`, and the plugin does not model this.

The other assumptions held as written, including no fallback from a `Plugin.Table` alias to the app,
which differs from templates and elements. `CasedTable` was added because PHP matches an
already-loaded class regardless of case, so reusing `WidgetsTable` would have made the test depend
on order.

### Session #3

Step 2 is done. `tableClassFqnFromAlias` is in `cake/CakeModel.kt`, and `TableLocatorTypeProvider`
uses it for the alias and for string `className` values. `TableLocatorPluginNotationTest` covers it on
Cake 5. Go to declaration and completion still behave as before, and the Cake 3 and 4 plugin tests
are still to come in step 5.

- The missing-table tests pass, so nothing was added for `complete()`.
- A `className` string the resolver rejects, such as `A.B.C`, falls through to the alias, as a
  non-string `className` value already did.
