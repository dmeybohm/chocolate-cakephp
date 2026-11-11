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

## Composer-installed plugins

`cakeN/packages/Installed` is a `cakephp-plugin` package the app requires through a `path`
repository, so `composer install` copies it into `vendor/acme/installed` and
`cakephp/plugin-installer` records it in `vendor/cakephp-plugins.php`, as it would for a plugin
from Packagist. The package carries an explicit `version`, since a path repository otherwise
offers only a dev version.

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
| A camelCase action's implicit template is the underscored action name: `viewAll` renders `view_all` | `testCamelCaseActionRendersImplicitTemplate` | Same |
| A camelCase name passed to `render()` is underscored: `render('viewAll')` renders `view_all` | `testCamelCaseRenderNameRendersUnderscoredTemplate` | Same |
| A name with a slash is not underscored, and is relative to the controller's template directory: `render('Sub/viewAll')` in `Widgets` renders `Widgets/Sub/viewAll` | `testRenderNameWithSlashIsNotUnderscored` | Same |
| The builder's `setTemplate('viewAll')` renders `view_all` | `testBuilderSetTemplateNameIsUnderscored` | 3, 4, 5 |
| `setTemplate('viewAll')` after `setTemplatePath('Gadgets')` renders `Gadgets/view_all` | `testBuilderSetTemplateAfterSetTemplatePathIsUnderscored` | 3, 4, 5 |
| `$this->view = 'viewAll'` renders `view_all` | `testViewPropertyNameIsUnderscored` | 2 |
| A routing prefix selects its own template | `testRoutingPrefixResolvesToPrefixTemplate` | 3, 4, 5: `Admin/Widgets/index`. 2: `Widgets/admin_index`, with no subdirectory |
| A plugin in the `plugins` directory beside the app directory is found, and renders its own template | `testRootPluginsDirectoryPluginRendersItsTemplate` | 2 only |
| A plugin loaded with an explicit `path` outside the app directory renders its own template | `testPluginLoadedWithExplicitPathRendersItsTemplate` | Same. 3: from `{path}/src/Template`. 4, 5: from `{path}/templates` |
| A plugin whose only copy is in another plugin's `vendor` directory is not found by name, even when that directory has its own `cakephp-plugins.php` | `testPluginInsideAnotherPluginsVendorDirectoryIsNotFound` | 3, 4, 5 |
| A plugin installed by Composer is found at `ROOT/vendor/{vendor}/{package}/` through `vendor/cakephp-plugins.php`, whose paths are built from the directory holding `composer.json` | `testComposerInstalledPluginIsFoundUnderTheComposerJsonDirectory` | 3, 4, 5 |
| A plugin directory under `ROOT/plugins/` is found by name when the Composer manifest has no entry for it, through `App.paths.plugins` | `testPluginsDirectoryPluginIsFoundByNameWithoutAManifestEntry` | 3, 4, 5 |

### `LayoutResolutionTest`

Relied on by layout navigation.

| Behavior | Test method | Versions |
|---|---|---|
| `render()`'s second argument names the layout | `testRenderSecondArgumentNamesLayout` | Same |
| A `render()` with a layout, called from a template, wraps its own output, and the outer layout wraps it again | `testRenderFromTemplateWrapsItsOutputAgainInTheOuterLayout` | Same |
| The builder's `setLayout()` names the layout | `testBuilderSetLayoutNamesLayout` | 3, 4, 5 |
| Builder setters chained after `setLayout()` all apply | `testChainedBuilderSettersAllApply` | 3, 4, 5 |
| The builder's `layout()` names the layout | `testBuilderLayoutNamesLayout` | 3, deprecated |
| The builder has no `layout()` method | `testBuilderHasNoLayoutMethod` | 4, 5 |
| A controller's `layout` property, declared or assigned, names the layout | `testControllerLayoutPropertyNamesLayout`, `testControllerLayoutAssignmentNamesLayout` | 2, 3 (deprecated) |
| A controller's `layout` property, declared or assigned, has no effect | `testControllerLayoutPropertyHasNoEffect`, `testControllerLayoutAssignmentHasNoEffect` | 4, 5 |
| `$this->layout = 'x'` in a template wraps it | `testLayoutAssignedInTemplateWraps` | 3, 4, 5. 2: `testLayoutNamedFromTemplateWraps` |
| A view's `layout()` names the layout | `testViewLayoutMethodNamesLayout` | 3, deprecated |
| A view has no `layout()` method | `testViewHasNoLayoutMethod` | 4, 5 |
| A data view renders without a layout, even when one is named | `testDataViewRendersWithoutLayout` | Same |
| A camelCase layout name is not underscored: `camelLayout` is `camelLayout` | `testCamelCaseLayoutNameIsNotUnderscored` | Same |
| A layout path selects a subdirectory of the layout directory | `testLayoutPathSelectsSubdirectory` | Same |
| A plugin controller's unprefixed layout is found in the plugin first | `testPluginControllerPrefersPluginLayout` | Same |
| A plugin controller's layout missing from the plugin falls back to the app's | `testPluginControllerFallsBackToAppLayout` | Same |
| The app's override directory wins over the plugin's own layout | `testAppOverrideWinsOverPluginLayout` | Same |
| An app controller's unprefixed layout does not reach a plugin's | `testAppControllerDoesNotReachPluginLayoutUnprefixed` | Same |
| An app controller's `Plugin.name` layout finds the plugin's | `testAppControllerReachesPluginLayoutWithPrefix` | Same |
| A layout named in a cell class has no effect | `testLayoutNamedInCellClassHasNoEffect` | 3, 4, 5 |
| A layout named in a cell template has no effect | `testLayoutNamedInCellTemplateHasNoEffect` | 4, 5 |
| A layout named in a cell template wraps the cell | `testLayoutNamedInCellTemplateWraps` | 3 |

