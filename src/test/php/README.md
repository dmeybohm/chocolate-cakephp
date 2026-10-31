# CakePHP assumption tests

Integration tests that record what real, pinned CakePHP releases do at runtime, so the plugin's
behavior is built on verified framework behavior. They test the framework, not the plugin, and
Gradle does not run them.

## Running

The only requirement is Podman or Docker. Images and dependencies are set up on first use.

```bash
scripts/run-cakephp-assumption-tests.sh test                      # every version
scripts/run-cakephp-assumption-tests.sh test 5                    # one version
scripts/run-cakephp-assumption-tests.sh test 4 5 -- --filter Element
scripts/run-cakephp-assumption-tests.sh check-pins                # compare pins with build.gradle.kts
```

## Adding an assumption

1. Add one test method named for the behavior, using the same class and method name on every
   version where the behavior applies. Write it as the assumption the plugin would like to make.
   If it fails, the failure says what the framework does instead.
2. Add any templates or plugin files the test needs. Templates print a marker naming their own
   file, so an assertion identifies exactly what rendered.
3. Add a row to the catalogue below.

## Moving a pin

Change the version in `build.gradle.kts` and in `cakeN/composer.json`, then run `composer update`
in the image. A failing test after that is a behavior change to review.

## Advisory exceptions

The Cake 2 and 3 manifests allow specific security advisories, by ID, on their locked packages,
because Composer otherwise refuses to install these end-of-life releases. The releases only run in
a test container and are never shipped. A new advisory stops the next resolve until it is reviewed.

## Catalogue

"Same" means the behavior holds on all four versions.

### `TemplateResolutionTest`

Relied on by view navigation, the controller action line marker and view file creation.

| Behavior | Test method | Versions |
|---|---|---|
| A plugin controller's implicit template comes from the plugin | `testPluginControllerRendersImplicitTemplateFromPlugin` | Same |
| A plugin controller's unprefixed `render()` finds the plugin's template | `testPluginControllerRendersUnprefixedTemplateFromPlugin` | Same |
| A plugin controller's template missing from the plugin falls back to the app's template of the same name | `testPluginControllerFallsBackToAppTemplate` | Same |
| The app's override directory wins over the plugin's own template | `testAppOverrideWinsOverPluginTemplate` | Same |
| A `Plugin.name` `render()` missing from the plugin falls back to the app's template | `testPrefixedRenderFallsBackToAppTemplate` | Same |
| The app's override directory wins for a `Plugin.name` `render()` | `testAppOverrideWinsForPrefixedRender` | Same |
| A plugin controller's `render()` of another plugin finds that plugin's template | `testPluginControllerRendersOtherPluginTemplate` | Same |
| A `render()` of another plugin falls back to the app's template | `testRenderOfOtherPluginFallsBackToAppTemplate` | Same |
| A `render()` of another plugin does not fall back to the calling plugin | `testRenderOfOtherPluginDoesNotFallBackToCallingPlugin` | Same |
| The app's override directory wins for a `render()` of another plugin | `testAppOverrideWinsForRenderOfOtherPlugin` | Same |
| An app controller's unprefixed `render()` does not reach a plugin's template | `testAppControllerDoesNotReachPluginTemplateUnprefixed` | Same |
| An app controller's `Plugin.name` `render()` finds the plugin's template | `testAppControllerRendersPrefixedPluginTemplate` | Same |
| A layout named from a template wraps that template | `testLayoutNamedFromTemplateWraps` | Same |
| A routing prefix selects its own template | `testRoutingPrefixResolvesToPrefixTemplate` | 3, 4, 5: `Admin/Widgets/index`. 2: `Widgets/admin_index`, with no subdirectory |

### `ElementResolutionTest`

Relied on by element navigation and element related usages.

