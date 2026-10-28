# Configuration code disambiguation for CakePHP 2

## Context

`configuration-code-disambiguation` (PR #292) added `ConfiguredMemberIndex` and
`ConfiguredMemberService`, which resolve dynamic properties from the code that configures them
for CakePHP 3, 4 and 5. CakePHP 2 still resolves every dynamic property by convention only:

- `$this->Foo` on a controller is the union `\Foo|\FooComponent` from
  `componentOrModelTypeFromFieldName()`.
- `$this->Html` in a view or helper is `\HtmlHelper` from `viewHelperTypeFromFieldName()`.
- `$this->Movie->Director` resolves by name through `getCakeTwoClass()` in the nested path of
  `ControllerFieldTypeProvider`, provided both names are `AppModel` subclasses.

The previous plan already arranged for CakePHP 2 to fit the same index and resolver without a
redesign: CakePHP 2 configures through property arrays, which are `LoadSource.PROPERTY_ARRAY`
records, and the `$helpers` reader is the same array reader the CakePHP 2 arrays need.

This feature extends the index and resolver to CakePHP 2, so aliased loads such as
`'Html' => array('className' => 'BootstrapHtml')` (common with BoostCake and similar
plugins), `'Author' => array('className' => 'User')` associations and `$uses` lists drive type
inference, completion and navigation, as they do for CakePHP 3 to 5.

## Configuration sources

| Kind        | Class type | Source                                                       | Notes |
|-------------|------------|--------------------------------------------------------------|-------|
| COMPONENT   | Controller | `public $components = array(...)`                            | entries with options may set `className` |
| TABLE       | Controller | `public $uses = array(...)`, `$this->loadModel('Name')`      | no options; `$uses = false` loads nothing |
| HELPER      | Controller | `public $helpers = array(...)`                               | the controller's helpers become the view's helpers |
| HELPER      | Helper     | `public $helpers = array(...)`                               | already read by the existing `$helpers` reader |
| ASSOCIATION | Model      | `public $belongsTo`, `$hasOne`, `$hasMany`, `$hasAndBelongsToMany` | value may be an array or a single string |

The entry shapes are the ones the existing array reader handles: a string value is a plain load
(`'Session'`, `'DebugKit.Toolbar'`) and a string key with an array value is a load with options
(`'Auth' => array('authorize' => 'Controller')`, `'Html' => array('className' => 'BootstrapHtml')`).
Both `array(...)` and `[...]` parse to the same AST node.

Two shapes are new:

- An association property set to a single string, `public $belongsTo = 'User';`, is one plain load.
- `$uses = false` or `$uses = array()` records nothing for `$uses`, and the resolver needs to tell
  that apart from a class that does not declare `$uses` at all (see Semantics).

`$this->loadModel('Name')` is already indexed as a TABLE `METHOD_CALL`. Its second argument
is an ID in CakePHP 2, and `sourcesWithoutOptions` already ignores it.

Not sources in this iteration: runtime loads through `$this->Components->load()`,
`$this->Helpers->load()`, `bindModel()` and `ClassRegistry::init()` (the last is already typed by
the existing `ClassRegistry` handling). `$actsAs` behaviours mix in methods, not properties, and
belong to the separate behaviour support idea.

## Semantics

### Inheritance

CakePHP 2 merges some property arrays down the inheritance chain, and others replace their
parent's. **These rules are from memory and must be checked against the CakePHP 2.10 source
(`Controller::_mergeControllerVars()`, `Object::_mergeVars()`, `Model::__construct()`) before
implementing:**

- A controller's `$components` and `$helpers` are merged with `AppController`'s (and the plugin's
  `AppController`), with the child's entry winning for an alias that both declare.
- `$uses` is merged with `AppController`'s `$uses` unless either side is `false`.
- Model association properties are not merged: a model that declares `$belongsTo` replaces its
  parent's `$belongsTo` entirely.

The resolver's current rule already handles the merging case: for each alias, the most-derived
class with an override load wins, and aliases loaded only by ancestors still apply. What it does
not express is replacement, where a class that redeclares a property hides *all* of its
parent's entries for that property, not only the ones with the same alias. Supporting replacement
needs the index to know which property a `PROPERTY_ARRAY` load came from. Record it in
`MemberLoad` (a new nullable `propertyName`, or the property name stored in the `methodName`
slot), bump the index version, and in the resolver drop an ancestor's property-array loads from
a property that a more-derived class also declares, unless the merge rules say that property
merges.

### Override and convention

Property arrays are unconditional, so they keep the override rule from the previous feature:
an alias loaded by a property array resolves to its loads only, and convention is the fallback
for aliases nothing in the chain loads.

Default model: a controller without `$uses` lazy-loads its `$modelClass`, the singular of its
name (`MoviesController` loads `Movie`). That stays convention. With an explicit `$uses`, the
models it lists are what the controller has, and the TABLE convention list is hidden in
completion, as for `loadModel` loads in `initialize()` on CakePHP 3 and 4.

### Helpers in templates

In CakePHP 2, helpers are configured on the controller, and the controller hands its helper
list to the view. So the receiver chain for `$this->Html` in a template is the controller (or
controllers) that renders it, not `AppView`.

- Find the rendering controllers through the existing template-to-controller mapping:
  `ViewFileIndexService.referencingElementsInSmartReadAction()` on the template's key, then the
  containing `PhpClass` of each referencing element. This is the mapping
  `ViewToControllerGotoRelatedProvider` uses.
- Elements, layouts, and templates no controller references fall back to `AppController`.
- The receiver list is a union, so the per-receiver rules from the union fix apply: an alias
  only loses its convention candidates when every rendering controller has an override load
  of it.
- `AppView`'s own `$helpers` (rare) still applies, so the receiver list is the controllers plus
  `viewType(settings)`.

This lookup runs inside `complete()` of `ViewHelperInViewTypeProvider`, which is called often.
It should be cached per template key with the same modification tracker the view variable
cache uses.

Completion in templates keeps the convention helper list, as for CakePHP 3 to 5, if CakePHP 2's
`HelperCollection::__isset()` lazy-loads helpers by name the way CakePHP 3's registry does.
**Check this in the 2.10 source**; if CakePHP 2 does not lazy-load, templates follow the hide
rule instead.

The same template-to-controller mapping is what the previous plan deferred for CakePHP 3 to 5
controller-side helper configuration (`viewBuilder()->addHelper()`, Cake 3's
`$this->helpers`). Building it here means that work can reuse it.

### Plugins and same-named classes

CakePHP 2 classes are global. `'DebugKit.Toolbar'` loads `\ToolbarComponent` from the
`DebugKit` plugin, but the resolved FQN is just `\ToolbarComponent`. This has two consequences:

- **Type inference cannot tell same-named classes apart.** A `PhpType` is a set of FQN strings,
  and `\ToolbarComponent` resolves to every class with that name. For CakePHP 2, the feature's
  benefit is aliases (`className`), `$uses` and association aliases, not plugin disambiguation.
- **Navigation and completion type text can.** `ConfiguredMemberService.loadClasses()` and the
  goto handlers can filter `getClassesByFQN()` by file location: classes under the configured
  plugin path for a dotted name, and classes outside every plugin path for a plain name.
  Whether this is worth doing depends on how often apps have same-named classes in the app and
  a plugin. Decide during implementation; it is not required for the rest of the feature.

### Associations

CakePHP 2 models lazy-load their associated models in `Model::__isset()`, so:

- `$this->Author` inside a model resolves against the model's association properties. This is
  a new case, like the `v3` signature in `AssociatedTableTypeProvider` for CakePHP 3 tables.
- `$this->Movie->Director` in a controller resolves the inner `$this->Movie` (by `$uses` or
  convention), then looks up `Director` among that model's associations. Today this is
  `getCakeTwoClass()` by name in the nested path of `ControllerFieldTypeProvider`.

`AssociatedTableTypeProvider` is gated on `cake3Enabled` and identifies tables by the `Table`
suffix, so it cannot detect CakePHP 2 models as is. Either extend it with a CakePHP 2 branch
that recognises models through `isCakeTwoModelClass()` at `complete()` time, or put the
CakePHP 2 association lookup in the nested path of `ControllerFieldTypeProvider` (which already
handles CakePHP 2 nested models) plus a small new case for `$this->Author` inside a model.
The second keeps CakePHP 2 code together and avoids widening the table-specific provider;
prefer it unless it duplicates too much of the receiver-signature handling.

## Changes to existing code

### Index

- `propertyArraySources` gains `components` (COMPONENT), `uses` (TABLE) and the four CakePHP 2
  association properties (ASSOCIATION). The kind is per property, as for `helpers` today.
- The association properties accept a single string value as one plain load.
- Record the property name for `PROPERTY_ARRAY` loads if the replacement rule needs it. Bump the
  index version.
- `$components` also exists on CakePHP 3 controllers. Indexing it is harmless there: CakePHP 3
  loads `$components` entries too, so reading them is correct, and the previous plan noted
  enabling it as a one-line change.

### Resolver

- `conventionCandidates()` adds the global names when `cake2Enabled`: `\{N}Component`,
  `\{N}Helper`, and `\{N}` for TABLE and ASSOCIATION. A dotted name uses the part after the dot.
- `frameworkBaseClasses` gains `\Controller`, `\View`, `\Helper` and `\Model`. `AppController`,
  `AppHelper` and `AppModel` are application classes and stay in the chain.
- The replacement rule above, if confirmed.

### Type providers, completion and navigation

- `ControllerFieldTypeProvider`: the configured-field path already covers CakePHP 2 controllers,
  since `$this` is a global FQN such as `\MovieController`, and `componentOrModelTypeFromFieldName()`
  already includes the CakePHP 2 names. Check that the TABLE kind resolves `$uses` entries.
- The nested path (`getCakeTwoClass()`) consults the receiver model's associations first.
- `ViewHelperInViewTypeProvider`, `ViewHelperInViewCompletionContributor` and
  `ViewHelperGotoDeclarationHandler`: use the rendering controllers as receivers for CakePHP 2
  templates.
- The completion contributors and goto handlers need no change beyond the resolver, since they
  already go through `ConfiguredMemberService`.
- `ControllerComponentsGotoDeclarationHandler` already handles strings in `$components` and
  `$helpers`. Route those through the service too so aliased entries navigate to their
  `className`, and add `$uses`.

## Testing

CakePHP 2 tests live under `src/test/kotlin/.../test/cake2` with fixtures under
`src/test/fixtures/cake2/app`, and `Cake2BaseTestCase` enables CakePHP 2 only.

The existing `cake2/app/Controller/MovieController.php` already declares
`$uses = ['Movie', 'Artist']` and `$components = ['MovieMetadata']`, and `Movie.php` declares
`$hasMany` and `$belongsTo`. Once these are indexed, component and model completion in
`MovieController` hides the convention list. Check the existing `cake2/ControllerTest` and
`cake2/ViewTest` expectations against that first: they should keep passing (they test loaded
members), but any that rely on an unloaded member being offered need a separate fixture rather
than a changed `MovieController`.

New fixtures:

- `ConfiguredController.php` extending `AppController`, with `$components` holding a plain
  entry, an aliased entry (`'Meta' => array('className' => 'MovieMetadata')`), a plugin-dotted
  entry and an entry with options but no `className`; `$uses` with a plain and a plugin-dotted
  model; `$helpers` with an aliased helper.
- `AppController.php` variant (or a second base controller) declaring `$components` and
  `$helpers`, to test merging into the child.
- A model with each association property, including a single-string `$belongsTo` and an aliased
  `$hasMany`, and a subclass that redeclares one association property, to test replacement.
- A `TestPlugin` under `app/Plugin/TestPlugin` with a component, a helper and a model, and a
  matching `PluginConfig` in the test setup.
- A template under the controller's view directory, and an element, for helper receivers.

Tests:

- Indexer: each property maps to its kind; the single-string association value; `$uses = false`;
  `array()` and `[]` syntax.
- Components: an aliased entry resolves to its `className` only; a plain entry resolves by name;
  an alias merged from `AppController` resolves in the child; completion lists only the loaded
  components.
- Models: `$uses` entries resolve; completion with `$uses` hides other models; a controller
  without `$uses` keeps convention.
- Helpers: an aliased helper on the controller resolves in its template; an element falls back
  to `AppController`; a helper's own `$helpers` still resolves.
- Associations: an aliased association resolves inside the model and through
  `$this->Movie->Alias` in a controller; a redeclared association property replaces the parent's.
- Navigation: `$this->Meta` goes to `MovieMetadataComponent`; strings in `$components` and
  `$uses` navigate through the service.

## Implementation steps

1. Check the CakePHP 2.10 merging and lazy-loading behaviour listed above, and record what the
   source does in this document.
2. Index: the new property sources, the single-string association value, the property name if
   needed. Indexer tests.
3. Resolver: CakePHP 2 convention candidates, the framework base classes, the replacement rule.
4. Components and models on controllers. Component and model tests.
5. Associations, in models and through nested controller fields. Association tests.
6. Template helpers through the rendering controllers, with caching. Helper tests.
7. Optional: plugin path filtering for navigation, if step 1 or the fixtures show it is worth it.
8. Run the full suite.

Commit after each step.

## Open questions

- Do CakePHP 2's merge rules match the summary above? (Step 1.)
- Does CakePHP 2 lazy-load helpers by name in views? This decides template completion. (Step 1.)
- Is plugin-path filtering for same-named classes worth doing, given that types cannot
  carry it? (Step 7.)