### `ElementResolutionTest`

Relied on by element navigation and element related usages.

| Behavior | Test method | Versions |
|---|---|---|
| A plugin template prefers the plugin's element | `testPluginTemplatePrefersPluginElement` | Same |
| A plugin template falls back to the app's element | `testPluginTemplateFallsBackToAppElement` | Same |
| An app template's unprefixed element does not reach a plugin | `testAppTemplateDoesNotReachPluginElementUnprefixed` | 3, 4, 5: `MissingElementException`. 2: a notice, with the page still rendered |
| An element in a wrongly-cased directory is not found, on a case-sensitive filesystem | `testWronglyCasedElementDirectoryIsNotFound` | 3, 4, 5: `MissingElementException`. 2: a notice, with the page still rendered |
| An app template's `Plugin.name` element finds the plugin's element | `testAppTemplateRendersPrefixedPluginElement` | Same |
| A `Plugin.name` element missing from the plugin falls back to the app's element | `testPrefixedElementFallsBackToAppElement` | Same |
| A plugin template's element of another plugin finds that plugin's element | `testPluginTemplateRendersOtherPluginElement` | Same |
| An element of another plugin falls back to the app's element | `testOtherPluginElementFallsBackToAppElement` | Same |
| An element of another plugin does not fall back to the calling plugin | `testOtherPluginElementDoesNotFallBackToCallingPlugin` | 3, 4, 5: `MissingElementException`. 2: a notice, with the page still rendered |
| An unprefixed element in an app template rendered by a plugin controller is the plugin's | `testElementInFallbackTemplateResolvesInControllerPlugin` | Same |
| An unprefixed element in a plugin template rendered by an app controller is the app's | `testElementInPrefixedTemplateResolvesInControllerPlugin` | Same |
| An unprefixed element in another plugin's template is the controller plugin's | `testElementInOtherPluginTemplateResolvesInControllerPlugin` | Same |
| A camelCase element name is not underscored: `camelElement` is `camelElement` | `testCamelCaseElementNameIsNotUnderscored` | Same |

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
| A cell's `render('viewAll')` renders `view_all` | `testCellRenderNameIsUnderscored` | Same |

### `ViewHelperTest`

Relied on by view helper resolution. Controller-side helpers on CakePHP 2 work differently, so
"Same" here means 3, 4 and 5.

