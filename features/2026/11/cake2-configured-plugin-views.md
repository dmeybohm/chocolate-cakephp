# View files of CakePHP 2 plugins outside the app directory

## Status

Planned 2026-11-02, from a gap found while planning
[cake2-controller-detection-in-index](../10/cake2-controller-detection-in-index.md). Merged
`origin/main` the same day, after #319 and #320 landed, and added the assumption test; see
Implementation Progress. Plugin code not started.

## The bug

`templatesDirectoryOfViewFile` (`CakePaths.kt`) finds the templates directory of a view file by
walking up from it. In its CakePHP 2 branch it accepts a plugin's `View` directory only when the
plugin is under the app directory: `{app}/Plugin/{Name}/View`, where the `View` directory's
great-grandparent matches `cake2AppDirectory`. It never consults `pluginConfigs`.

So a view file of a CakePHP 2 plugin anywhere else has no templates directory, even when the plugin
is configured. That includes `{root}/plugins/{Name}`, which CakePHP 2 searches by default, and any
path a project passes to `CakePlugin::load()`. It applies with CakePHP 2 alone as well as in mixed
mode.

The lookup side does consult the configs: `pluginAndThemeTemplatePaths` adds
`{pluginPath}/View` for each `CakeTwoPluginConfig`. So navigation into a configured plugin's
templates works, and navigation out of them does not.

The controller side has consulted them since 3b3ed077, and since #320 decides from the path and the
settings alone: `isCakeTwoPluginDirectory` (`ViewFileIndexService.kt`) accepts a `Plugin` or
`plugins` directory under the app directory, or a configured plugin's path as a suffix. #320's log
records this view-side gap under "Plugin controllers" as separate from that fix.

Plugin identity has a second gap of its own. `cakeTwoPluginName` (`CakePlugins.kt`) names a file's
plugin from a `Plugin` or `plugins` directory above it, and otherwise from a configured path, which
it resolves from the IDE project directory. Settings paths are relative to the app root
(`appRootDirectoryFromViewFile`, and the settings form), and that root may be nested in the project,
as every test fixture nests it under `cake2/`. So a configured plugin outside those directories,
such as `lib/Shop` in `ConfiguredPluginTest`, is named only when the project directory is the app
root. Recognising its `View` directory alone would then make `viewFileOf` treat its templates as
the app's. `isInConfiguredPlugin`'s CakePHP 2 branch, from #319, resolves the same way. The
CakePHP 3+ rule, `cakeThreePluginName`, matches configured paths as suffixes for this reason.

## What the user sees

In a view file of such a plugin, everything that starts from the file's templates directory is
missing. That includes going to the controller that renders it, element references
([view-index-bugs](../10/view-index-bugs.md) noted this narrowing when element indexing moved onto
`templatesDirectoryOfViewFile`), and view variable and helper completion. Every caller of `templatesDirectoryOfViewFile`,
across the view file index, the view variable index and navigation, sees the file as outside any
templates directory.

## Reproduction

Settings: CakePHP 2 enabled, `cake2AppDirectory = "app"`, and a `CakeTwoPluginConfig` with
`pluginName = "Blog"`, `pluginPath = "plugins/Blog"`.

```
cake2/plugins/Blog/Controller/PostsController.php # viewAll() { $this->layout = 'admin'; }
cake2/plugins/Blog/View/Posts/view_all.ctp
cake2/plugins/Blog/View/Layouts/admin.ctp
```

