# Controller-side helpers for CakePHP 3 to 5

## Context

`configuration-code-disambiguation` (PR #292) resolves helpers in CakePHP 3 to 5 templates
against `AppView`, from `loadHelper()` and `addHelper()` calls in the view class. It left out
helpers configured from the controller, because finding them needs to know which controller
renders a template:

> Not in this iteration: controller-side helper configuration through `viewBuilder()->addHelper()`
> and `setHelpers()`, and Cake 3's `$this->helpers` on controllers. Those need the
> template-to-controller mapping from `ViewFileIndex` to find the receiver, which is a separate
> piece of work.

`configuration-code-disambiguation-cake2` builds that mapping for CakePHP 2, where helpers are
always configured on the controller: rendering controllers per template, a walk back through
`$this->element()` includes for elements, and layouts through `$this->layout` assignments (with
`default` meaning every controller). This feature reuses it for CakePHP 3 to 5, and picks up
the CakePHP 3 to 5 side of two gaps that review of the CakePHP 2 plan found.

## Scope

### 1. Controller-side helper configuration

Sources, checked against the CakePHP 3.10, 4.6 and 5.4 `ViewBuilder` and `Controller`:

| Version | Source                                                                    | Notes |
|---------|---------------------------------------------------------------------------|-------|
| 3       | `$this->viewBuilder()->setHelpers([...], $merge = true)`, `helpers([...])` | `helpers()` is the older getter/setter |
| 3       | `public $helpers = [...]` on the controller                               | deprecated since 3.0, still read |
| 4       | `addHelper('Name', $options)`, `addHelpers([...])`, `setHelpers([...], $merge = true)` | |
| 5       | `addHelper()`, `addHelpers()`, `setHelpers([...])`                        | `setHelpers()` no longer merges |

A template's helper receivers become `AppView` plus its rendering controllers. Helpers the
controller configures are unioned with `AppView`'s, since `ViewBuilder` passes them to the view
and `View::initialize()` loads its own as well. To check in step 1: which wins for an alias both
configure, and whether `setHelpers()` without merge in 5 replaces `AppView`'s or only the
builder's earlier helpers.

### 2. Layouts

CakePHP 3 to 5 layouts resolve against `AppView` today, which is right while only `AppView`
configures helpers. With controller-side helpers, a layout has the helpers of whichever
controller renders it, so it needs the CakePHP 2 layout mapping:

- `$this->viewBuilder()->setLayout('name')` in 3.4+, 4 and 5, `layout('name')` in 3, and the
  deprecated `$this->layout` property in 3. The view file index already reads `viewBuilder()`
  calls for `setTemplate()` and `setTemplatePath()`, so `setLayout()` goes there.
- The layout path changes the key, as with CakePHP 2's `$layoutPath`: `setLayoutPath()` in 3.4+,
  4 and 5, and the deprecated `$this->layoutPath` in 3.
- A controller's layout names and paths come from its whole class chain, including calls in
  `AppController`, and `default` is always one of its names, since a `setLayout()` call in one
  action does not change what another action renders.
  Reuse the CakePHP 2 layout receiver lookup.

### 3. Incomplete `$helpers` declarations

`readLoadArray()` skips entries it cannot read, such as `protected $helpers = [self::X]` on a
helper. The helper then counts as having an initialize load of `HELPER`, so
`ViewHelperInViewHelperCompletionContributor` hides the convention list without knowing what the
helper loads. The CakePHP 2 feature adds a complete flag to property declarations; use it here
so an incomplete `$helpers` keeps the convention fallback. The same applies to a non-literal
`className` in an `addHelper()` or `loadComponent()` options array.

## Dependencies

Builds on `configuration-code-disambiguation-cake2`, for the rendering-controller mapping, the
element walk, layout indexing and the complete flag. Start once that branch is merged, or
branch from it.

## Plan

1. Check the merge and precedence behaviour in the real CakePHP 3, 4 and 5 source: controller
   helpers against `AppView::initialize()` loads, `setHelpers()` with and without merge, and
   where the deprecated Cake 3 `$helpers` property and `$this->layout` are read.
2. Index: `viewBuilder()` helper calls in controllers as HELPER loads on the controller, Cake 3's
   controller `$helpers` property, and `setLayout()`/`layout()` in the view file index.
3. Resolver and providers: CakePHP 3 to 5 templates, elements and layouts use `AppView` plus
   the rendering controllers as helper receivers. This changes the deferred signature from
   `#<key><fieldName>` to the template-carrying form the CakePHP 2 feature adds.
4. The complete flag for CakePHP 3 to 5 property arrays and options.
5. Tests, Cake 5 first: an aliased helper added in a controller action resolves in its
   template, in an element it includes and in a layout it sets; `default.ctp` unions it; an
   `AppView` helper still resolves everywhere; an incomplete `$helpers` on a helper keeps
   convention completion. Then Cake 4 and 3, including the deprecated property.
6. Run the full suite.

## Open questions

- Does a controller-side helper in one action belong to every template the controller renders,
  or only to that action's? The index stores the containing method, so a per-action mapping is
  possible, but templates rendered by `render()` from another action complicate it. Start with
  the whole controller.