| Behavior | Test method | Versions |
|---|---|---|
| A view class's `addHelper()` in `initialize()` wins over a controller's helper of the same alias | `testViewAddHelperWinsOverControllerHelper` | 4, 5. 3 has no `View::addHelper()` |
| A view class's `loadHelper()` wins over a controller's helper of the same alias without options | `testViewLoadHelperWinsOverControllerHelperWithoutOptions` | Same |
| A view class's `loadHelper()` raises an error when a controller gives the same alias another `className` | `testViewLoadHelperConflictsWithControllerClassName` | Same |
| The builder's helpers replace a `$helpers` property declared on the view class | `testControllerBuilderReplacesViewHelpersProperty` | Same |
| The controller's helpers replace a `$helpers` property declared on the view class | `testControllerHelpersReplaceViewHelpersProperty` | 2 only |
| A view built by hand, with no helpers passed, keeps its `$helpers` property | `testViewBuiltByHandKeepsHelpersProperty` | Same, and 2 |
| A controller's helper reaches a layout and an element in it | `testControllerHelperReachesLayoutAndItsElement` | Same |
| A cell's own `viewBuilder()` helper reaches its template | `testCellOwnHelperReachesItsTemplate` | Same |
| An app controller's helper reaches a plugin cell's template | `testControllerHelperReachesPluginCellTemplate` | Same |
| An app controller's helper reaches an element in a plugin cell's template | `testControllerHelperReachesElementInPluginCellTemplate` | Same |
| A plugin controller's helper reaches an app cell's template | `testPluginControllerHelperReachesAppCellTemplate` | Same |
| A controller's `$helpers` property reaches its template, merged with its parent's | `testControllerHelpersPropertyReachesTemplateMerged` | 3 |
| A controller's `$helpers` property raises a warning | `testControllerHelpersPropertyRaisesWarning` | 4 |
| A controller's `$helpers` property is ignored | `testControllerHelpersPropertyIsIgnored` | 5 |
| A controller's `$helpers` property is not loaded once its warning is past | `testControllerHelpersPropertyIsNotLoadedPastTheWarning` | 4 |
| A cell's `initialize()` helper reaches its template | `testCellInitializeHelperReachesItsTemplate` | Same |
| The caller's helper wins over a cell's `initialize()` helper of the same alias | `testCallerHelperWinsOverCellInitializeHelper` | Same |
| A cell action's helper wins over the caller's of the same alias | `testCellActionHelperWinsOverCallerHelper` | Same |
| A `$helpers` property on a cell reaches its template | `testCellHelpersPropertyReachesItsTemplate` | 3 |
| A `$helpers` property on a cell wins over the caller's helper of the same alias | `testCellHelpersPropertyWinsOverCallerHelper` | 3 |
| A `$helpers` property on a cell is ignored | `testCellHelpersPropertyIsIgnored` | 4, 5 |

### `RouteTargetTest`

Relied on by route target completion and navigation: the `controller`, `action`, `prefix` and
`plugin` keys of a route target array, in the route builder and in URL building. CakePHP 2 routes
differently and will get its own table, so "Same" here means 3, 4 and 5. The app's routes use
`DashedRoute`, as the application skeleton does; "plain route" means the base `Route` class.

