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

In the order to evaluate them:

### 1. `PhpTypeInferenceExclusion` (needs a newer IDE)

New since 232: `com.jetbrains.php.typeInferenceExclusion`, with
`isExcludedFromTypeInference(PsiElement)`. `PhpTypeInfo.getTypeCached` and `getTypeFromAST`
return `PhpType.EMPTY` for excluded elements. The only built-in user excludes compiled Twig
templates. The idea is to exclude a `FieldReference` whose configured type is authoritative,
which might drop PhpStorm's own `#P` signature and with it the `__get` fallback, while keeping
our provider's type.

"Authoritative" means `MemberTypeResult.hasInitializeLoad`, the flag the type providers already
use to stop adding convention candidates. `MemberLoad.isInitializeLoad` counts both an
`initialize()` call and a property array such as `$helpers`, so the exclusion must key on that
flag, not on `initialize()` alone. The acceptance tests cover three paths, and the experiment
needs all three:

| Load path | Provider |
|---|---|
| `loadComponent()`/`loadModel()` in a controller's `initialize()` | `ControllerFieldTypeProvider` |
| `loadHelper()` in a view's `initialize()` | `ViewHelperInViewTypeProvider` |
| A helper's `$helpers` property array | `ViewHelperInViewHelperTypeProvider` |

Unknowns, to settle by experiment:

- Do `PhpTypeProvider4` results survive the exclusion, or does it blank the whole element?
- Is calling `ConfiguredMemberService` from inside type inference safe from recursion and
  index-access problems?
- Which build introduced the extension point? This sets the new minimum (currently
  `pluginSinceBuild = 232`). Check 241, 242, 243, 251 and 252 plugin jars.

Raising the minimum IDE version is a product decision. Settle it before the plugin depends on
this.

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

Approaches can combine. For example, 1 where the IDE supports it, and 2 for older IDEs or as an
explicit user choice.

## Plan

1. **Bytecode check of approach 1.** Before any platform work, read the 263.7307.44 bytecode to
   find out whether an exclusion can help at all. Two questions decide it:
   - Are `PhpTypeProvider4` results added inside the `getTypeCached`/`getTypeFromAST`
     short-circuit, so an exclusion would blank our type too, or somewhere it survives?
   - Is PhpStorm's own `#P` field signature, which leads to the `__get` fallback in
     `PhpIndexImpl.getMembers`, produced on the path the exclusion empties?

   Approach 1 continues only if our types survive and the `#P` signature does not.
2. **Check whether our plugin is involved in the `IsUnique.php` indexing failure.** The real
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
3. **Find when the extension point arrived,** only if step 1 keeps approach 1 alive. Check the
   newest PHP plugin build of each branch from 233 to 262 for `typeInferenceExclusion`, to learn
   the minimum IDE build approach 1 would require.
4. **Experiment with approach 1.** On a throwaway platform bump, register an exclusion for
   `FieldReference`s whose configured type has `hasInitializeLoad`, covering all three load
   paths, and run the 28 real-mode tests. Record whether provider types survive, and check
   performance and recursion behavior.
5. **Decide with the user.** Choose between approach 1 (with the new minimum build), approach 2,
   or both. If step 1 rules out approach 1, the decision is between approach 2 and approach 3.
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
