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
it (see [its log](rendering-controllers-element-plugin.md)). This feature
reuses that for CakePHP 3 to 5, and picks up the CakePHP 3 to 5 side of two gaps that review of
the CakePHP 2 plan found.

`plugin-aware-view-keys` (#307) since made the view file machinery plugin-aware on every version:
`pluginOf` and `resolveName` work for CakePHP 3 to 5, references are filtered by the framework's
fallback rule in `ViewFileResolution`, and `RenderingControllers` carries unprefixed hops to the
controller it reaches. What this feature has left to do is narrower than first planned; the
sections below say what #307 already covers.

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

Since #307, `RenderingControllers` already filters the layout union through the carried unprefixed
hops (`pendingResolve`), so a plugin's element reached through a layout gets only that plugin's
controllers, and the app's element of that name gets the rest. Setting the values above is all the
layout rule needs here. Until then, a CakePHP 3 to 5 layout has no references in the view file
index, so the walk finds no controllers and falls back to `AppView` alone, and
`ContributingSourceWalk` decides an element in such a layout by the element's own plugin.

### 3. Incomplete `$helpers` declarations

`LoadReader.readArray()` in `ConfiguredMemberDataIndexer` skips entries it cannot read, such as
`protected $helpers = [self::X]` on a helper. The helper then counts as having an initialize load of `HELPER`, so
`ViewHelperInViewHelperCompletionContributor` hides the convention list without knowing what the
helper loads. The CakePHP 2 feature records the two cases separately, both read in
`CakeTwoChainResolver`:

- `PropertyDeclaration.isComplete` is false when a property array has an entry the indexer could
  not read, such as `self::X`. Use it here so an incomplete `$helpers` keeps the convention
  fallback.
- `MemberLoad.hasUnreadClassName` is true when a load's `className` option is present but not a
  literal, as in `addHelper('Foo', ['className' => $x])` or the same on `loadComponent()`. That
  load is complete as a declaration but its class is unknown, so it should neither determine the
  type nor rule out the convention class.

### 4. Plugin templates

CakePHP 3 to 5 resolve plugin template, element and layout names as CakePHP 2 does (checked in
CakePHP 5's `View::pluginSplit()` and `_paths()`): a `Foo.` prefix or the view's own plugin
picks the plugin, and the search order is the app's `templates/plugin/Foo/`, then the plugin's
`templates/`, then the app's `templates/`. Template keys carry no plugin, so an app controller
and a plugin controller with the same name both match either template.

#307 did this part. `cakeThreePluginName` (in `cake/CakePlugins.kt`) takes a file's plugin from the
configured plugin paths, matched as path suffixes, from `plugins/{Name}/` by convention, and from
the app's override directory (`templates/plugin/{Name}/`, `src/Template/Plugin/{Name}/` on 3).
`viewFileOf` strips the override prefix so an override template has the plugin's key.
`ViewFileResolution.resolvesTo` applies the fallback rule, and `RenderingControllers` carries
unprefixed hops to the controller it reaches, so an element in an app template rendered by a plugin
controller by fallback is found as that plugin's element. Nothing is left to do here beyond
switching the walk on for these versions. Layouts need no plugin check, as a plugin's layout also
gets the app's controllers.

Known from #307, and accepted here: an app override and the plugin's original template have the
same `ViewFile`, so when both exist the original, which CakePHP never renders, still gets the
plugin's controllers and their helpers.

### 5. Cell templates

The first draft of this section planned to skip `CELL_RENDER` references in `RenderingControllers`,
on the grounds that a cell renders in its own view. Step 1 showed that is wrong for helpers. On
CakePHP 3 to 5, `CellTrait::_createCell()` passes the calling view's `$helpers` to the cell's
builder, and only `View` uses that trait, not `Controller`. So a cell template and its elements get
the helpers of the view calling `cell()`, which include that view's controller's helpers, and the
walk goes through the caller. `ContributingSourceWalk` still skips these references, as view
variables are not passed on.

A cell hop differs from an element hop in two ways. An unprefixed `cell()` name resolves in the app,
and what the cell's template includes resolves in the cell's plugin, not the controller's, so the
hop checks the name directly and drops the carried hops.

### Facade from the CakePHP 2 feature

`configuration-code-disambiguation-cake2` puts view helper receivers behind a version-neutral
facade, `ViewHelperReceivers`, with the per-version rules in `TemplateDirStructure` (chosen by
the `TemplatesDir` subtype) and the cached walk in `RenderingControllers`. #307 moved `ViewFile`,
`viewFileOf` and the signature `encode`/`decode` out of the facade into `ViewFile.kt`, beside
`ViewFileResolution` and `directReferencesTo`, since every entry point now uses them. For
CakePHP 3 to 5 the `CakeThreeAndLater` structure already resolves plugins and splits `Foo.`
prefixes, and the view file index records plugins for its references. What's left in the structure:

- `usesRenderingControllers = true`, with `AppView` still among the view-class receivers.
  `fallbackReceivers` stays empty: a template no controller renders keeps `AppView` alone.
- The layout values from section 2.

`TemplateDirStructure.ofSourceFile()` already gives every non-CakePHP 2 controller the CakePHP 3+
structure, and `cakeThreePluginName` finds plugin controllers under `plugins/{Name}/src/Controller`
by convention, so those count without further work. #307 notes one limitation to keep in mind:
with CakePHP 2 and 3+ both enabled, a CakePHP 2 plugin controller is taken as a CakePHP 3+ one.

## Dependencies

Builds on `configuration-code-disambiguation-cake2` (#299), for the rendering-controller mapping,
the element walk and the complete flag; on #305 for the layout rule; and on
`plugin-aware-view-keys` (#307) for CakePHP 3 to 5 plugin resolution and the carried-hop element
fix. All three are merged.

## Plan

1. Check the merge and precedence behaviour in the real CakePHP 3, 4 and 5 source: controller
   helpers against `AppView::initialize()` loads, `setHelpers()` with and without merge, and
   where the deprecated Cake 3 `$helpers` property is read. Also where a cell's view gets its helpers.
2. Index: `viewBuilder()` helper calls in controllers as HELPER loads on the controller, and Cake
   3's controller `$helpers` property.
3. Resolver and providers: CakePHP 3 to 5 templates and elements use `AppView` plus the rendering
   controllers as helper receivers, and layouts use `AppView` plus all controllers.
   `ViewHelperInViewTypeProvider` already carries the `ViewFile` in its signature on every version,
   so the providers only need the structure to say `usesRenderingControllers`.
   Walk through `CELL_RENDER` references from templates, as section 5 describes.
4. Honour `PropertyDeclaration.isComplete` for CakePHP 3 to 5 property arrays, and
   `MemberLoad.hasUnreadClassName` for `addHelper()` and `loadComponent()` options, in the
   CakePHP 3 to 5 resolution path. Today only `CakeTwoChainResolver` reads either flag;
   `ConfiguredMemberService.resolveChain` and `combine` mark every initialize load authoritative.

   The CakePHP 3 to 5 rules stay in their own resolver rather than joining
   `CakeTwoChainResolver`. That class encodes `Controller::_mergeControllerVars()`: nearest
   declaration per property, merging onto `AppController` and `{Plugin}AppController`, the
   `$uses` forms and default model, associations from five properties plus `bindModel()`, and
   views getting nothing. CakePHP 3 to 5 share none of it: an `initialize()` load replaces per
   alias with the most-derived class winning, and `AppView` is a plain receiver. A combined class
   would branch on the version in every method. So:

   - Move the inline block in `resolveChain` into `InitializeChainResolver`, named for the rule it
     implements, beside `CakeTwoChainResolver`. Both implement an internal `ChainResolver` with
     one method, `resolve(kinds, alias, resolveLoad): ChainResolution`, and `resolveChain` picks
     the implementation.
   - Each resolver decides for itself whether a load determines its member. `CakeTwoChainResolver`
     already does, from the merged declarations and its association rule, and is not changed. The
     new resolver treats a load with `hasUnreadClassName` as not determining, and a property-array
     load as determining only when its `PropertyDeclaration` `isComplete`, which means reading
     `ClassConfiguration.declarations`, which the inline block ignores.
   - `CakeTwoChainResolver` keeps its name, as it is CakePHP 2 specific, matching the
     `CakeTwo`/`CakeThreeAndLater` pairing in `TemplateDirStructure`.
5. Tests, Cake 5 first: an aliased helper added in a controller action resolves in its template
   and in an element only that template includes, without other controllers' aliases; any layout,
   and an element in a layout, gets the union of all controllers; a plugin bypassing `AppController`
   keeps its alias in layouts; a cell template and its elements get the helpers of the controller
   rendering the template that calls `cell()`, and not other controllers'; an `AppView` helper still resolves
   everywhere; an incomplete `$helpers` on a helper keeps convention completion; an
   `addHelper('Foo', ['className' => $x])` with a non-literal class resolves `Foo` to `FooHelper`
   with `hasInitializeLoad == false`, asserted on `ConfiguredMemberService.memberType` as the
   CakePHP 2 `ConfiguredMemberResolutionTest` does with `authoritative = false`. The type alone
   proves nothing: `resolveLoad` already falls back from a null `className` to the alias, so
   `FooHelper` comes out either way, and only the flag shows that the load no longer counts as
   determining the member. Then Cake 4 and 3, including the deprecated property.
6. Run the full suite.

## Open questions

- Does a controller-side helper in one action belong to every template the controller renders,
  or only to that action's? The index stores the containing method, so a per-action mapping is
  possible, but templates rendered by `render()` from another action complicate it. Start with
  the whole controller.

## Implementation Progress

### Session #1

**What step 1 found in the CakePHP 3.10, 4.6 and 5.4 source:**

- The builder's helpers reach `View::$helpers` through the constructor, before `initialize()`. So
  `AppView::addHelper()` overwrites the alias, and a `loadHelper()` there loads first, leaving
  `loadHelpers()` to get the same instance or raise for different config. `AppView` wins an alias
  both configure.
- `setHelpers()` without merge only replaces the builder's earlier helpers, not `AppView`'s, which
  are added later.
- Cake 3 reads the controller's `$helpers` in `createView()`, merged up the class chain by
  `_mergeControllerVars()`. Cake 4 only warns about the property, and Cake 5 ignores it.
- Cells inherit the calling view's helpers, which reversed section 5.

**Decisions:**

- `ViewHelperReceivers.helperMemberType` now takes the `ViewFile`. An initialize load on the view
  class decides the type alone. Otherwise the types are unioned, and the result only determines the
  type when every controller determines it. Without this, a controller not loading an `AppView`
  alias would bring back the convention types for it.
- A load in an action does not determine the type, since other actions don't have it. One in the
  controller's `initialize()` does.
- `layoutDirectory` stays the source for `isLayoutKey`. A new `indexesLayoutReferences`, true only on
  CakePHP 2, keeps layout indexing off here and leaves that to `layout-navigation-references`, which
  also has to solve Cake 3 controllers getting the `cakeFour` structure.
- `\Cake\Controller\Controller` subclasses are found at any depth without the framework indexed
  (`FrameworkNotIndexedControllerHelperTest`), so `appControllerClass` stays null here. Removing the
  `AppController` lookup from CakePHP 2 is left as the follow-up the plan names.

**Accepted limitations:**

- A builder held in a variable, as in `$builder = $this->viewBuilder(); $builder->addHelper('X')`, is
  not indexed.
- A cell class's own `viewBuilder()` helpers are not receivers of its templates.
- `$helpers` on a Cake 4 or 5 controller is still read, though the framework ignores it, since the
  index doesn't know the version.
- `setHelpers()` is treated as adding, so a replaced helper stays in the union.
- Completion's type text unions `AppView` and controllers for an alias both configure, where the type
  provider and go to declaration give `AppView` alone.
- A prefix's layout directory, such as `templates/Admin/layout/`, is not taken as a layout.

**Found along the way, not changed:**

- Since the view class receives the builder's `helpers` even when empty, the constructor replaces a
  `$helpers` property declared on `AppView`. The index still reads that property as a load.
- `elementPathPrefixFromSourceFile` needs the element directory to exist when a file is indexed, so a
  file indexed before that directory is created has no element references until it is next
  reindexed. This made a test order-dependent until its setup created the directory first.