Go to related from `view_all.ctp` or `admin.ctp` should list `PostsController::viewAll()`. It lists
nothing. The controller's index keys are right (`Layouts/admin`), so the miss is on the view side.
#320 pins the controller half of this with `test configured plugin controller beside the app stores
CakePHP 2 layout keys` in `BothVersionsLayoutNavigationTest`, which reads the indexer's keys for
exactly this setup because navigating from the layout fails here.

## Plan

1. Assumption test on CakePHP 2: a plugin loaded with `CakePlugin::load('Name', array('path' => ...))`
   from a directory outside the app renders templates from its own `View` directory. This is what a
   configured plugin stands for. Done: `testPluginLoadedWithExplicitPathRendersItsTemplate` loads
   the `Pathed` plugin from `{root}/packages/Pathed`, a directory that is neither `app/Plugin` nor
   `plugins`, and passes. The `{root}/plugins` case was already pinned:
   `testRootPluginsDirectoryPluginRendersItsTemplate` landed with #320, loading the `Rooted` plugin
   with no path.
2. Tests, on cake2, expected to fail first:
   - Go to related from a configured `plugins/Blog` template and layout lists the action, with
     CakePHP 2 alone.
   - The same in mixed mode (CakePHP 3+ force enabled, `appDirectory = "src"`), beside #320's
     indexer-keys test in `BothVersionsLayoutNavigationTest`, which configures the plugin by loading
     a settings state with a `CakeTwoPluginConfig`. Since #319 a layout lists only project
     controllers, those in content or under a configured plugin path, which the fixture's controller
     is. With `plugins/Blog`, `cakeTwoPluginName` names the plugin from the directory, so these
     pin the templates directory lookup alone.
   - The explicit-path case end to end: a configured plugin at `lib/Shop`, the path
     `ConfiguredPluginTest` uses, outside any `Plugin` or `plugins` directory and under the nested
     `cake2/` root. Go to related from its template and layout lists its controller's action, and
     `viewFileOf` of the template names the plugin `Shop`, in the style of `PluginAwareViewKeysTest`.
     This fails first on both counts: no templates directory, and no plugin name once there is one.
   - An element call in a configured plugin's view is indexed under `Elements/`, following
     `ElementReferenceIndexingTest`.
   - A view file of an unconfigured `{root}/plugins` plugin still has no templates directory, so the
     tests pin that it is left to auto-discovery.
3. One rule for a configured CakePHP 2 plugin directory, in `CakePlugins.kt` beside
   `cakeTwoPluginName`: the directory's path ends with `"/" + pluginPath`, as
   `isCakeTwoPluginDirectory` (private to `ViewFileIndexService.kt`) matches today. Recognition is
   then decided from the path and the settings alone, and holds when the app root is nested in the
   project. Use it from four places:
   - the CakePHP 2 branch of `templatesDirectoryOfViewFile`, which also accepts a `View` directory
     whose parent is such a plugin;
   - `isCakeTwoController`'s plugin check, which moves to it;
   - the configured-path clause of `cakeTwoPluginName`, in place of the project-directory lookup,
     so the plugin's files are named by it (`viewFileOf`, `pluginOfClassFile` while indexing, and
     `ConfiguredMemberService.cakeTwoPluginOfClass`);
   - the CakePHP 2 branch of `isInConfiguredPlugin`, so #319's layout union counts the plugin's
     controllers under a nested root too.
   When the project directory is the app root, the suffix matches everything the relative lookup
   did, so nothing recognised today is lost. The directory-name clause of `cakeTwoPluginName`
   stays first, as it is.

   Two details of today's check to keep: a configured path is trimmed of slashes and skipped when
   blank, as `isCakeTwoPluginDirectory` and `cakeThreePluginName` do (a saved path may carry a
   trailing slash, as `SettingsTest` shows with `app/Plugin/DebugKit/`); and
   `isCakeTwoController` still accepts `{app}/Plugin/{Name}` and `{app}/plugins/{Name}` with no
   config, through its directory-name clause. The existing
   `test plugin controller keeps the CakePHP 2 conventions` in `BothVersionsLayoutNavigationTest`
   catches a regression in the second.
4. Bump the `ViewFileIndex` and `ViewVariableIndex` versions, since both indexers call
   `templatesDirectoryOfViewFile`, and `cakeTwoPluginName` reaches stored keys through
   `pluginOfClassFile`. After #320 they are at 28 and 22.
5. Run the cake2 tests in full, since every caller starts seeing these files, then the targeted
   tests for other versions.

## Not in scope

- Unconfigured `{root}/plugins/{Name}` plugins. "Auto-discovered CakePHP 2 plugins" in
  [cake2-plugin-settings](../10/cake2-plugin-settings.md) would create configs for them, and step
  3 then covers them with no further change. Hard-coding the directory here would give plugin
  identity a second source.
- Reindexing on settings changes. Nothing new is needed: `PluginForm` already requests a rebuild of
  the view file and view variable indexes when the plugin configs change, so adding a plugin config
  reindexes its view files, as it does its controllers. Since #319 the `RenderingControllers` cache
  also depends on `Settings.modificationTracker`, so go to related does not keep a stale result
  either.

## Implementation Progress

### Session #1

Merged `origin/main` (2026-11-02), which brought #319 ([layout-union-project-controllers](layout-union-project-controllers.md))
and #320 ([cake2-controller-detection-in-index](../10/cake2-controller-detection-in-index.md)).
The merge touched no code on this branch. What changed for this plan:

- `testRootPluginsDirectoryPluginRendersItsTemplate` is on `main`, so step 1 keeps only the
  explicit-path assumption test.
- #320 pins the controller half of the reproduction with an indexer-keys test, and its log records
  this gap as the view side left over. The navigation tests of step 2 go beside that test.
- `isCakeTwoController` now decides from the path and the settings alone, and `ViewFileIndex` is
  at 28, so step 4 bumps from there.
- #319 filters a layout's controllers to project ones through `isInConfiguredPlugin`, and added
  `Settings.modificationTracker`, which the cache invalidation note above now relies on.

### Session #2

Added the assumption test of step 1. `app/Config/bootstrap.php` loads a fourth plugin, `Pathed`,
with an explicit `path` of `{root}/packages/Pathed/`, and
`testPluginLoadedWithExplicitPathRendersItsTemplate` renders `pathed:Trinkets/index` from
`/pathed/trinkets/index`. It passes on CakePHP 2.10.24, so a configured plugin's `View` directory
is its templates directory wherever the plugin sits. The full CakePHP 2 assumption suite still
passes (48 tests). No plugin code yet.

### Session #3

A review of the plan found the explicit-path case was not covered end to end: step 3 would have
recognised `packages/Pathed/View` as a templates directory while `cakeTwoPluginName` still resolved
configured paths from the project directory, so under a nested app root `viewFileOf` would have
treated the plugin's templates as the app's, and plugin-aware navigation and resolution could stay
wrong after the fix. The plan had put that alignment out of scope.

Brought it into scope, rather than narrowing the outcome to `plugins/{Name}`: settings paths are
relative to the app root, which the fixtures and real monorepos nest, and the 3+ rule and the
controller side already match as suffixes, so the project-directory lookup was the odd one out.
Step 3 now defines the suffix rule once in `CakePlugins.kt` and routes `templatesDirectoryOfViewFile`,
`isCakeTwoController`, `cakeTwoPluginName` and `isInConfiguredPlugin` through it, and step 2 adds a
`lib/Shop` test that checks navigation and `viewFileOf`'s plugin name together. No plugin code yet.

A re-review found no blocking issue, and asked that the shared helper keep two behaviours of the
current controller check: accepting `app/Plugin/{Name}` without a config, and trimming slashes from
configured paths. Both are now recorded under step 3.