| Behavior | Test method | Versions |
|---|---|---|
| A plugin template prefers the plugin's element | `testPluginTemplatePrefersPluginElement` | Same |
| A plugin template falls back to the app's element | `testPluginTemplateFallsBackToAppElement` | Same |
| An app template's unprefixed element does not reach a plugin | `testAppTemplateDoesNotReachPluginElementUnprefixed` | 3, 4, 5: `MissingElementException`. 2: a notice, with the page still rendered |
| An app template's `Plugin.name` element finds the plugin's element | `testAppTemplateRendersPrefixedPluginElement` | Same |
| A `Plugin.name` element missing from the plugin falls back to the app's element | `testPrefixedElementFallsBackToAppElement` | Same |
| A plugin template's element of another plugin finds that plugin's element | `testPluginTemplateRendersOtherPluginElement` | Same |
| An element of another plugin falls back to the app's element | `testOtherPluginElementFallsBackToAppElement` | Same |
| An element of another plugin does not fall back to the calling plugin | `testOtherPluginElementDoesNotFallBackToCallingPlugin` | 3, 4, 5: `MissingElementException`. 2: a notice, with the page still rendered |
| An unprefixed element in an app template rendered by a plugin controller is the plugin's | `testElementInFallbackTemplateResolvesInControllerPlugin` | Same |
| An unprefixed element in a plugin template rendered by an app controller is the app's | `testElementInPrefixedTemplateResolvesInControllerPlugin` | Same |
| An unprefixed element in another plugin's template is the controller plugin's | `testElementInOtherPluginTemplateResolvesInControllerPlugin` | Same |

### `CellTest`

Relied on by cell navigation, the cell action line marker, cell view variables and view helper
resolution. Cells do not
exist on CakePHP 2, so "Same" here means 3, 4 and 5.

| Behavior | Test method | Versions |
|---|---|---|
| A plugin cell's template comes from the plugin | `testPluginCellRendersTemplateFromPlugin` | Same |
| A plugin cell's template missing from the plugin falls back to the app's | `testPluginCellFallsBackToAppTemplate` | Same |
| The app's override directory wins over a plugin cell's template | `testAppOverrideWinsOverPluginCellTemplate` | Same |
| An app cell does not reach a plugin's template | `testAppCellDoesNotReachPluginTemplate` | Same |
| An app template's unprefixed `cell()` does not reach a plugin's cell | `testAppTemplateDoesNotReachPluginCellUnprefixed` | Same |
| A plugin template's unprefixed `cell()` does not reach that plugin's own cell | `testPluginTemplateDoesNotReachOwnPluginCellUnprefixed` | Same |
| A plugin template's unprefixed `cell()` finds the app's cell | `testPluginTemplateRendersAppCellUnprefixed` | Same |
| A plugin template's `cell()` of another plugin finds that plugin's cell | `testPluginTemplateRendersOtherPluginCell` | Same |
| Another plugin's cell does not fall back to the calling plugin's template | `testOtherPluginCellDoesNotFallBackToCallingPlugin` | Same |
| An unprefixed element in a plugin cell's template is the cell plugin's | `testElementInPluginCellTemplateResolvesInCellPlugin` | Same |
| An unprefixed element in a plugin cell's template found in the app is the cell plugin's | `testElementInFallbackCellTemplateResolvesInCellPlugin` | Same |
| An unprefixed element in a plugin cell called from another plugin's template, under an app controller, is the cell plugin's | `testElementInCellCalledFromOtherPluginTemplateResolvesInCellPlugin` | Same |
| An unprefixed element in an app cell called under a plugin controller is the app's | `testElementInAppCellCalledFromPluginResolvesInApp` | Same |
| An unprefixed element in a cell template named with `render()` is the cell plugin's | `testElementInNamedCellTemplateResolvesInCellPlugin` | Same |
| An element missing from a plugin cell's plugin falls back to the app's | `testElementInPluginCellFallsBackToAppElement` | Same |
| A helper a controller adds through `viewBuilder()` reaches its template | `testControllerHelperReachesItsTemplate` | Same |
| A controller's helper reaches the template of a cell called from its view | `testControllerHelperReachesCellTemplate` | Same |
| A controller's helper reaches an element in that cell's template | `testControllerHelperReachesElementInCellTemplate` | Same |
| A cell template lacks a helper its caller's controller did not add | `testCellTemplateHasNoHelperItsCallerLacks` | Same |

