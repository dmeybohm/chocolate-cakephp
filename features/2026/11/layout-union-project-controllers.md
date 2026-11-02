# Leave library controllers out of the layout helper union

## Context

A layout's helper receivers are every controller in the project: `RenderingControllers.allControllers`
takes all subclasses of the version's framework controller class, plus `AppController` and its
subclasses on CakePHP 2 (see [the layout rule](../10/rendering-controllers-element-plugin.md) and
[controller-side helpers](../10/controller-side-helpers.md), section 2). An alias settles the type
only when every receiver settles it; otherwise the convention class stays in the union.

Adding parity tests on the `cake5-layout-helper-test-parity` branch showed that on CakePHP 3 to 5
the convention type never leaves a layout union. The framework ships
`Cake\Controller\ErrorController extends Controller`, which configures nothing, and it is always
indexed because the vendor is a library. `ControllerSideHelperTest` on 3, 4 and 5 pins this, and
`FrameworkNotIndexedControllerHelperTest` on each shows the union is exact when only app controllers
exist. In a real project the gap is wider: every vendor plugin's controllers, such as DebugKit's, are
indexed and sit in the union too.

CakePHP 2 is different only by accident. Its `CakeErrorController` extends `AppController`, so an
`AppController` alias reaches it through `_mergeControllerVars()`.

The cost today is an extra union member, never a missing one. It only shows when an alias names an
unrelated class while a convention class with different methods also exists. It still makes the
exact-set rule the cake2 tests pin unreachable on later versions, and go-to-declaration lists two
targets for an alias of a convention subclass.

## Decision

A layout gets the controllers of the project, not of its libraries: controllers whose file is in
project content, plus controllers under a configured plugin path, so a composer-installed plugin the
user configured still counts. The filter applies only to the layout union in `allControllers`.
Templates and elements reach their controllers through references in the view file index, which a
library controller does not produce for a project template, so they need nothing.

The rule is version-neutral. On CakePHP 2 it drops `CakeErrorController`, which inherited
`AppController`'s aliases anyway, so no CakePHP 2 result changes.

### Why `ProjectFileIndex` rather than paths

The plugin already decides a file's plugin from its path (`cakeThreePluginName`), but it has no
path rule for "a file of the app". `matchesAppDirectory` compares a directory's name with the
configured `src`, and `vendor/cakephp/cakephp/src/Controller/ErrorController.php` has a `src` too.
`ProjectFileIndex.isInContent` and `isInLibrary` tell the two apart without a path rule.

**Check first:** how a real PhpStorm project marks `vendor/`. The tests attach the framework as a
module library, where `isInLibrary` holds and `isInContent` does not. PhpStorm's Composer
integration also registers `vendor` as a library root and excludes it, but confirm which of the two
predicates holds there before choosing one, and whether a configured plugin path inside `vendor/`
is excluded as well. The configured-plugin-path clause exists for that case.

**Answer:** PhpStorm's Composer integration (with "add packages as libraries", the default) does
both for each installed package: `ExcludedDirectoriesApplier` adds the package directory to the
module's excluded folders, and `IncludePathApplier` adds it to the PHP include path, which
`PhpIncludePathManager` serves as library roots. So a vendor file has `isInContent` false and
`isInLibrary` true, as in the tests, and a configured plugin under `vendor/` is excluded too, which
the configured-plugin-path clause covers. With that option off, `vendor/` stays in content and the
filter keeps its controllers, which is today's behaviour. The filter uses `isInContent`.

The lookup runs inside the cached search that `RenderingControllers.of` starts from a read action,
so `ProjectFileIndex` can be queried where `allControllers` already resolves each class's file.

### Cache invalidation

`RenderingControllers.of` caches its results on `PhpFilesModificationTracker` alone. The filter
reads two more inputs, and a change to either must drop the cache without waiting for a PHP file to
change:

- Which roots are libraries. `ProjectRootManager` is a `SimpleModificationTracker` that is bumped
  on every root change, so it joins the cached value's dependencies.
- The configured plugin paths. `Settings` has no tracker, and its properties are read-only views
  over the state, so `loadState()` is the settings UI's only write path. Add a
  `SimpleModificationTracker` to `Settings`, bumped in `loadState()`, and depend on it. The state
  object `getState()` returns is mutable, and some `SettingsTest` cases assign its `pluginConfigs`
  directly; such writes bypass the tracker, so the tests here load a state instead. The
  autodetected app directory comes from `composer.json` through `CakePhpFilesModificationTracker`,
  which joins too.

The settings forms already react to a plugin config change, but only by requesting a rebuild of
the view file and view variable indexes (`PluginForm.requestIndexRebuild`, and the same in
`ConfigForm` and `DataViewsForm`). That reindexes files; it fires no VFS events, so
`PhpFilesModificationTracker` does not move and a `CachedValue` depending on it is not dropped.
The rebuild keeps the indexes right, and the tracker keeps the caches over them right.