| Behavior | Test method | Versions |
|---|---|---|
| The `controller` key is the class's short name without `Controller` | `testControllerKeyIsTheClassShortName` | Same |
| A dashed route camelizes a lowercase `controller` key when it parses the request | `testDashedRouteCamelizesTheControllerKey` | Same |
| A lowercase `controller` key on a plain route is a missing controller | `testLowercaseControllerKeyDoesNotDispatchOnAPlainRoute` | Same |
| The `action` key is the method name as written | `testActionKeyIsTheMethodName` | Same |
| A dashed route camelizes an underscored `action` key when it parses the request | `testDashedRouteCamelizesTheActionKey` | Same |
| An underscored `action` key on a plain route is a missing action | `testUnderscoredActionKeyDoesNotDispatchOnAPlainRoute` | Same |
| An `action` key in the wrong case | `testActionKeyIsCaseSensitive`, `testActionKeyIsCaseInsensitive` | 4, 5: a missing action. 3: dispatches to the method |
| The `prefix` key selects a sub-namespace under `Controller` | `testPrefixKeySelectsTheSubNamespace` | Same |
| A nested `prefix` key, `Admin/Reports`, selects nested namespaces | `testNestedPrefixKeyJoinsNamespacesWithASlash` | Same |
| A `prefix` key in another case than the namespace | `testLowercasePrefixKeyDoesNotDispatch`, `testLowercasePrefixKeyDispatchesWithADeprecation`, `testPrefixKeyIsCamelizedOnDispatch` | 5: a missing controller. 4: dispatches, with a deprecation. 3: dispatches, each segment is camelized |
| The `plugin` key selects the plugin's `Controller` namespace | `testPluginKeySelectsThePluginNamespace` | Same |
| A vendor-namespaced `plugin` key, `Acme/Vendored`, selects the nested namespace | `testVendorPluginKeySelectsTheNestedNamespace` | Same |
| A public method of the app's own base controller is an action | `testInheritedActionDispatches` | Same |
| A method of `Cake\Controller\Controller` is not an action | `testBaseControllerMethodIsNotAnAction` | Same |
| A `prefix()` scope sets the prefix of the routes connected inside it | `testPrefixScopeAppliesToItsRoutes` | Same |
| The name given to `prefix()` becomes the `prefix` param | `testPrefixScopeNameIsKeptAsWritten`, `testPrefixScopeNameIsUnderscored` | 4, 5: as written, `Admin`. 3: underscored, `admin`, and the `plugin` param of an app request is `false` rather than `null` |
| Nested `prefix()` scopes join their names with a slash | `testNestedPrefixScopesJoinWithASlash` | Same |
| A `'prefix' => null` key inside a `prefix()` scope connects an unprefixed route | `testNullPrefixKeyEscapesThePrefixScope` | Same |
| A `'prefix' => false` key inside a `prefix()` scope | `testFalsePrefixKeyEscapesThePrefixScope`, `testFalsePrefixKeyIsRejected` | 3, 4: connects an unprefixed route. 5: `CakeException`, a prefix must be a string or null |
| A `scope()` with a `prefix` param sets the prefix of its routes | `testScopeParamsSetThePrefix` | Same |
| A `plugin()` scope sets the plugin of the routes connected inside it | `testPluginScopeAppliesToItsRoutes` | Same |
| A `scope()` with a `plugin` param sets the plugin of its routes | `testScopeParamsSetThePlugin` | Same |
| A `plugin()` scope with a vendor-namespaced name sets that plugin | `testVendorPluginScopeAppliesToItsRoutes` | Same |
| A `'plugin' => null` key inside a `plugin()` scope connects an app route | `testNullPluginKeyInPluginScopeTargetsTheApp` | Same |
| A `plugin` key naming another plugin inside a `plugin()` scope is rejected | `testOtherPluginKeyInPluginScopeIsRejected` | Same, `BadMethodCallException` |
| A `'plugin' => false` key inside a `plugin()` scope is rejected | `testFalsePluginKeyInPluginScopeIsRejected` | Same, `BadMethodCallException` |
| A string target `Controller::action` names the controller and action | `testStringTargetNamesControllerAndAction` | Same |
| A string target `Prefix/Controller::action` takes the prefix | `testStringTargetTakesAPrefix` | Same. 3 lowercases the prefix part |
| A string target `Plugin.Controller::action` takes the plugin | `testStringTargetTakesAPlugin` | Same |
| A string target `Vendor/Plugin.Controller::action` takes the vendor-namespaced plugin | `testStringTargetTakesAVendorPlugin` | Same |
| `resources()` takes the controller name | `testResourcesNameIsTheController` | Same |
| `resources()` inside a `prefix()` scope takes the prefix | `testResourcesInPrefixScopeTakesThePrefix` | Same |
| `Router::url()` with `controller` and `action` keys builds the route's URL | `testUrlControllerAndActionKeysBuildTheUrl` | Same |
| `Router::url()` with only an `action` key keeps the request's controller | `testUrlActionOnlyKeepsTheRequestController` | Same |
| `Router::url()` with no `action` key copies the request's action when the `controller` key is the request's controller name, even with another `plugin` key | `testUrlNoActionKeyCopiesTheRequestActionForTheSameController`, `testUrlNoActionKeyCopiesTheRequestActionAcrossPlugins` | Same |
| `Router::url()` with no `action` key copies the request's action across prefixes too | `testUrlNoActionKeyCopiesTheRequestActionAcrossPrefixes` | Same |
| `Router::url()` with no `action` key defaults to `index` for another controller | `testUrlNoActionKeyDefaultsToIndexForAnotherController` | Same |
| `Router::url()` keeps the request's prefix when the array has no `prefix` key | `testUrlKeepsTheRequestPrefix` | Same |
| `Router::url()` with `'prefix' => false` leaves the request's prefix | `testUrlFalsePrefixLeavesTheRequestPrefix` | Same |
| `Router::url()` keeps the request's plugin when the array has no `plugin` key | `testUrlKeepsTheRequestPlugin` | Same |
| `Router::url()` with `'plugin' => false` leaves the request's plugin | `testUrlFalsePluginLeavesTheRequestPlugin` | Same |
| `Router::url()` with a `plugin` key selects the plugin's routes | `testUrlPluginKeySelectsThePlugin` | Same |
| `Router::url()` matches the `prefix` key case-sensitively against the route | `testUrlPrefixKeyMustMatchTheRouteSpelling` | Same: `MissingRouteException`. 4, 5 for `admin`, 3 for `Admin` |
| `Router::url()` on a dashed route matches `plugin` and `controller` keys in any case | `testUrlDashedRouteInflectsControllerAndPlugin` | Same |
| `Html->link()`, `Form->postLink()`, `Url->build()` and `Form->create()`'s `url` option build a target array like `Router::url()`, request defaults included | `testHelpersBuildUrlsLikeRouterUrl` | Same |
| `Controller::redirect()` builds a target array like `Router::url()` | `testControllerRedirectBuildsUrlsLikeRouterUrl` | Same |
| Helpers in a template rendered by a plugin controller keep the request's plugin | `testHelpersInPluginTemplateKeepTheRequestPlugin` | Same |
| Helpers in a template rendered by a prefixed controller keep the request's prefix | `testHelpersInPrefixedTemplateKeepTheRequestPrefix` | Same |
| A URL built in a cell's template takes the calling request's plugin and controller, not the cell's | `testUrlInCellTemplateTakesTheCallingRequest` | Same |

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