### `TableLocatorTest`

Relied on by table locator types, navigation and completion. CakePHP 2 has no table locator, so
"Same" here means 3, 4 and 5. `fetchTable()` does not exist on Cake 3, so its controller and cell
tests are 4 and 5 only.

| Behavior | Test method | Versions |
|---|---|---|
| A `Plugin.Table` alias loads the plugin's table, with an app table of the same name present | `testPluginAliasLoadsPluginTable` | Same |
| A bare alias loads the app's table, with a plugin table of the same name present | `testBareAliasLoadsAppTable` | Same |
| A bare alias of a table only in a plugin is a generic `Table` | `testBareAliasDoesNotReachPluginTable` | Same |
| A bare `fetchTable()` in a plugin controller loads the app's table | `testPluginControllerBareFetchTableLoadsAppTable` | 4, 5 |
| A `Plugin.Table` alias missing from the plugin is a generic `Table`, with no fallback to the app | `testPluginAliasDoesNotFallBackToAppTable` | Same |
| A plugin controller's alias of another plugin loads that plugin's table | `testPluginControllerLoadsOtherPluginTable` | Same |
| A `Vendor/Plugin.Table` alias maps the slash to a namespace separator | `testVendorPrefixedAliasMapsSlashesToNamespace` | Same |
| The last namespace segment of a vendor-prefixed plugin is a generic `Table`, because the plugin part is read as a namespace | `testLastSegmentOfVendorPluginIsGenericTable` | Same |
| A table in an autoloadable plugin that was never loaded still resolves | `testUnloadedPluginTableResolvesByNamespace` | Same |
| A lowercase plugin part in an alias | `testLowercasePluginAliasResolution` | 3: resolves, because the alias is camelized first. 4, 5: a generic `Table` |
| A slash in the table part selects a sub-namespace, with or without a plugin | `testSlashInTableNameSelectsSubNamespace` | Same |
| An alias splits on its first dot only, so further dots give a generic `Table` | `testAliasSplitsOnFirstDotOnly` | Same |
| A `Plugin.Table` table has the short alias and the full registry alias | `testPluginAliasSetsShortAlias` | Same |
| A bare alias and a `Plugin.Table` alias of the same name are cached separately | `testBareAndPluginAliasesAreCachedSeparately` | Same |
| The `className` option accepts plugin notation and keeps the given alias | `testClassNameOptionAcceptsPluginNotation` | Same |
| The `className` option accepts a fully qualified class | `testClassNameOptionAcceptsFullyQualifiedClass` | Same |
| The `className` option wins over a `Plugin.Table` alias | `testClassNameOptionWinsOverAlias` | Same |
| A fully qualified class as the alias loads that class | `testFullyQualifiedAliasLoadsClass` | Same |
| A plugin table uses the plugin's entity | `testPluginTableUsesPluginEntity` | Same |
| A plugin table with no entity uses `Cake\ORM\Entity`, not the app's entity of that name | `testPluginTableDoesNotFallBackToAppEntity` | Same |
| `TableRegistry::get()` with a `Plugin.Table` alias loads the plugin's table | `testStaticRegistryGetLoadsPluginTable` | 3 |
| An alias with an empty plugin part, `.Table`, loads the app's table | `testEmptyPluginSegmentLoadsAppTable` | Same |
| A bare `fetchTable()` in a plugin cell loads the app's table | `testPluginCellBareFetchTableLoadsAppTable` | 4, 5 |
| A plugin cell's `fetchTable()` of another plugin loads that plugin's table | `testPluginCellLoadsOtherPluginTable` | 4, 5 |
