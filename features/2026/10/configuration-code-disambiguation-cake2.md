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
| ASSOCIATION | Model      | `public $belongsTo`, `$hasOne`, `$hasMany`, `$hasAndBelongsToMany` | value may be an array or a comma-separated string |

The entry shapes are the ones the existing array reader handles: a string value is a plain load
(`'Session'`, `'DebugKit.Toolbar'`) and a string key with an array value is a load with options
(`'Auth' => array('authorize' => 'Controller')`, `'Html' => array('className' => 'BootstrapHtml')`).
Both `array(...)` and `[...]` parse to the same AST node.

Some shapes are new:

- An association property set to a string is a comma-separated list of plain loads:
  `public $belongsTo = 'User, Category';` is two loads (`Model::_createLinks()` explodes on `,`
  and trims each name).
- A dotted association key without `className`, `'TestPlugin.Rating'`, is alias `Rating` with
  className `TestPlugin.Rating`, which is what the reader already records.
- `$uses = true` (the `Controller` default), `$uses = false`, `$uses = null` and `$uses = array()`
  each mean something different (see Models on controllers), so the index has to record a
  `$uses` declaration even when it lists no models.
- A `hasAndBelongsToMany` entry's `with` option names a join model that `Model::__isset()` also
  lazy-loads as a property (`'with' => 'MoviesTag'` gives `$this->MoviesTag`). It is a possible
  extra ASSOCIATION source; leave it out of the first pass unless it falls out of the reader.
- A string key with a string value, `'Auth' => 'x'`, is a load of `Auth` with junk settings at
  runtime (`(array)'x'`). The reader skips it today; that is fine.

`$this->loadModel('Name')` is already indexed as a TABLE `METHOD_CALL`. Its second argument
is an ID in CakePHP 2, and `sourcesWithoutOptions` already ignores it.

Not sources in this iteration: runtime loads through `$this->Components->load()`,
`$this->Helpers->load()`, `bindModel()` and `ClassRegistry::init()` (the last is already typed by
the existing `ClassRegistry` handling). `$actsAs` behaviours mix in methods, not properties, and
belong to the separate behaviour support idea.

## Semantics

### Verified against CakePHP 2.10

Step 1 checked the behaviour below against the CakePHP 2.10.24 source
(`lib/Cake/Controller/Controller.php`, `Core/CakeObject.php`, `Controller/ComponentCollection.php`,
`Utility/ObjectCollection.php`, `View/View.php`, `View/HelperCollection.php`, `View/Helper.php`,
`Controller/Component.php`, `Model/Model.php`).

**Defaults on the framework classes.** `Controller` declares `$uses = true`,
`$components = array('Session', 'Flash')`, `$helpers = array()` and `$modelClass = null` (set
to the singular of the controller name in the constructor). `View` and `Helper` declare
`$helpers = array()`. `Model` declares the four association properties as `array()`.

**Components and helpers on controllers merge with `AppController` only.**
`Controller::_mergeControllerVars()` merges `$components` and `$helpers` with the class
properties of `{Plugin}AppController` (for plugin controllers) and then of `AppController`,
using `Hash::merge($parent, $child)` after `Hash::normalize()`. It does not walk the chain:
an intermediate class between `AppController` and the controller contributes only through
ordinary PHP property inheritance, so a controller that redeclares `$components` loses its
intermediate parent's entries but keeps `AppController`'s. `AppController`'s own value is
itself plain PHP inheritance, so when `AppController` does not declare `$components`, the
`Controller` defaults `Session` and `Flash` are what gets merged.

`Hash::merge` is recursive, so for an alias declared in both, the options are merged with the
child's winning key by key. A child entry `'Auth' => array('authorize' => 'Controller')` keeps
an `AppController` entry's `'className' => 'MyAuth'`.

**Models on controllers.** After merging, `_mergeControllerVars()` and `constructClasses()` do:

- `$uses = null` becomes `false`.
- `$uses = true` becomes `array($modelClass)`.
- If `$uses` is still the same array as `AppController`'s (the controller does not redeclare
  it), `$modelClass` is prepended.
- Unless `$uses` is `false`, the `{Plugin}AppController` and `AppController` `$uses` arrays are
  appended (`array_merge` with the entries not already present). `true` on those parents is
  ignored.