### `EntityAccessorTest`

Relied on by entity virtual field types, completion and navigation
([entity-virtual-fields](../../../features/2026/10/entity-virtual-fields.md)). CakePHP 2 has no
entities, so "Same" here means 3, 4 and 5.

| Behavior | Test method | Versions |
|---|---|---|
| `_getFullName()` answers `full_name`, `fullName` and `FullName` | `testGetterAnswersSnakeCamelAndTitleNames` | Same |
| A getter receives the stored value of the same field, or `null` | `testGetterReceivesStoredValueOrNull` | Same |
| A public `_get*` method is an accessor | `testPublicGetterIsFound` | Same |
| A private `_get*` method on an entity extending `Entity` is not | `testPrivateGetterIsNotFound` | Same |
| A private `_get*` method on a parent entity is not | `testPrivateGetterOnParentIsNotFound` | Same |
| A parent entity's protected getter is inherited | `testParentGetterIsInherited` | Same |
| A trait's protected getter is an accessor | `testTraitGetterIsFound` | Same |
| A child's getter overrides the parent's of the same name | `testChildGetterOverridesParent` | Same |
| Accessors on a class that uses `EntityTrait` directly, instead of extending `Entity` | `testGettersOnClassUsingEntityTraitDirectly` | 3, 4: found, its private getter included, because the scope is the class itself. 5: none at all, because the trait's `static::class === self::class` guard resolves to the using class |
| On a child of that class, the parent's protected and private getters are found and the child's private getter is not | `testGettersOnChildOfClassUsingEntityTraitDirectly` | Same |
| `_setNickname()` runs on `$entity->nickname = ...` | `testSetterRunsOnAssignment` | Same |
| The field name is `Inflector::underscore()` of the method suffix: `_getURLSlug` is `u_r_l_slug`, `_getName2` is `name2` | `testGetterNameIsUnderscoredLikeInflector` | Same |
| `getNotAField()` is not an accessor | `testMethodWithoutUnderscoreIsNotAccessor` | Same |
| `$_virtual` is not needed for access; it only adds the field to `toArray()` | `testVirtualPropertyIsNotNeededForAccess` | Same |
| `has()` and `isset()` are true for a getter returning a value | `testHasAndIssetOnAccessorReturningValue` | Same |
| `has()` and `isset()` for a getter returning `null` | `testHasAndIssetOnAccessorReturningNull` | 3, 4: both false. 5: `has()` true because the accessor exists, `isset()` false |

### `EntityAssociationPropertyTest`

Relied on by entity association property types, completion and navigation
([entity-virtual-fields](../../../features/2026/10/entity-virtual-fields.md), step 6). "Same"
means 3, 4 and 5. The tests create `users` and `reviews` tables in the in-memory SQLite
connection, because `getProperty()` reads the source table's schema.