The plugin-path gap exists today: `resolvesTo` and `pluginOf` already read the plugin configs
inside the same cache, so a changed path keeps a stale result until a PHP file changes. The new
dependencies fix that as well. `ViewHelperReceivers` has caches with the same single dependency;
they are left as they are here and noted as a follow-up.

### What this gives up

- A layout inside a library, such as a vendor plugin's own `templates/layout/default.php`, no
  longer gets that plugin's controllers unless the plugin is configured. Nobody edits those.
- A controller in a plugin found only by the `plugins/{Name}` convention is in content, so it
  still counts. Only library controllers outside any configured plugin path are dropped.

## Plan

1. Confirm the predicate against a real project as described above, and record the answer here.
2. Filter `allControllers` in `RenderingControllers` to project controllers, through a small
   `isProjectFile(file)` on `ViewFileResolution` or beside `pluginOf`, which already takes the
   settings and the project directory.
3. Add the settings tracker, and make the `RenderingControllers` cache depend on it,
   `ProjectRootManager` and `CakePhpFilesModificationTracker` alongside `PhpFilesModificationTracker`.
4. Flip the three `test a layout keeps the convention type as the framework's ErrorController
   settles nothing` tests to assert the exact union without the convention type. Drop the
   `FrameworkNotIndexedControllerHelperTest` classes on cake3 and cake4, which only existed to show
   the cause, and the matching test on cake5, keeping its any-depth test.
5. Run the cake3, cake4 and cake5 `ControllerSideHelperTest` classes and the cake2 layout helper
   tests, then leave the full suite to CI.

## Tests

- A layout unions the aliases without the convention type when every app controller settles the
  alias, with the real framework attached, on cake 3, 4 and 5.
- A controller of a configured plugin that is a library still counts in a layout union. The fixture's
  `vendor/test/test_plugin` is in project content, so it would pass even if the filter ignored the
  configured paths. Instead the test writes a controller to a temporary directory on disk whose path
  ends in `vendor/test/test_plugin/src/Controller`, which `cakeThreePluginName` matches as a suffix,
  attaches that directory with `PsiTestUtil.addProjectLibrary` (the overload that returns the
  `Library`, removed again in `tearDown` since the light project is shared), checks the file is
  indexed, and asserts the plugin controller's alias is in the layout union. `ErrorController`'s
  absence in the same union pins the library exclusion.
- Changing the configuration drops the cache. After the lookup above, load a settings state whose
  plugin path no longer names that directory, with no PHP file changed in between, and assert the
  library controller's alias leaves the union. Then restore the path and assert it returns. A root
  change is not tested: adding and removing the library in the test above already goes through
  `ProjectRootManager`, and the platform bumps it.
- A plugin controller under `plugins/{Name}` still counts, which the existing
  `test a plugin controller bypassing AppController counts in a layout` covers.
- The cake2 `AppControllerHelperTest` and `DirectControllerHelperTest` are unchanged and still pass.

## Dependencies

Branched from `cake5-layout-helper-test-parity`, which adds the tests this change flips. Merge that
branch first, or rebase this one onto `main` once it lands.

## Implementation Progress

### Session #1

- Rebased onto `main` now that `cake5-layout-helper-test-parity` has landed.
- Read the Composer integration in the bundled PHP plugin (232.8660.205) for step 1; the answer
  is recorded under "Why `ProjectFileIndex` rather than paths".
- `ViewFileResolution.isProjectFile` holds for a file in project content or under a configured
  plugin path (`isInConfiguredPlugin` in `CakePlugins.kt`, matching CakePHP 2 paths from the
  project directory and CakePHP 3+ paths as suffixes, as the plugin name rules do).
  `RenderingControllers` filters the layout union through it.
- `Settings.modificationTracker` is bumped in `loadState()`. The `RenderingControllers` cache
  depends on it, `ProjectRootManager` and `CakePhpFilesModificationTracker` as well as
  `PhpFilesModificationTracker`.
- The three ErrorController layout tests now assert the exact union. The cake3 and cake4
  `FrameworkNotIndexedControllerHelperTest` classes are gone, and the cake5 one keeps only its
  any-depth test.
- New cake5 tests: a configured plugin's controller in a library counts in a layout, and changing
  the plugin path drops it from the union and restores it, with no PHP file changed. Each fails
  when the configured-plugin clause or the settings tracker dependency is removed.
- The cake3, cake4 and cake5 `ControllerSideHelperTest` classes and all the cake2 helper tests
  pass. The full suite is left to CI.
- Follow-up, unchanged here: `ViewHelperReceivers` caches still depend on
  `PhpFilesModificationTracker` alone.

### Session #2

- Review: the project filter ran after `allControllers` dropped duplicate FQNs, so when a library
  copy of a project controller came first, both copies were lost. The filter now runs inside
  `allControllers`, before `distinctBy`.
- New cake5 test: a project controller counts in a layout when a library has a copy of it. It adds
  the library copy after the project file, the order in which the index returns the library copy
  first, and fails without the fix.
