# CakePHP 2 controller detection depends on sibling directories

## Status

Recorded 2026-10-31 while planning [view-index-bugs](view-index-bugs.md). Investigated and planned
2026-11-02; not yet fixed.

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

CakePHP 2 plugin controllers are not affected: `isCakeTwoPluginDirectory` recognises them from their
path and settings before the sibling check.

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

## Plan

Assume `cake2AppDirectory` and `appDirectory` name different directories. A mixed-version project
where both settings name the same directory is rare enough not to handle: the path cannot tell the
versions apart there, and such a controller is treated as 3+. That also leaves out the indexing-order
case above, which only arises with that configuration.

1. Regression test in `cake2/BothVersionsLayoutNavigationTest`, expected to fail first:
   `test app controller keeps the CakePHP 2 conventions beside a templates directory`, the
   reproduction above.
2. Decide the version from the controller's path and the settings alone. After the plugin check, a
   controller is CakePHP 2 when its app directory matches `cake2AppDirectory` and not `appDirectory`,
   using `matchesAppDirectory` from `CakePaths.kt` for the second. The `appDirectory` check covers
   nested overlaps such as `app` and `src/app`. Remove the sibling lookups.
3. Bump the `ViewFileIndex` version, since stored keys change.
4. Run the `cake2` tests, then the other versions' layout navigation tests.

No assumption test applies: the classification depends on how the project is laid out and
configured, not on what CakePHP does at runtime.

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
the one from #317. No other assumption test applies: what is left depends on how the project is laid
out and configured, not on what CakePHP does at runtime.

A review pointed out that the bug also misclassifies a CakePHP 2 controller straight away, not only
depending on indexing order, and that only the layout keys change. Both were confirmed in the code,
and the first was reproduced by the first regression test of the plan, which fails as expected and
passes once the `templates` file is removed. The plan above was written from that.
