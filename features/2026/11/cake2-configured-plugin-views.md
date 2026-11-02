# View files of CakePHP 2 plugins outside the app directory

## Status

Planned 2026-11-02, from a gap found while planning
[cake2-controller-detection-in-index](../10/cake2-controller-detection-in-index.md). Merged
`origin/main` the same day, after #319 and #320 landed; see Implementation Progress. Not started.

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
   configured plugin stands for. The `{root}/plugins` case is already pinned:
   `testRootPluginsDirectoryPluginRendersItsTemplate` landed with #320, loading the `Rooted` plugin
   with no path.
2. Tests, on cake2, expected to fail first:
   - Go to related from a configured `plugins/Blog` template and layout lists the action, with
     CakePHP 2 alone.
   - The same in mixed mode (CakePHP 3+ force enabled, `appDirectory = "src"`), beside #320's
     indexer-keys test in `BothVersionsLayoutNavigationTest`, which configures the plugin by loading
     a settings state with a `CakeTwoPluginConfig`. Since #319 a layout lists only project
     controllers, those in content or under a configured plugin path, which the fixture's controller
     is.
   - An element call in a configured plugin's view is indexed under `Elements/`, following
     `ElementReferenceIndexingTest`.
   - A view file of an unconfigured `{root}/plugins` plugin still has no templates directory, so the
     tests pin that it is left to auto-discovery.
3. In the CakePHP 2 branch of `templatesDirectoryOfViewFile`, also accept a `View` directory whose
   parent is a configured CakePHP 2 plugin. Match the path with `endsWith("/" + pluginPath)`, as
   `isCakeTwoPluginDirectory` does on the controller side, so recognition is decided from the path
   and the settings alone, and holds when the app root is nested in the project as the test
   fixtures are. That helper is private to `ViewFileIndexService.kt`; move it to `CakePlugins.kt`
   beside `cakeTwoPluginName` so both sides share one rule, rather than copying it.
4. Bump the `ViewFileIndex` and `ViewVariableIndex` versions, since both indexers call it. After
   #320 they are at 28 and 22.
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
- How `cakeTwoPluginName` and `isInConfiguredPlugin` (`CakePlugins.kt`) match a configured CakePHP 2
  path: from the project directory, not as a suffix, so a configured plugin under a nested app root
  is named only when it sits under a `Plugin` or `plugins` directory. The tests here use
  `plugins/Blog`, which the directory name covers. Aligning those with the suffix rule is a separate
  change.

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