| Behavior | Test method | Versions |
|---|---|---|
| A `belongsTo` property is the singular, underscored alias: `Authors` is `author`, `ReviewAuthors` is `review_author`, `People` is `person` | `testBelongsToPropertyIsSingularUnderscored` | Same |
| A `hasOne` property is the singular, underscored alias | `testHasOnePropertyIsSingularUnderscored` | Same |
| A `hasMany` property is the underscored alias: `ReviewComments` is `review_comments` | `testHasManyPropertyIsUnderscoredAlias` | Same |
| A `belongsToMany` property is the underscored alias | `testBelongsToManyPropertyIsUnderscoredAlias` | Same |
| A `Plugin.Alias` association's property drops the plugin | `testPluginPrefixIsDroppedFromProperty` | Same |
| The `propertyName` option replaces the derived name | `testPropertyNameOptionOverrides` | Same |
| The property's entity is the target table's entity class, so `className` decides it and a plugin target gives the plugin's entity | `testTargetEntityIsTargetTablesEntityClass` | Same |
| A target with no table class gives `Cake\ORM\Entity` | `testTargetWithoutTableClassUsesBaseEntity` | Same |
| A `belongsTo` property holds the target entity, or `null` when nothing matched | `testBelongsToPropertyHoldsTargetEntityOrNull` | Same |
| A `hasOne` property holds the target entity, or `null` when nothing matched | `testHasOnePropertyHoldsTargetEntityOrNull` | Same |
| A `hasMany` property holds an array of target entities, empty when nothing matched | `testHasManyPropertyHoldsArrayOfTargetEntities` | Same |
| An accessor named for an association property receives the association's value | `testAccessorReceivesAssociationValue` | Same |
| A child's `initialize()` replaces its parent's association of the same alias, so only the child's property exists | `testChildInitializeReplacesParentAssociationOfSameAlias` | 3, 4 |
| A query method's association replaces the one of the same alias only once it runs, so either property can exist | `testQueryMethodReplacesAssociationOfSameAliasWhenItRuns` | 3, 4 |
| A child's `initialize()` that does not call its parent's has only its own association of the alias | `testChildInitializeWithoutParentCallSkipsParentAssociation` | 5 |
| Configuring an alias a second time during `initialize()` throws | `testConfiguringAnAliasTwiceInInitializeThrows` | 5 |
| A query method configuring an alias that already exists throws | `testQueryMethodConfiguringAnExistingAliasThrows` | 5 |

### `InflectorTest`

Relied on by the plugin's `Inflector` port and the `camelCaseToUnderscore()`,
`tableToEntityClass()` and `entityToTableClass()` helpers, which derive virtual field names,
association property names and entity and table class names
([entity-virtual-fields](../../../features/2026/10/entity-virtual-fields.md)). "Same" means 3,
4 and 5.

| Behavior | Test method | Versions |
|---|---|---|
| `underscore()` puts an underscore before each capital after the first character and lowercases: `fullName` and `FullName` are `full_name`, `uRLSlug` is `u_r_l_slug`, `name2` stays `name2` | `testUnderscoreOfCamelCaseNames` | Same |
| `singularize()` handles the irregular, special and uncountable plurals: `People` is `Person`, `Movies` is `Movie`, `Categories` is `Category`, `Statuses` is `Status`, `Sheep` stays | `testSingularizeIrregularAndSpecialPlurals` | Same |
| `pluralize()` inverts them | `testPluralizeIrregularAndSpecialSingulars` | Same |
| A table's default entity class is the singularized alias, so `PeopleTable` has `Person` | `testTableEntityClassIsTheSingularizedAlias` | Same |

### `BehaviorTest`

Relied on by behavior method and finder completion, types and navigation, and by the
associations behaviors add. CakePHP 2 attaches behaviors through `$actsAs` and maps methods by
other rules, so it has its own `BehaviorTest` with its own rows.

