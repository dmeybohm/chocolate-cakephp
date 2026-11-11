# CakePHP 2 controller detection depends on sibling directories

## Status

Recorded 2026-10-31 while planning [view-index-bugs](view-index-bugs.md). Investigated, planned and
fixed 2026-11-02.

## The bug

With CakePHP 2 and 3+ both enabled, `isCakeTwoController` (`ViewFileIndexService.kt`) classifies an
app controller as CakePHP 2 when the directory above its `Controller` directory matches
`cake2AppDirectory` and no entry named `templates` sits beside that directory and none named
`Template` sits inside it. It compares `nameWithoutExtension`, so a file such as `templates.json`
counts as well as a directory.

That goes wrong in two ways:

- **Immediately.** A CakePHP 2 app with a `templates` entry beside it, such as a repo partway through
  a 2 to 4 migration with `app/` next to `src/` and `templates/`, has every CakePHP 2 app controller
  classified as 3+, even in a fresh index.
- **With indexing order.** When `cake2AppDirectory` also names the 3+ app directory, a 3+ controller
  indexed before its `templates` directory exists is classified as CakePHP 2. The index reindexes a
  file only when its own content changes, so it keeps that classification until it is edited. This
  is the same class of bug as the element directory dependency in `view-index-bugs`.

CakePHP 2 plugin controllers are covered in [Plugin controllers](#plugin-controllers).

## What the user sees

The classification reaches the index only through `TemplateDirStructure.ofSourceFile`. Of its uses
while indexing a controller, only one changes the result:

- `layoutStructuresOf` picks the layout keys: `Layouts/` for CakePHP 2, or `Layout/` and `layout/`
  for 3+. A misclassified controller's layouts are stored under the other version's keys, so
  navigation between a layout and the action that names it misses.
- `referencePluginContext` only calls `resolveName`, which no structure overrides, so the split of a
  `Plugin.name` reference is the same either way.
- `pluginOfClassFile` returns null for an app controller under both `cakeTwoPluginName` and
  `cakeThreePluginName`.

## Reproduction

Settings: CakePHP 2 enabled, `cake3ForceEnabled`, `cake2AppDirectory = "app"`, `appDirectory = "src"`.

```
cake2/templates/Posts/index.php          # any entry named templates
cake2/app/Controller/PostsController.php # viewAll() { $this->layout = 'admin'; }
cake2/app/View/Layouts/admin.ctp
```

Go to related from `admin.ctp` should list `PostsController::viewAll()`. It lists nothing. Without
the `templates` file it works.

## Plugin controllers

`isCakeTwoPluginDirectory` runs before the sibling check, and decides from the path and settings
alone. When it returns true the sibling check never runs, so the fix cannot change these cases.

- `{app}/Plugin/{Name}/Controller`: recognised from the path.
- `{root}/plugins/{Name}/Controller`, the directory beside the app directory: CakePHP 2 searches it
  by default (`App.php`, pinned by `testRootPluginsDirectoryPluginRendersItsTemplate`). Recognised
  when the plugin is configured, from its configured path. Unconfigured, it fails the plugin check
  and then `matchesCake2AppDirectory`, so it is classified 3+, before the fix and after it. That is
  left to "auto-discovered CakePHP 2 plugins" in [cake2-plugin-settings](cake2-plugin-settings.md),
  which would make such a plugin a configured one; hard-coding `{root}/plugins` here would give
  plugin identity a second source. A 3+ plugin there has `src` before `Controller`, so the two do
  not collide.

Separate, and not in this branch: `templatesDirectoryOfViewFile` (`CakePaths.kt`) recognises a
CakePHP 2 plugin's `View` directory only under the app directory, and does not consult configured
plugins. A view file of a plugin in `{root}/plugins`, configured or not, has no templates directory,
so going from it to its controller finds nothing, with CakePHP 2 alone as well.
[view-index-bugs](view-index-bugs.md) noted the narrowing this caused for element references.

## Plan

Assume `cake2AppDirectory` and `appDirectory` name different directories. A mixed-version project
where both settings name the same directory is rare enough not to handle: the path cannot tell the
versions apart there, and such a controller is treated as 3+. That also leaves out the indexing-order
case above, which only arises with that configuration.

1. Tests in `cake2/BothVersionsLayoutNavigationTest`:
   - `test app controller keeps the CakePHP 2 conventions beside a templates directory`: the
     reproduction above. Fails until the fix.
   - `test configured plugin controller beside the app stores CakePHP 2 layout keys`: a configured
     `plugins/Blog` controller, beside a `templates` entry, stores `Layouts/admin` and not
     `Layout/admin`. Passes today; it guards the plugin check against the fix. It reads the
     indexer's keys rather than navigating from the layout, because of the view side gap above.
   - `test the longer app directory setting wins when it nests the CakePHP 2 one`:
     `cake2AppDirectory = "src/app"`, `appDirectory = "app"`, and a controller in
     `src/app/Controller` stores CakePHP 2 layout keys. Passes today; a rule that lets any
     `appDirectory` match win would break it.
   - `test the longer app directory setting wins when it nests the CakePHP 3 one`: the reverse,
     `cake2AppDirectory = "app"`, `appDirectory = "src/app"`, stores 3+ layout keys. Fails today:
     with no `templates` entry beside it, the controller is classified as CakePHP 2.
2. Decide the version from the controller's path and the settings alone. After the plugin check, a
   controller is CakePHP 2 when its app directory matches `cake2AppDirectory`, unless it also
   matches `appDirectory` (`matchesAppDirectory` in `CakePaths.kt`) and that setting has at least as
   many path components. Both matchers compare only the trailing components, so distinct settings
   can both match one directory when one nests the other, such as `src/app` and `app`. The shorter
   is then a suffix of the longer, and the longer names the actual directory. Equal lengths only
   happen with identical settings, which are treated as 3+. Remove the sibling lookups.
3. Bump the `ViewFileIndex` version, since stored keys change.
4. Run the `cake2` tests, then the other versions' layout navigation tests.

The app controller classification depends on how the project is laid out and configured, not on
what CakePHP does at runtime, so it has no assumption test. The default plugin location does:
`testRootPluginsDirectoryPluginRendersItsTemplate` (2).

## Implementation Progress

### Session #1

Merged `origin/main` twice (2026-11-02). What changed for this bug:

- #309 (`view-index-bugs`) fixed the element directory dependency by deriving the prefix from the
  file's own templates directory, not a sibling lookup. It did not touch `isCakeTwoController`.
- 3b3ed077 added `isCakeTwoPluginDirectory`, so CakePHP 2 plugin controllers are now recognised from
  their path and settings alone. The sibling `templates`/`Template` check is still there for app
  controllers, so the bug remains for them.
- #317 ([goto-related-method-names](goto-related-method-names.md)) pinned with
  `testCamelCaseActionRendersImplicitTemplate` that CakePHP 2 underscores an implicit template name
  as 3+ does, so `fullImplicitViewPath` now always underscores and `indexImplicitRender` no longer
  calls `lookupControllerFileInfo`. That removed the implicit render keys from this bug. The
  `ofSourceFile` uses described above are what is left.

An assumption test for the same behaviour was added here and then removed again, as it duplicated
the one from #317.

A review pointed out that the bug also misclassifies a CakePHP 2 controller straight away, not only
depending on indexing order, and that only the layout keys change. Both were confirmed in the code,
and the first was reproduced by the first regression test of the plan, which fails as expected and
passes once the `templates` file is removed. The plan above was written from that.

Narrowed the plan to distinct `cake2AppDirectory` and `appDirectory` settings, and looked at plugin
controllers. Added `testRootPluginsDirectoryPluginRendersItsTemplate`, which passes: CakePHP 2.10.24
loads a plugin from `{root}/plugins` and renders its template. A navigation test for a configured
`plugins/Blog` controller failed on the view side, not on controller detection, which is how the
`templatesDirectoryOfViewFile` gap above was found; the test reads the indexer's keys instead, and
passes.

A second review found that distinct settings can still both match one directory, since the matchers
compare trailing path components: `src/app` matches both `src/app` and `app`. The plan's rule let
`appDirectory` win any overlap, which would classify a CakePHP 2 controller in `src/app` as 3+ when
`appDirectory` is `app`. The rule now prefers the setting with more components, with a test for each
direction of the overlap.


### Session #2

Implemented steps 2 and 3 of the plan. `isCakeTwoController` no longer looks at sibling entries: after
the plugin check, a controller is CakePHP 2 when its app directory matches `cake2AppDirectory` and
either does not match `appDirectory` or `cake2AppDirectory` has more path components. `ViewFileIndex`
is at version 28.

All five tests in `BothVersionsLayoutNavigationTest` pass, including the two that failed before the
fix. The `cake2` tests and the cake3, cake4 and cake5 `LayoutNavigationTest` pass (266 tests).
