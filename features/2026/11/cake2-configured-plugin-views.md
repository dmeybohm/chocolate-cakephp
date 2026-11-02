# View files of CakePHP 2 plugins outside the app directory

## Status

Planned 2026-11-02, from a gap found while planning
[cake2-controller-detection-in-index](../10/cake2-controller-detection-in-index.md). Not started.

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

## What the user sees

In a view file of such a plugin, everything that starts from the file's templates directory is
missing. That includes going to the controller that renders it, element references
([view-index-bugs](../10/view-index-bugs.md) noted this narrowing when element indexing moved onto
`templatesDirectoryOfViewFile`), and view variable and helper completion. `templatesDirectoryOfViewFile`
has 14 callers, all of which see the file as outside any templates directory.

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

## Plan

1. Assumption tests on CakePHP 2:
   - A plugin loaded with `CakePlugin::load('Name', array('path' => ...))` from a directory outside
     the app renders templates from its own `View` directory. This is what a configured plugin
     stands for.
   - `testRootPluginsDirectoryPluginRendersItsTemplate`, for `{root}/plugins`, is on the
     `cake2-controller-detection-in-index` branch. Whichever branch merges second should pick it up
     rather than adding another.
2. Tests, on cake2, expected to fail first:
   - Go to related from a configured `plugins/Blog` template and layout lists the action, with
     CakePHP 2 alone.
   - The same in mixed mode (CakePHP 3+ force enabled, `appDirectory = "src"`), next to the tests in
     `BothVersionsLayoutNavigationTest`.
   - An element call in a configured plugin's view is indexed under `Elements/`, following
     `ElementReferenceIndexingTest`.
   - A view file of an unconfigured `{root}/plugins` plugin still has no templates directory, so the
     tests pin that it is left to auto-discovery.
3. In the CakePHP 2 branch of `templatesDirectoryOfViewFile`, also accept a `View` directory whose
   parent is a configured CakePHP 2 plugin. Match the path with `endsWith("/" + pluginPath)`, as
   `isCakeTwoPluginDirectory` does on the controller side, so recognition is decided from the path
   and the settings alone.
4. Bump the `ViewFileIndex` and `ViewVariableIndex` versions, since both indexers call it.
5. Run the cake2 tests in full, since all 14 callers start seeing these files, then the targeted
   tests for other versions.

## Not in scope

- Unconfigured `{root}/plugins/{Name}` plugins. "Auto-discovered CakePHP 2 plugins" in
  [cake2-plugin-settings](../10/cake2-plugin-settings.md) would create configs for them, and step
  3 then covers them with no further change. Hard-coding the directory here would give plugin
  identity a second source.
- Reindexing on settings changes. Nothing new is needed: `PluginForm` already requests a rebuild of
  the view file and view variable indexes when the plugin configs change, so adding a plugin config
  reindexes its view files, as it does its controllers.