| Behavior | Test method | Versions |
|---|---|---|
| A bare name loads the app's behavior before the core one of the same name | `testBareNameLoadsAppBehaviorBeforeCore` | 3, 4, 5 |
| A bare name does not reach a plugin's behavior | `testBareNameDoesNotReachPluginBehavior` | 3, 4, 5. 2: `testBareNameOfAnUnloadedClassDoesNotReachPluginBehavior`, only while the class is not loaded |
| A `Plugin.Name` loads the plugin's behavior under the short alias | `testPluginNameLoadsPluginBehavior` | 3, 4, 5 |
| A `Vendor/Plugin.Name` loads from the nested namespace, `Vendor\Plugin\Model\Behavior`, under the short alias | `testVendorPluginNameLoadsFromTheNestedNamespace` | 3, 4, 5 |
| A `Plugin.Name` loads the behavior of a plugin the application has not loaded, since only the namespace is used | `testPluginNameOfAPluginThatIsNotLoadedLoadsItsBehavior` | 3, 4, 5 |
| A bare name in a plugin's own table loads the app's behavior, not the plugin's | `testBareNameInPluginTableLoadsAppBehavior` | 3, 4, 5 |
| A `Plugin.Name` missing from the plugin does not fall back to the app's behavior | `testPluginNameDoesNotFallBackToAppBehavior` | 3, 4, 5. 2: `testPluginNameOfAnUnloadedClassDoesNotFallBackToAppBehavior`, only while the class is not loaded |
| Any name of a behavior class that is already loaded uses that class, whichever app or plugin it came from, since behavior class names are global | `testAnyNameOfALoadedClassUsesItWhateverItsOrigin` | 2 |
| A plain `className` option loads the app's behavior before the core one of the same name | `testClassNamePlainNameLoadsAppBehaviorBeforeCore` | 3, 4, 5 |
| The `className` option accepts a plain name, plugin notation and an FQN, and the first argument is the alias | `testClassNameOptionAcceptsPlainPluginAndFqnAndKeepsAlias` | 3, 4, 5. 2: `testClassNameOptionKeepsAliasAndLoadsClass`, plain name only |
| With a `className` option, a plugin-dotted first argument is the alias whole, dot included | `testClassNameOptionKeepsADottedFirstArgumentAsTheAlias` | 2, 3, 4, 5 |
| A first argument equal to the `className` is the alias whole on 2, 3 and 4, and split at the dot on 5 | `testClassNameEqualToTheFirstArgument` | 2, 3, 4, 5 |
| `addBehaviors()` accepts a plain string and an alias with options | `testAddBehaviorsAcceptsPlainAndKeyedEntries` | 3, 4, 5 |
| Reflection mixes in the public methods, keys `find*` methods as finders by `lcfirst`, and skips protected methods, callbacks and base-class methods | `testReflectionMixesInPublicMethodsAndFinders` | 3, 4, 5 |
| Reflection includes a parent behavior's public methods | `testReflectionIncludesParentBehaviorMethods` | 3, 4, 5 |
| Reflection includes the public methods and finders of a trait the behavior uses, and public static methods | `testReflectionIncludesTraitAndStaticMethods` | 3, 4, 5. 2: `testMappedMethodsIncludeTraitAndStaticMethods` |
| A reflected finder's name is the method name without `find` and with only its first letter lowered, `findTopRated` is `topRated`, and is matched in any case | `testReflectedFinderNameKeepsItsCaseAfterTheFirstLetter` | 3, 4, 5 |
| `$_defaultConfig` maps restrict the mixed-in methods and finders to the listed aliases, for app and core behaviors | `testDefaultConfigMapsRestrictMixedInMethods` | 3, 4, 5 |
| A `$_defaultConfig` naming only one of the two maps leaves the other to reflection | `testMethodsAndFindersFallBackToReflectionSeparately` | 3, 4, 5 |
| A child behavior inherits its parent's `$_defaultConfig` maps, so its own public methods are not mixed in | `testChildBehaviorInheritsDefaultConfigMaps` | 3, 4, 5 |
| A child behavior redeclaring `$_defaultConfig` without the maps is reflected, its parent's unmapped methods included | `testChildBehaviorRedeclaringDefaultConfigIsReflected` | 3, 4, 5 |
| `implementedMethods` in the load options merges with the defaults by method name, custom alias winning, and `[]` disables them | `testLoadOptionsMergeWithDefaultMapsByMethodName` | 3, 4, 5 |
| `implementedMethods` in the load options with no defaults replaces reflection for that key only | `testLoadOptionsWithoutDefaultsReplaceReflection` | 3, 4, 5 |
| The table's own method and own `findX()` win over a behavior's of the same name, although the registry still maps them | `testTableOwnMethodAndFinderWinOverBehavior` | 3, 4, 5. 2: `testModelOwnMethodWinsOverBehavior` |
| Two behaviors providing the same method name throw `LogicException` on load | `testTwoBehaviorsProvidingTheSameMethodThrow` | 3, 4, 5. 2: `testFirstBehaviorKeepsADuplicateMethodName`, the first keeps it |
| A behavior finder is callable on the table and on a query built from it | `testBehaviorFinderIsCallableOnTableAndQuery` | 3, 4, 5 |
| A behavior attached outside `initialize()` mixes in once that method runs | `testBehaviorAttachedOutsideInitializeMixesInOnceCalled` | 3, 4, 5. 2: `testBehaviorsLoadAttachesAtRuntime` |
| A child table inherits its parent's `initialize()` behaviors | `testChildTableInheritsParentBehaviors` | 3, 4, 5 |
| A child table calling `parent::initialize()` and then loading a parent's behavior again plainly keeps the parent's options | `testChildTableRepeatingAParentLoadKeepsTheParentOptions` | 3, 4, 5 |
| A child table whose `initialize()` does not call its parent's has none of the parent's behaviors | `testChildTableThatSkipsParentInitializeHasNoneOfItsBehaviors` | 3, 4, 5 |
| A child table loading a behavior plainly before `parent::initialize()` loads it with other options throws a `RuntimeException` when constructed | `testChildTableLoadingPlainlyBeforeParentInitializeThrows` | 3, 4, 5 |
| `getBehavior()` returns the loaded instance and throws `InvalidArgumentException` for an unloaded alias | `testGetBehaviorReturnsLoadedInstanceAndThrowsForUnloaded` | 3, 4, 5 |
| `getBehavior()` throws for the plugin-prefixed name of a loaded behavior | `testGetBehaviorDoesNotTakeAPluginPrefixedName` | 3, 4, 5 |
| `hasBehavior()` and `removeBehavior()` take the alias, not the plugin-prefixed name or the `className` | `testHasAndRemoveBehaviorTakeTheAlias` | 3, 4, 5 |
| A removed behavior's methods and finders | `testRemovedBehaviorMethodsAreUnmapped`, `testRemovedBehaviorMethodsStayMapped` | 4, 5: unmapped. 3: still mapped in the registry |
| Loading a loaded alias again with no options keeps the first instance and its options | `testLoadingALoadedAliasAgainKeepsTheFirstInstance` | 3, 4, 5 |
| Loading a loaded alias again by a name that resolves to another class keeps the first class | `testLoadingALoadedAliasAgainByAnotherNameKeepsTheFirstClass` | 3, 4, 5 |
| Loading a loaded alias again with the same options keeps the first instance | `testLoadingALoadedAliasAgainWithTheSameOptionsKeepsTheFirstInstance` | 3, 4, 5 |
| Loading a loaded alias again with some of its options keeps the first instance and the options the later load left out | `testLoadingALoadedAliasAgainWithSomeOfItsOptionsKeepsTheFirstOptions` | 3, 4, 5 |
| Loading a loaded alias again with other options throws a `RuntimeException` | `testLoadingALoadedAliasWithOtherOptionsThrows` | 3, 4, 5 |
| A behavior method called on the table raises a deprecation | `testBehaviorMethodCalledOnTableIsDeprecated` | 5 (since 5.3). 3, 4: `testBehaviorMethodCalledOnTableIsNotDeprecated` |
| A behavior method called on its instance raises no deprecation | `testBehaviorMethodCalledOnItsInstanceIsNotDeprecated` | 3, 4, 5 |
| An association a behavior adds through `$this->_table` or `$this->table()` (`getTable()` on 3) is an association of the table, reachable by `contain()` and as an entity property | `testAssociationAddedByBehaviorBelongsToTable` | 3, 4, 5 |
| `$actsAs` loads behaviors plain, with settings and plugin-prefixed, in order | `testActsAsLoadsPlainConfiguredAndPluginBehaviors` | 2 |
| A model's `$actsAs` is merged onto `AppModel`'s, whose entries come first, and a model declaring none has `AppModel`'s | `testActsAsMergesWithAppModel` | 2 |
| A model's `$actsAs` is merged onto its direct parent model's, the parent's entries first | `testActsAsMergesWithTheDirectParentModel` | 2 |
| A model's `$actsAs` is not merged with a grandparent model's, other than `AppModel` | `testActsAsDoesNotMergeWithAGrandparentModel` | 2 |
| Mapped methods exclude `_`-prefixed names, callbacks and `ModelBehavior` methods | `testMappedMethodsExcludeUnderscoredCallbacksAndModelBehaviorMethods` | 2 |
| Mapped methods include a parent behavior class's | `testMappedMethodsIncludeAParentBehaviorsMethods` | 2 |
| A mapped method is called without its `Model` parameter | `testMappedMethodIsCalledWithoutTheModelParameter` | 2 |
