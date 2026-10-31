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
always configured on the controller: rendering controllers per template, and a walk back through
`$this->element()` includes for elements. Every layout gets all controllers, as #305 simplified
it (see `rendering-controllers-element-plugin.md`, on `main` once #305 merges). This feature
reuses that for CakePHP 3 to 5, and picks up the CakePHP 3 to 5 side of two gaps that review of
the CakePHP 2 plan found.

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
configures helpers. With controller-side helpers, a layout could have the helpers of any
controller, so it follows the CakePHP 2 rule: any layout, and anything it includes, gets all
controllers plus `AppView`. A wrong answer only means an extra type in a union or a missing alias,
so this doesn't track which controllers name a layout. It needs no `setLayout()` indexing, layout
paths, or layout names across a class chain.

`CakeThreeAndLater` needs its own values for this:

- `frameworkControllerClass` of `\Cake\Controller\Controller`.
- The layout directory for each version.
- `appControllerClass`, from the configured `appNamespace`, unless the lookup below makes it
  unnecessary. It's a fixed value today, with no access to settings.

**Check first:** CakePHP 2's `FrameworkNotIndexedHelperTest` shows `getAllSubclasses("\\Controller")`
works without the framework class being indexed. If `\Cake\Controller\Controller` behaves the same,
these versions can use that lookup alone and skip `appControllerClass`. In that case, also remove the
redundant `AppController` lookup from CakePHP 2 as a follow-up.

**Watch out:** `layoutDirectory` controls both layout indexing and `isLayoutKey`, so setting it here
also switches on the existing indexing. Split the two uses, or coordinate with
`layout-navigation-references`, which adds CakePHP 3 to 5 layout references for navigation.

### 3. Incomplete `$helpers` declarations

`readLoadArray()` skips entries it cannot read, such as `protected $helpers = [self::X]` on a
helper. The helper then counts as having an initialize load of `HELPER`, so
`ViewHelperInViewHelperCompletionContributor` hides the convention list without knowing what the
helper loads. The CakePHP 2 feature adds a complete flag to property declarations; use it here
so an incomplete `$helpers` keeps the convention fallback. The same applies to a non-literal
`className` in an `addHelper()` or `loadComponent()` options array.

### 4. Plugin templates

CakePHP 3 to 5 resolve plugin template, element and layout names as CakePHP 2 does (checked in
CakePHP 5's `View::pluginSplit()` and `_paths()`): a `Foo.` prefix or the view's own plugin
picks the plugin, and the search order is the app's `templates/plugin/Foo/`, then the plugin's
`templates/`, then the app's `templates/`. Template keys carry no plugin, so an app controller
and a plugin controller with the same name both match either template. Reuse the plugin-aware
lookup from the CakePHP 2 feature, with the plugin taken from the configured `PluginConfig` a
file is under, and a template under `templates/plugin/{Name}/` belonging to `{Name}`. Layouts need
no plugin check, as a plugin's layout also gets the app's controllers.

Unprefixed elements follow the plugin of the rendering controller, not the including file, which
`RenderingControllers` doesn't handle yet. Once `pluginOf` works on CakePHP 3 to 5, that gap applies
here too. The fix belongs to `plugin-aware-view-keys`, so land it first, or record the gap as
accepted.

### Facade from the CakePHP 2 feature

`configuration-code-disambiguation-cake2` puts view helper receivers behind a version-neutral
facade, `ViewHelperReceivers`, with the per-version rules in `TemplateDirStructure` (chosen by
the `TemplatesDir` subtype) and the cached walk in `RenderingControllers`. For CakePHP 3 to 5
its `CakeThreeAndLater` structure currently returns `AppView` only, resolves no plugins and
does not split `Foo.` prefixes. This feature mostly changes that structure:

- `usesRenderingControllers = true`, with `AppView` still among the view-class receivers.
- `pluginOf()` from the configured `PluginConfig` a file is under, and `resolveName()` splitting
  `Foo.` prefixes, as the CakePHP 2 structure does. The view file index then records plugins
  for CakePHP 3 to 5 references too (an index version bump).
- `TemplateDirStructure.ofSourceFile()` tells CakePHP 3+ controllers apart; plugin controllers
  under `plugins/{Name}/src/Controller` need to count.

## Dependencies

Builds on `configuration-code-disambiguation-cake2`, for the rendering-controller mapping, the
element walk and the complete flag, and on #305 for the layout rule. Start once #305 is merged,
ideally after the element fix in `plugin-aware-view-keys`.

## Plan

1. Check the merge and precedence behaviour in the real CakePHP 3, 4 and 5 source: controller
   helpers against `AppView::initialize()` loads, `setHelpers()` with and without merge, and
   where the deprecated Cake 3 `$helpers` property is read.
2. Index: `viewBuilder()` helper calls in controllers as HELPER loads on the controller, and Cake
   3's controller `$helpers` property.
3. Resolver and providers: CakePHP 3 to 5 templates and elements use `AppView` plus the rendering
   controllers as helper receivers, and layouts use `AppView` plus all controllers. This changes
   the deferred signature from `#<key><fieldName>` to the template-carrying form the CakePHP 2
   feature adds.
4. The complete flag for CakePHP 3 to 5 property arrays and options.
5. Tests, Cake 5 first: an aliased helper added in a controller action resolves in its template
   and in an element only that template includes, without other controllers' aliases; any layout,
   and an element in a layout, gets the union of all controllers; a plugin bypassing `AppController`
   keeps its alias in layouts; an `AppView` helper still resolves everywhere; an incomplete
   `$helpers` on a helper keeps convention completion. Then Cake 4 and 3, including the deprecated
   property.
6. Run the full suite.

## Open questions

- Does a controller-side helper in one action belong to every template the controller renders,
  or only to that action's? The index stores the containing method, so a per-action mapping is
  possible, but templates rendered by `render()` from another action complicate it. Start with
  the whole controller.