- `$uses = false` leaves no models and an empty `$modelClass`.
- Then `$modelClass` becomes the first entry of `$uses`.

`Controller::__isset()` lazy-loads only the models in `$uses` (matched by the name after the
dot) and `$modelClass`. So a controller with `$uses = array('Artist')` has no `$this->Movie`,
even if it is `MoviesController`. `loadModel()` adds to `$uses` at runtime.

**Models do not merge associations.** `Model::__construct()` merges only `$actsAs` and
`$findMethods` with the parent and `AppModel`. The association properties follow plain PHP
inheritance: the nearest declaration in the chain is the whole value.

**Aliases.** `ComponentCollection::load()` and `HelperCollection::load()` both take a
`className` setting as the class and keep the array key as the alias;
`ObjectCollection::normalizeObjectArray()` keys every entry by the name after the dot. This is
what the reader already records.

### What this means for the resolver

The CakePHP 3 to 5 rule, "the most-derived load of an alias wins, ancestors' other aliases
still apply", is not right for CakePHP 2. CakePHP 2 resolves at the level of a whole property,
not of an alias:

1. **Effective value of a property in a class:** the nearest declaration of that property
   in the class chain, framework classes included (PHP inheritance). This needs the index to
   record which property a `PROPERTY_ARRAY` load came from, and to record a declaration even
   when the array is empty or the value is `true`/`false`.
2. **Controllers, `$components` and `$helpers`:** the controller's effective value, merged with
   `{Plugin}AppController`'s and `AppController`'s effective values, the child's entry winning
   for an alias. Option merging means a child entry without `className` inherits the parent's
   `className`; the resolver should apply that, since it is the case where an alias silently
   points at another class.
3. **Controllers, `$uses`:** the steps above, with `$modelClass` from the controller name.
4. **Models, associations:** each association property's effective value, unmerged.
5. **Helpers, `$helpers`:** the helper's effective value. Not merged with `AppHelper` (`Helper`
   does not call `_mergeVars()`).

So the chain walk should not stop at `\Controller`: its `$components` default matters. It can
still stop above the framework classes, and `\View` contributes nothing (see below).

Override and convention: property arrays are unconditional, so an alias the effective value
loads resolves to its loads only, as before. A component alias no property loads keeps
convention for type resolution. For completion, the component and model convention lists are
hidden whenever the controller's effective `$components` / `$uses` is known, because
`Controller::__isset()` does not lazy-load anything else.

`$this->loadModel('Name')` in an action adds a TABLE load outside the property, unioned as
before. A `loadModel()` call in `beforeFilter()` (common in CakePHP 2) is unioned, not an
override, matching the previous feature's rule.

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
- `View::__construct()` copies `$helpers` from the controller over the view's own (it is in
  `View::$_passedVars`), so `AppView`'s `$helpers` never applies when a controller renders the
  view. The view class is not a receiver.

This lookup runs inside `complete()` of `ViewHelperInViewTypeProvider`, which is called often.
It should be cached per template key with the same modification tracker the view variable
cache uses.

Completion in templates keeps the convention helper list, as for CakePHP 3 to 5: verified,
`View::__get()` asks `HelperCollection::__isset()`, which loads any helper by name and, if that
fails, tries the view's plugin (`{plugin}.{Name}`).

Inside a helper, `Helper::__get()` loads an entry of its `$helpers` through
`View::loadHelper()`, and `HelperCollection::load()` returns the instance already loaded under
that alias. The controller's helpers are loaded first, in the view constructor, so when a
controller aliases `Html` to `BootstrapHtml`, `$this->Html` inside any helper is the
`BootstrapHtml` instance, whatever the helper's own `$helpers` entry says. The helper's own
`className` only applies to aliases the controller does not load. Treat this as a known
limitation unless it is cheap to union the helper's resolution with the rendering
controllers': a helper has no single rendering controller, so this would mean every controller.

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
- The association properties accept a comma-separated string value.
- Record the property name for `PROPERTY_ARRAY` loads, and record a declaration of a property
  even when it loads nothing (`array()`, `true`, `false`, `null`), since the effective-value
  rule needs to know where a property is declared. This is a value format change: bump the
  index version.
- `$components` also exists on CakePHP 3 controllers. Indexing it is harmless there: CakePHP 3
  loads `$components` entries too, so reading them is correct, and the previous plan noted
  enabling it as a one-line change.

