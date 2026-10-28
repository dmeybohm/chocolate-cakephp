# Fix `__get` type dilution of configured members

## Context

Running the suite against real CakePHP source (`real-cakephp-vendor-spike`, PR #294) showed
that the precise types the plugin gives configured components, tables and helpers are diluted
in real projects. The stubs never declared the framework's magic `__get`, so the tests never saw
it.

For a configured `$this->Legacy` (loaded as `MovieMetadataComponent`), PhpStorm's type is:

| Version | Extra types unioned in | Source |
|---|---|---|
| Cake 5 | `Component\|Table\|null\|mixed` | `Controller::__get` declares `Component\|Table\|null`, returns `mixed` |
| Cake 4 | `RepositoryInterface\|null` | `Controller::__get` (old `loadModel()` magic) |
| Cake 3 | `object\|bool` | `Controller::__get` declares `bool\|object` |

Helpers get the same treatment from `View::__get` and `Helper::__get`: `Helper|null` on Cake 4
and 5, plus `void` from Cake 4's `Helper::__get`. On Cake 3, `View::__get` declares `mixed`, so
PhpStorm infers `ViewBlock|array` from its body, and `Helper::__get` contributes an inferred
`array` as well.

What users see: with real Cake 5, completion on `$this->Legacy->` offered 123 items, every `Table`
method among them, where the stubs gave just the component's `generateMetadata`. On Cake 4 the
leak is `RepositoryInterface`'s `find`/`get`/`save`/`delete`. For helpers the extra members are
mostly inherited anyway, so the visible effect there is imprecise type text and `null`. Precise
completion is the point of the configured-member feature, so this needs fixing rather than
documenting.

## Root cause

From the PHP plugin bytecode (232.8660.205, and unchanged in 263.7307.44): for a field signature
`#P#C\Class.Name`, `PhpIndexImpl.getMembers` calls `PhpClassImpl.findFieldsByName`. When that
finds nothing, it falls back to the class's `__get` and adds that method's type. Our type
providers (`ControllerFieldTypeProvider`, `ViewHelperInViewTypeProvider`,
`ViewHelperInViewHelperTypeProvider`) add the precise type alongside, but a `PhpTypeProvider4`
can only add types, never remove them.

Ruled out:

- **No member-contributing extension point,** in either 232 or 263.
- **`magicMethodProvider` is not a type API.** It is part of dead-code detection only.

Confirmed:

- **A `@property` tag works.** On the controller, `@property \App\Controller\Component\MovieMetadataComponent $Legacy`
  makes `findFieldsByName` succeed. In real mode, the type narrows to exactly that class.

## Acceptance

The 28 tests marked `assumeStubVendor(magicGetDilution)` pass in real mode with the skip
removed:

- **cake3, cake4:** `ConfiguredComponentTest` (4), `ConfiguredHelperTest` (4) and
  `ConfiguredTableTest` (2) each.
- **cake5:** `ConfiguredComponentTest` (4) and `ConfiguredHelperTest` (4).

These tests already detect primitive dilution. Their type helpers keep entries that start with
`\`, and PhpStorm writes primitive types that way (`\null`, `\mixed`, `\bool`, `\void`,
`\array`). The real-mode failures showed every one of these in the assertion messages, so the
`assertEquals` on the full set fails on any extra type. The filter drops only unresolved `#…`
signatures.

If an approach can't make the type exact, those tests change to assert what it does guarantee,
for example completion contents, and that trade-off is recorded here. Stub mode stays fully
green, and `assumeStubVendor` stays available for other uses.

## Approaches

In the order to evaluate them, updated after the step 1 bytecode check:

### 0. A provider keyed on the native field signature (works on 232)

`PhpIndexImpl.doCompleteType`, which resolves every signature, first offers it to each
`PhpTypeProvider4` whose `getKey()` equals the signature's key character (`s.charAt(1)`). PhpStorm's
own field signature uses `PhpTypeSignatureKey.FIELD`, which is `P` (`#P#C\Class.Name`). After a
matching provider's `complete()` returns a non-empty type:

- **`interceptsNativeSignature()` is `false`:** `doCompleteType` returns without running native
  resolution, unless another provider shares the key and the result is ambiguous. Native
  resolution (`getBySignature`, then `getMembers`) is where the `__get` fallback happens, so it is
  skipped.
- **`interceptsNativeSignature()` is `true`:** the result is added, and native resolution still
  runs.
- **`complete()` returns `null`:** native resolution runs as usual.

So a provider keyed `P` with `interceptsNativeSignature() == false`, whose `complete()` returns the
configured type for an authoritative member (`hasInitializeLoad`) and `null` for everything else,
replaces the native field lookup for exactly those fields. Every other field keeps PhpStorm's
normal behavior. Native keys are a sanctioned pattern: the bundled Prophecy providers key on
`PhpTypeSignatureKey.METHOD` (with `interceptsNativeSignature() == true`). The same
`doCompleteType` structure is present in 263.7307.44, so the mechanism holds across the supported
range.

Unknowns, to settle by experiment:

- **Receiver encoding.** The receiver part of `#P` can be another signature (`#C\…`, a variable,
  a method call, a union). Find out what `$this->Field` in controllers, views, templates and
  helpers looks like, and whether `complete()` should resolve a non-class receiver through
  `PhpIndex` or leave it alone.
- **Cost.** `complete()` runs for every `#P` signature in the project. It needs a cheap string
  filter (an uppercase field name, a receiver that could be a controller, view or helper) before
  any index access.
- **Other plugins.** A third-party provider keyed `P` would hit the ambiguity branch. Check the
  behavior if two providers both answer.
- **Recursion.** `complete()` runs under `doCompleteType`'s recursion guard. Index lookups are
  normal in `complete()` (our providers already do them), but confirm with the real-mode suite
  and the cold-index run.

### 0b. `PhpTypeProvider4.replacesTypeFromAst()` (263 only)

In 263 (absent in 232), `PhpTypeInfo`'s provider loop collects the types of providers whose
`replacesTypeFromAst()` is `true` separately. If any are non-empty, it returns only those and
drops the AST type, PhpStorm's own `#P` signature included. It is decided per provider, not per
element, and at `getType()` time, before indexes are available, so it can't tell which fields are
authoritative. It would also need a raised minimum IDE version. Approach 0 is preferred, and this
is kept as a fallback.

### 1. `PhpTypeInferenceExclusion`: ruled out

`typeInferenceExclusion` (added after 232) makes `PhpTypeInfo.getTypeFromAST` return
`PhpType.EMPTY` for an excluded element *before* the provider loop runs. That loop lives in
`lambda$getTypeFromAST$0`, which is where `PhpTypeProvider4.getType` gets called. Excluding a
configured `$this->Field` would therefore blank our provider's type along with PhpStorm's.

### 2. `@property` inspection and quick-fix (works on 232)

An inspection on controllers, views and helpers that load members without a matching
`@property` tag, with a quick-fix that adds the tags, similar to what `cakephp-ide-helper`
generates. It goes through PhpStorm's own mechanism, so completion, type text, goto and other
inspections all become exact. The cost is that each class needs a user action, and the tests
would apply the fix or add the tags before asserting.

### 3. Filter completion only (fallback)

A completion contributor that runs the remaining contributors and drops items that only the
`__get` types contribute, when the plugin resolved the field. No user action, but type text,
goto and inspections stay diluted, and it depends on contributor ordering.

Approaches can combine. For example, approach 0 for the type, with approach 2 offered as an
explicit user choice for documenting loaded members in the code.

## Plan

1. **Bytecode check of approach 1.** Before any platform work, read the 263.7307.44 bytecode to
   find out whether an exclusion can help at all. Two questions decide it:
   - Are `PhpTypeProvider4` results added inside the `getTypeCached`/`getTypeFromAST`
     short-circuit, so an exclusion would blank our type too, or somewhere it survives?
   - Is PhpStorm's own `#P` field signature, which leads to the `__get` fallback in
     `PhpIndexImpl.getMembers`, produced on the path the exclusion empties?

   Approach 1 continues only if our types survive and the `#P` signature does not.
2. **Check whether our plugin is involved in the `IsUnique.php` indexing failure.** Done: it
   isn't (session 2). The real
   vendor tests disable `RecursionManager`'s missed-cache and recursion-prevention assertions
   because stub building for CakePHP 5's `ORM/Rule/IsUnique.php` hit a recursion guard on
   `php.type.info->FieldImpl: _options`. That was attributed to the PHP plugin, but stub
   building runs type inference, which may call our type providers. Recent changes added or
   reworked several of them (`ControllerFieldTypeProvider`, `ViewHelperIn*TypeProvider`,
   `AssociatedTableTypeProvider`, `TableLocatorTypeProvider`, `ViewVariableTypeProvider`).
   - Capture the complete stack of the failure, with the assertions left on, and look for
     `com.daveme` frames.
   - Reproduce on a cold index with the assertions on, with this plugin's type providers
     unregistered, and at a commit from before the recent type-provider changes. If the failure
     goes away, find the provider responsible and fix it.
   - If our plugin is involved, the fix should let `BaseTestCase` drop the `RecursionManager`
     workaround. If not, record the evidence here, and keep the workaround scoped to real mode.
3. **Version search: not needed for now.** Approach 1 is ruled out, and approach 0 uses APIs
   present in 232. Only check which build introduced `replacesTypeFromAst()` if approach 0 fails
   and 0b becomes the fallback.
4. **Done (session 3), it passes. Experiment with approach 0** on the current platform (232). Add a provider keyed `P` whose
   `complete()` answers authoritative members on all three load paths. Run the 28 real-mode tests
   with their skips removed, then the full suite in both modes, plus a cold-index real-mode run.
   Record the receiver encodings seen, the cost, and any recursion.
5. **Decide with the user.** Choose approach 0 if the experiment passes. Otherwise choose between
   0b (with a raised minimum build), approach 2 and approach 3.
6. **Implement for Cake 5 first,** then Cake 4 and 3, removing the matching skips as each
   version passes.
7. **Full suite in both modes, then update CI** if the platform version changes.

## Open questions

- **Cake 2.** `Controller::__get`, `View::__get` and `Helper::__get` in 2.10.24 all declare
  `mixed`, so Cake 2 models, components and helpers are diluted too, plus whatever PhpStorm
  infers from the method bodies. Real mode raised no Cake 2 failures only because no Cake 2 test
  asserts an exact member type. Add such a test first, and decide whether the fix covers Cake 2's
  `$uses`/`$components`/`$helpers` path.
- **Unconfigured members.** Properties resolved by convention only, with no `initialize()` load,
  are also diluted. Should the fix cover them, or only members with an `initialize()` load?

## Implementation Progress

### Session #1

Plan step 1, the bytecode check of PHP plugin 263.7307.44 against 232.8660.205:

- **`typeInferenceExclusion` can't help.** `PhpTypeInfo.getTypeFromAST(element, visitor, bool)`
  checks `PhpTypeInferenceExclusions.isExcludedFromTypeInference` and returns `PhpType.EMPTY`
  before calling `lambda$getTypeFromAST$0`. That lambda computes the AST type and then loops over
  `PhpTypeProvider4.getType` results, so our providers never run for an excluded element.
- **`replacesTypeFromAst()` (263 only).** In that loop, providers returning `true` go into a
  separate type set. If that set is non-empty it is returned on its own, and the AST type and
  additive provider types are dropped.
- **Native-key interception (232 and 263).** In `PhpIndexImpl.doCompleteType`, a provider whose
  key matches the signature's key character gets `complete()` first. A non-empty result from a
  provider with `interceptsNativeSignature() == false` returns before native `getBySignature`
  resolution, unless another provider shares the key and the result is ambiguous.
  `PhpTypeSignatureKey.FIELD` is `P`, and `CLASS` is `C`. The bundled Prophecy providers key on
  `METHOD` (`M`) with intercepting on, which shows native keys are supported.

This led to approach 0, which needs no platform bump. The version search is deferred.

### Session #2

Plan step 2: is this plugin involved in the `IsUnique.php` indexing failure? **No.**

- **Stack with the assertions on.** In a cold real-mode run of
  `cake5.ViewVariableResolutionTest`, with the two `RecursionManager.disable*` calls commented
  out, the failure reproduced: `Failed to build stub tree for IsUnique.php`, `Preventions:
  [php.type.info->FieldImpl: _options]`. None of the 144 trace lines is a `com.daveme` frame. The
  chain is entirely the PHP plugin's AST analysis. `DefaultStubBuilder.createStub` asks for the
  field's type, `PhpTypeAnalyserVisitor.visitPhpField` infers it from the assignment, then
  `visitPhpBinaryExpression` (`$options + $this->_options`), then `visitPhpFieldReference`, then
  `FieldImpl.getType` on the same field, where the guard stops it. Type providers run in the same
  lambda but after the visitor, so they aren't part of the cycle.
- **With every plugin type provider masked.** `ExtensionTestUtil.maskExtensions` on
  `PhpTypeProvider4.EP_NAME`, keeping everything except `com.daveme` classes (6 of 50 removed),
  was applied after the application loaded and before the project opened, on a wiped index. The
  same file failed with the same prevention key in two cold runs. This covers every provider
  regardless of version, so the comparison at `v1.1.0` (from before the 2026-10-27 provider
  changes) is unnecessary. Only type providers take part in stub-building type inference, and the
  plugin's file-based indexes run separately.
- **A masking pitfall.** The first masked run failed earlier, with `Failed to add indexing
  statistics` (a `ConcurrentModificationException` in `ProjectDumbIndexingHistoryImpl`). Changing
  the extension point seems to start a second indexing pass that races the first one's
  statistics. Two reruns didn't hit it. Expect this flakiness in any test that masks
  `PhpTypeProvider4` during project setup.

Conclusion: the failure is a PHP plugin issue with self-referential field inference, triggered
by real framework code. The `RecursionManager` workaround in `BaseTestCase` stays, limited to real
mode, as it is now.

### Session #3

Plan step 4: approach 0 works.

Native signatures, from a real-mode probe on Cake 5, are below. Each also appears wrapped as
`#π(#P…)` by another provider, and that resolves its inner `#P` signature through the same
`doCompleteType` path.

| Path | PhpStorm's own field signatures |
|---|---|
| Controller `$this->Legacy` | `#P#C\App\Controller\ConfiguredController.Legacy`, `#P#S\…ConfiguredController.Legacy` |
| Helper `$helpers` `$this->Alias` | `#P#C\…\ConfiguredHelper.Alias`, `#P#S\…ConfiguredHelper.Alias` |
| Template `$this->Fmt` | `#P#C\App\View\AppView.Fmt` |

So a receiver arrives as `#C` (`PhpTypeSignatureKey.CLASS`) or `#S` (`POLYMORPHIC_CLASS`).

`ConfiguredMemberFieldTypeProvider` (in `configuredmemberindex`, registered as a
`typeProvider4`):

- **Key and hooks.** It is keyed on `PhpTypeSignatureKey.FIELD` (`P`). `getType()` always returns
  `null`, `interceptsNativeSignature()` keeps its default of `false`, and `getBySignature()` is
  empty.
- **Cheap filters before any index access.** `complete()` only considers `#P#C\Fqn.Field` and
  `#P#S\Fqn.Field` with an uppercase field. Receivers are classified the way the existing
  providers do it: `…Controller` gives `COMPONENT` and `TABLE`, and `…View` or `…Helper` gives
  `HELPER`. Everything else gets `null`.
- **Then the lookup.** It skips dumb mode and a disabled plugin. It asks `ConfiguredMemberService`
  for the member and answers only when `hasInitializeLoad` is true (an `initialize()` load or a
  `$helpers` array), and only when the class doesn't declare the field itself. The answer is the
  configured type, so `doCompleteType` returns before native resolution and the `__get` fallback.
  In every other case it returns `null`.

Results:

- **Targeted runs.** With the 28 `assumeStubVendor(magicGetDilution)` skips and their reason
  fields removed (each test file is now identical to its pre-skip version, apart from the
  `configureByFiles` helper rename), real mode passes every `Configured*` class: cake5 40, cake4
  44, cake3 44, all with no skips.
- **Full suite.** Stub mode: 961 tests, 0 failed, 0 skipped (8 m 06 s). Real mode on a wiped
  index: 961 tests, 0 failed, 0 skipped (10 m 43 s, including indexing all four framework
  versions from scratch). No new recursion or indexing errors appeared. The `IsUnique.php`
  workaround still applies.
- **No `@property` needed.** None of the fixtures have `@property` tags. A class that declares
  the field is left to native resolution.

Still not covered, for the decision in step 5:

- **Convention-only members,** with no `initialize()` load or `$helpers` array, are still
  diluted. The provider deliberately doesn't answer for them, because convention gives a union
  of candidates rather than an authoritative class.
- **Receivers that aren't `#C`/`#S`,** such as `$controller->Legacy` through a variable or a
  method call's result, keep native resolution and so are still diluted.
- **Cake 2** has no configured-member index (`$components`/`$uses` arrays aren't indexed), so
  nothing changes there yet. See Open questions.
- **No completion test yet.** No test asserts the user-visible symptom directly, for example
  that completion on `$this->Legacy->` in real mode no longer offers `Table` methods. The 28
  exact-type assertions cover it indirectly.


### Session #4

Added `test completion on a loaded component leaves out table methods` to the Cake 5
`ConfiguredComponentTest`. It checks that completion on `$this->Legacy->` offers
`generateMetadata` and not `newEntity`. It passes in both modes. With the provider's
`plugin.xml` registration commented out, it fails in real mode, with completion offering every
`Table` method. That closes the "No completion test yet" gap from Session #3.

Also added a Vim swap file pattern (`.*.sw[a-p]`) to `.gitignore` after one was nearly committed.
