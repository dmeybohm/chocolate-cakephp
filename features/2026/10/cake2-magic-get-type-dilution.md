# Fix `__get` type dilution of CakePHP 2 configured members

## Context

`fix-magic-get-type-dilution` (PR #295) added `ConfiguredMemberFieldTypeProvider`: for PhpStorm's
native field signature `#P#C\Receiver.Field`, when the receiver's configuration determines the
member (`hasInitializeLoad`) and the class does not declare the field, it returns the configured
type, which stops PhpStorm from adding the type of the class's magic `__get()`. It left
CakePHP 2 out, since CakePHP 2 had no configured-member resolution then.

`configuration-code-disambiguation-cake2` added that resolution. Run against real CakePHP 2.10
(`-PcakeVendor=real`), its tests showed the `__get()` types still leaking in:

- **Associations on models.** `Model::__get()` adds `\bool|\string|\mixed|\null` to
  `$this->Director` inside a model and to `$this->Movie->Director` in a controller. The provider
  only knows receivers named `...Controller`, `...View` and `...Helper`, and only class
  receivers (`#C`/`#S`), so it handles neither a model nor a chain.
- **Members the configuration rules out.** With `$components` and `$uses` known, `$this->Unloaded`
  on a controller has no configured type, and resolves to `Controller::__get()`'s
  `\CakeRequest|\string|\mixed|\null`. The provider returns the empty configured type, which
  PhpStorm treats like no result. Completion on `$this->Unloaded->` then offers `CakeRequest`'s
  methods.
- **Incomplete kinds.** An incomplete `$components` keeps the convention fallback
  (`\FooComponent`), and `hasInitializeLoad` is false, so the provider steps aside and the
  `__get()` types come back alongside it. The same gap exists for CakePHP 3 to 5 convention-only
  members (Session #3 of `fix-magic-get-type-dilution`).

## Test workarounds to remove

- `Cake2BaseTestCase.projectClassTypes()`, used by `ConfiguredControllerTest` and
  `ConfiguredAssociationTest`, drops PHP pseudo-types and classes found only in the attached
  framework library, so the type assertions pass in real mode. With the fix, those tests can
  compare the complete types again in both modes.
- The stub `Controller::__get()` has no return statement, so in stub mode the same tests saw
  `\void`, which they filtered out. The stub `Model` has no `__get()` at all, so stub mode can't
  show association dilution yet. Give `Controller` the real `@return mixed`, and add
  `Model::__get()` with the same, so the tests prove the fix in both modes. Real mode's
  `\CakeRequest` and the other non-`mixed` types are inferred from the method bodies, so
  `@return mixed` alone should only reproduce `\mixed` in stub mode. The ruled-out completion
  symptom needs real mode, or a stub body that returns a request.

Two other test accommodations from that branch are not `__get` related and stay where they are
logged: creating the `Elements` directory before the templates that include elements is the
ordering bug in `resolve-element-paths-at-lookup`, and component convention completion listing
only subclasses of `\AppComponent` predates both features.

## Approach

Extend `ConfiguredMemberFieldTypeProvider` rather than adding a CakePHP 2 provider, so the rules
stay in one place:

1. **Model receivers.** When the receiver is not a controller, view or helper by name, and
   CakePHP 2 is enabled, check `isCakeTwoModel()` at `complete()` time (index access is allowed
   there) and use `MemberKind.ASSOCIATION`. Associations keep their by-name fallback (bound with
   `bindModel()` elsewhere), so for an association the configuration does not determine, return
   the configured type plus the by-name model instead of stepping aside.
2. **Chained receivers.** Accept field signatures whose receiver is itself a signature, such as
   `#P#P#C\MoviesController.Movie.Director`: resolve the receiver part with `completeType()`, then
   apply the same rules to each resolved class. Bound the depth, as the model chain signature
   in `ControllerFieldTypeProvider` does.

   A non-empty result replaces native resolution for the whole signature, so it has to account
   for every resolved receiver. Answer only when every class among them is one the rules can
   answer for: a controller, view, helper or CakePHP 2 model that does not declare the field. The
   answer is the union of each class's type. If any other class is present, or a class declares
   the field, return null, so native resolution and the additive providers keep that class's type
   and the `__get()` types stay until a later fix. A view whose configuration does not list the
   helper still never contributes `\null` (see 3). Until step 5 of the plan gives it a convention
   type, it has no answer, so the whole signature returns null. PHP pseudo-types such as `\null`
   and `\mixed` have no fields, so they do not count as receivers. A framework class that
   `__get()` added to the receiver does count, so a chain through a member that isn't determined yet (for example
   `$this->Movie` with an incomplete `$uses`) stays diluted until step 5 of the plan fixes the
   receiver.
3. **Ruled-out members.** Decide what `$this->Unloaded` should be. At runtime
   `Controller::__get()` returns null for it, so `\null` is accurate; returning a non-empty
   `\null` type stops the `CakeRequest` methods from appearing. Check that PhpStorm treats a
   `\null`-only result as a result. Apply this to controller and helper receivers, and to models
   only if their by-name fallback does not apply. `Helper::__get()` only loads the helper's own
   `$helpers`, but `View::__get()` loads any helper by name, so a view receiver must not get
   `\null`.
4. **Incomplete kinds and convention-only members.** Return the configured type plus the
   convention candidates, leaving out `excludedKinds` as `ControllerFieldTypeProvider`'s private
   `conventionType()` does (known `$uses` with incomplete `$components` still rules out the
   table), so `__get()` stops diluting the fallback too. This covers CakePHP 3 to
   5 convention-only members as well, which changes what #295 deliberately left alone: check
   those against real CakePHP 5 before deciding.

Not in scope: helpers in CakePHP 2 templates and layouts. Those files get the native signature
`#P#C\AppView.Html`, and a project without an `AppView` class has nothing for `__get()` to
dilute with (see Session #1). A project that defines `AppView` is untested. The provider can't
apply the rendering controllers there in any case, as the native signature does not name the
view file.

## Plan

1. Check that PhpStorm keeps a `\null`-only result from `complete()`, with a real-mode experiment
   on `$this->Unloaded`. If it doesn't, decide on another result for ruled-out members before
   going on. Then give the cake2 stub `Controller::__get()` and `Model::__get()` the real return
   types, and turn the affected cake2 assertions back into complete types. Confirm they fail in
   both modes. These assertions now expect `setOf("\\null")` instead of an empty set:
   - `ConfiguredControllerTest`: `test an unloaded component gets no type even when its class
     exists`, the `$this->Movie` case of `test uses entries resolve and other models do not
     exist`, and `test uses false has no models`. The test names change to match.

   `ConfiguredAssociationTest`'s `test a model the controller does not load has no nested type or
   completion` keeps its empty set: its caret is on `Director`, on a `\null` receiver, which has no
   fields. Confirm this when the test runs.
2. Model receivers (1), with the association by-name fallback.
3. Chained receivers (2), with a test for a receiver that is both a model and another class: the
   other class's field type must survive.
4. Ruled-out members (3).
5. Incomplete kinds (4), CakePHP 2 first, then CakePHP 3 to 5 after checking real mode.
6. Remove `projectClassTypes()`; run the full suite in stub and real mode.

## Dependencies

Builds on `configuration-code-disambiguation-cake2` (merged in #299), for CakePHP 2
configured-member resolution, `isCakeTwoModel()`, `MemberTypeResult.excludedKinds` and the model
chain signature.

## Implementation Progress

### Session #1

Merged `main`, which brought #299 and the later #297, #298, #300, #301, #302 and #303. No code
has changed on this branch yet. Checked the plan against the merged code:

- **Still accurate.** `ConfiguredMemberFieldTypeProvider` is unchanged in the ways the Context
  describes: it only parses `#C`/`#S` receivers, knows controller, view and helper receivers, and
  returns the empty configured type for a ruled-out member. `projectClassTypes()` is still used
  only by `ConfiguredControllerTest` and `ConfiguredAssociationTest`.
- **Real `__get()` declarations (2.10.24).** `Controller`, `Model`, `View` and `Helper` all
  declare `@return mixed`.
- **Templates.** A temporary real-mode probe of `$this->Html` in `app/View/Movies/index.ctp`,
  with no `AppView` class in the project:

  | Case | Raw signatures | Complete type |
  |---|---|---|
  | Controller aliases `Html` | `#茕v2Movies/index茕茕Html`, `#π(#P#C\AppView.Html)`, `#P#C\AppView.Html` | `\BootstrapHtmlHelper`, `?` |
  | Template no controller renders | `#茕v2Movies/index2茕茕Html`, `#π(#P#C\AppView.Html)`, `#P#C\AppView.Html` | `\HtmlHelper`, `?` |

  No `View::__get()` types appear, so templates are left out of the plan.

Real mode, after the merge: `TemplateHelperTest`, `ConfiguredControllerTest` and
`ConfiguredAssociationTest` pass. The full suite was not run.

### Session #2

Implemented the plan in `ConfiguredMemberFieldTypeProvider`. `conventionType()` moved from
`ControllerFieldTypeProvider` to `controllerConventionType()` in `Classes.kt`, so both share it.
`projectClassTypes()` is gone, and the cake2 stub `Controller`, `Model`, `View` and `Helper` all
declare `__get()` with `@return mixed`.

- **`\null` works.** PhpStorm keeps a `\null`-only result from `complete()`. On real CakePHP 2,
  `$this->Unloaded` is exactly `\null`, and completion on it no longer offers
  `CakeRequest::referer`. That completion test fails in real mode without the fix.
- **Chained signatures.** Probing `$this->Movie->Director` and `$this->film()->Director` showed
  `#P#P#C\…`, `#P#π(#P#C\…).Director`, `#P#π(#M#C\…).Director` and the plugin's own signature as
  the receiver, so any receiver signature is accepted, on CakePHP 2 only for cost.
- **Mixed receivers need a parent class to test.** A `@return Movie|Plain` method in the same
  class also gives separate `#P#C\Movie.Director` and `#P#C\Plain.Director` signatures, so nothing
  is lost and a test there passes even with the all-or-nothing rule removed. With the method in a
  parent class only the chained signature exists: removing the rule gives `[\Person]`, losing
  `\Review`. With the rule the type is `\Review` plus the `__get()` types, and without
  `\Person`, which nothing added for a method receiver before this branch either.
- **Convention guard, not in the plan.** On CakePHP 3 to 5 the provider answers for a convention
  member only when a convention class exists. Otherwise an unknown `$this->Foo` would swap the
  `__get()` types for class names that don't exist. CakePHP 2 keeps its existing convention type
  either way, as `Controller::__get()` gives nothing useful there.
- **CakePHP 2 views step aside when undetermined.** Every CakePHP 2 template has
  `#P#C\AppView.Field`, so a convention answer there would add `\HtmlHelper` beside a controller's
  aliased helper.

Results:

- CakePHP 2 work (`2b00065`): `cake2.*` passes in stub and real mode.
- Full suite in stub mode at `91c4017`: 1300 passed, 0 failed, 1 skipped (the real-only test
  `test a field with no class by convention keeps its native type`).

Real mode, for the CakePHP 3 to 5 convention change and plan step 6:

- **Full suite in real mode at `7c77697`:** 1300 passed, 0 failed, 1 skipped (the stub-only
  `test without the framework and declarations convention stays`).
- **With the convention path disabled,** real CakePHP 5 fails `test an unloaded table property
  leaves out the framework's magic types`, with `\null`, `\mixed` and `\Cake\ORM\Table` in the type.
- **Core helpers never reach `__get()`.** The helper test first used `$this->Time` and passed with
  the path disabled, because the real `View` declares `@property \Cake\View\Helper\TimeHelper
  $Time`. It now uses the app's `ArtistFormatter`, and fails with the path disabled (`\null` and
  `\Cake\View\Helper` in the type).
- **CakePHP 3 and 4** have no test of their own for convention members. The real-mode suite only
  shows that they did not regress.

### Session #3

A review comment on #306 found the convention guard from Session #2 only looked at the convention
candidates. `BeforeFilterOnlyController` loads `Late` as `MovieMetadataComponent` in
`beforeFilter()`, and no `LateComponent` exists, so the provider stepped aside and real CakePHP 5
kept `\null`, `\mixed`, `\Cake\ORM\Table` and `\Cake\Controller\Component` beside the configured
class. The guard now also accepts a configured class. It checks `classFqns` without an index
lookup, so a load naming a class that can't be found also answers.

`test a beforeFilter load with no class by convention leaves out the framework's magic types`
fails in real mode without the fix. `Configured*` for CakePHP 3 to 5 passes in stub and real mode.
The type still includes the convention names such as `LateComponent`, which is the union #299 kept
for loads outside `initialize()`.
