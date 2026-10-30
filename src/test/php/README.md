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
| An app controller's unprefixed `render()` does not reach a plugin's template | `testAppControllerDoesNotReachPluginTemplateUnprefixed` | Same |
| An app controller's `Plugin.name` `render()` finds the plugin's template | `testAppControllerRendersPrefixedPluginTemplate` | Same |
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

### `CellTest`

Relied on by cell navigation, the cell action line marker and cell view variables. Cells do not
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