### Resolver

- `conventionCandidates()` adds the global names when `cake2Enabled`: `\{N}Component`,
  `\{N}Helper`, and `\{N}` for TABLE and ASSOCIATION. A dotted name uses the part after the dot.
- A CakePHP 2 resolution path implementing the property-level rules above. The chain walk reads
  `\Controller` for its `$components` default instead of stopping before it; `\View`, `\Helper`
  and `\Model` declare only empty arrays and can be stop classes.
- A child entry's missing `className` inherits the merged parent entry's.
- Telling CakePHP 2 classes from CakePHP 3+ ones: a chain that reaches `\Controller` (not
  `\Cake\Controller\Controller`) is CakePHP 2.

### Type providers, completion and navigation

- `ControllerFieldTypeProvider`: the configured-field path already covers CakePHP 2 controllers,
  since `$this` is a global FQN such as `\MovieController`, and `componentOrModelTypeFromFieldName()`
  already includes the CakePHP 2 names. Check that the TABLE kind resolves `$uses` entries.
- The nested path (`getCakeTwoClass()`) consults the receiver model's associations first.
- `ViewHelperInViewTypeProvider`, `ViewHelperInViewCompletionContributor` and
  `ViewHelperGotoDeclarationHandler`: use the rendering controllers, not `viewType(settings)`, as
  receivers for CakePHP 2 templates.
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
  `$helpers`, to test merging into the child, including a child entry without `className` that
  inherits the parent's.
- An intermediate controller between `AppController` and a child, both declaring `$components`,
  to test that the intermediate's entries are lost.
- The vendor stub's `Controller` declaring `$components = array('Session', 'Flash')` and
  `$uses = true`, to test the defaults.
- A model with each association property, including a comma-separated string `$belongsTo` and
  an aliased `$hasMany`, and a subclass that redeclares one association property, to test
  replacement.
- A `TestPlugin` under `app/Plugin/TestPlugin` with a component, a helper and a model, and a
  matching `PluginConfig` in the test setup.
- A template under the controller's view directory, and an element, for helper receivers.

Tests:

- Indexer: each property maps to its kind; the comma-separated association string; declarations
  of `$uses = true`, `false` and `array()`; `array()` and `[]` syntax.
- Components: an aliased entry resolves to its `className` only; a plain entry resolves by name;
  an alias merged from `AppController` resolves in the child; completion lists only the loaded
  components.
- Models: `$uses` entries resolve; completion with `$uses` hides other models; a controller
  without `$uses` gets its default model; `$uses = array('Artist')` on `MoviesController` has
  no `Movie`; `$uses = false` has no models; `AppController`'s `$uses` is appended.
- Helpers: an aliased helper on the controller resolves in its template; an element falls back
  to `AppController`; a helper's own `$helpers` still resolves.
- Associations: an aliased association resolves inside the model and through
  `$this->Movie->Alias` in a controller; a redeclared association property replaces the parent's.
- Navigation: `$this->Meta` goes to `MovieMetadataComponent`; strings in `$components` and
  `$uses` navigate through the service.

## Implementation steps

1. Done: checked the CakePHP 2.10 merging and lazy-loading behaviour; see "Verified against
   CakePHP 2.10".
2. Index: the new property sources, the comma-separated association string, the property name
   and empty declarations. Indexer tests.
3. Resolver: CakePHP 2 convention candidates and the property-level rules.
4. Components and models on controllers. Component and model tests.
5. Associations, in models and through nested controller fields. Association tests.
6. Template helpers through the rendering controllers, with caching. Helper tests.
7. Optional: plugin path filtering for navigation, if step 1 or the fixtures show it is worth it.
8. Run the full suite.

Commit after each step.

## Open questions

- Is plugin-path filtering for same-named classes worth doing, given that types cannot
  carry it? (Step 7.)
- Should the `hasAndBelongsToMany` `with` join model be a source in the first pass?
- Is it worth resolving a helper's `$helpers` aliases through the rendering controllers, given
  the controller's alias wins at runtime?

Resolved by step 1: the merge rules (property-level, `AppController` only, recursive option
merge; associations unmerged) and helper lazy-loading in views (yes, so template completion
keeps the convention list).
