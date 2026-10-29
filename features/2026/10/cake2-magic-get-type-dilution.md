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
  `\void`, which they filtered out. Give the stub the real `@return mixed`, so stub mode shows
  the same dilution real mode does, and the tests prove the fix in both.

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
3. **Ruled-out members.** Decide what `$this->Unloaded` should be. At runtime
   `Controller::__get()` returns null for it, so `\null` is accurate; returning a non-empty
   `\null` type stops the `CakeRequest` methods from appearing. Check that PhpStorm treats a
   `\null`-only result as a result.
4. **Incomplete kinds and convention-only members.** Return the configured type plus the
   convention candidates, so `__get()` stops diluting the fallback too. This covers CakePHP 3 to
   5 convention-only members as well, which changes what #295 deliberately left alone: check
   those against real CakePHP 5 before deciding.

## Plan

1. Give the cake2 stub `Controller::__get()` and `Model::__get()` the real return types, and turn
   the affected cake2 assertions back into complete types. Confirm they fail in both modes.
2. Model receivers (1), with the association by-name fallback.
3. Chained receivers (2).
4. Ruled-out members (3).
5. Incomplete kinds (4), CakePHP 2 first, then CakePHP 3 to 5 after checking real mode.
6. Remove `projectClassTypes()`; run the full suite in stub and real mode.

## Dependencies

Builds on `configuration-code-disambiguation-cake2`, for CakePHP 2 configured-member
resolution, `isCakeTwoModel()`, `MemberTypeResult.excludedKinds` and the model chain signature.
Start once that branch is merged, or branch from it.
