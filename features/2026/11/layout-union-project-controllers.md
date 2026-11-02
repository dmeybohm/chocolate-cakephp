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

The lookup runs inside the cached search that `RenderingControllers.of` starts from a read action,
so `ProjectFileIndex` can be queried where `allControllers` already resolves each class's file.

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
3. Flip the three `test a layout keeps the convention type as the framework's ErrorController
   settles nothing` tests to assert the exact union without the convention type. Drop the
   `FrameworkNotIndexedControllerHelperTest` classes on cake3 and cake4, which only existed to show
   the cause, and the matching test on cake5, keeping its any-depth test.
4. Run the cake3, cake4 and cake5 `ControllerSideHelperTest` classes and the cake2 layout helper
   tests, then leave the full suite to CI.

## Tests

- A layout unions the aliases without the convention type when every app controller settles the
  alias, with the real framework attached, on cake 3, 4 and 5.
- A controller under the configured plugin path (`vendor/test/test_plugin`) still counts in a layout
  union. In the test fixture that path is in content, so this pins the clause without exercising the
  library case; the library case is pinned by `ErrorController`'s absence.
- A plugin controller under `plugins/{Name}` still counts, which the existing
  `test a plugin controller bypassing AppController counts in a layout` covers.
- The cake2 `AppControllerHelperTest` and `DirectControllerHelperTest` are unchanged and still pass.

## Dependencies

Branched from `cake5-layout-helper-test-parity`, which adds the tests this change flips. Merge that
branch first, or rebase this one onto `main` once it lands.
