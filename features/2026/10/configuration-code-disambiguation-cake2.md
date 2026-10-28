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
  lazy-loads as a property, so it is an ASSOCIATION source in this iteration, recorded from the
  `$hasAndBelongsToMany` property alongside the association itself:
  - `'with' => 'MoviesTag'` is a plain load of alias `MoviesTag`; a dotted
    `'with' => 'TestPlugin.MoviesTag'` is alias `MoviesTag` with that loaded name.
  - `'with' => array('MoviesTag' => array(...))` is a load of alias `MoviesTag`, the array key
    (`__isset()` matches `key($relation['with'])`).
  - Without `with`, CakePHP generates a join model name from the join table and, if no such
    class exists, instantiates a plain `AppModel` for it. That implicit join model is not
    recorded; it falls under the by-name association fallback below.
- A string key with a string value, `'Auth' => 'x'`, is a load of `Auth` with junk settings at
  runtime (`(array)'x'`). The reader skips it today; that is fine.

`$this->loadModel('Name')` is already indexed as a TABLE `METHOD_CALL`. Its second argument
is an ID in CakePHP 2, and `sourcesWithoutOptions` already ignores it.

`$this->bindModel(array('hasMany' => array('Review')))` inside a model class is an ASSOCIATION
`METHOD_CALL` source for that model. Its argument has the same nested shape as CakePHP 3's
`addAssociations()`, keyed by association type, so it goes through the same reader, with the
CakePHP 2 key set (`belongsTo`, `hasOne`, `hasMany`, `hasAndBelongsToMany`) in place of
CakePHP 3's (`belongsToMany` instead of `hasAndBelongsToMany`). `bindModel()` runs
conditionally and can be reset, so its loads are always unioned, never overriding, whatever
the enclosing method. Calls on any other receiver, such as `$this->Movie->bindModel(...)` from a
controller, are not indexed: the index stores syntax per containing class and cannot tell at
index time which model the receiver is. They are covered by the by-name type fallback for
associations (see Associations). `unbindModel()` is ignored.

Not sources in this iteration: runtime loads through `$this->Components->load()`,
`$this->Helpers->load()`, `bindModel()` on receivers other than `$this`, and
`ClassRegistry::init()` (the last is already typed by the existing `ClassRegistry` handling). `$actsAs` behaviours mix in methods, not properties, and
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
- Then, if `$uses` is non-empty, `$modelClass` becomes its first entry. If it is empty,
  `$modelClass` keeps the constructor's default, the singular of the controller name.

`Controller::__isset()` lazy-loads only the models in `$uses` (matched by the name after the
dot) and `$modelClass`. So a controller with `$uses = array('Artist')` has no `$this->Movie`,
even if it is `MoviesController`. `loadModel()` adds to `$uses` at runtime.

`$uses = array()` is not the same as `false`, and what it gives depends on `AppController`:

- `AppController` declares a non-empty `$uses`: the controller's `$uses` becomes
  `AppController`'s list, and `$modelClass` its first entry, so the controller's own default
  model is not loadable unless `AppController` lists it.
- `AppController` does not declare `$uses` (it inherits `true`, which `_mergeUses()` ignores):
  `$uses` stays empty, `$modelClass` keeps the default, and `$this->Movie` on
  `MoviesController` still lazy-loads. Nothing else does.

An empty child `$components = array()` behaves differently again: `_mergeVars()` merges a
non-empty parent value into an empty child array, so it keeps `AppController`'s components
(or the `Controller` defaults `Session` and `Flash`, when `AppController` does not declare
`$components`). It only drops what an intermediate class between `AppController` and the
child declared.

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
   record which property a `PROPERTY_ARRAY` load came from, to record a declaration even
   when the array is empty or the value is `true`/`false`, and to record whether it could read
   the whole value (see Known below).
2. **Controllers, `$components` and `$helpers`:** the controller's effective value, merged with
   `{Plugin}AppController`'s and `AppController`'s effective values, the child's entry winning
   for an alias. Option merging means a child entry without `className` inherits the parent's
   `className`; the resolver should apply that, since it is the case where an alias silently
   points at another class.
3. **Controllers, `$uses`:** the steps above, with `$modelClass` from the controller name.
4. **Models, associations:** each association property's effective value, unmerged, plus the
   model's own `bindModel()` loads, unioned. Unlike rules 2 and 3, a known association
   configuration limits only completion, not type resolution; see Associations.
5. **Helpers, `$helpers`:** the helper's effective value. Not merged with `AppHelper` (`Helper`
   does not call `_mergeVars()`).

So the chain walk should not stop at `\Controller`: its `$components` default matters. It can
still stop above the framework classes, and `\View` contributes nothing (see below).

Override and convention: property arrays are unconditional, so an alias the effective value
loads resolves to its loads only, as before.

Unlike CakePHP 3 to 5, a CakePHP 2 controller has no members beyond what its properties and
`loadModel()` calls load, so convention is not a fallback for aliases they do not load:

- **Models.** `Controller::__isset()` lazy-loads only the models in the effective `$uses`
  (after the merge rules) and `$modelClass`. When the effective `$uses` is known, a model that
  is in neither, and that no `loadModel()` call in the chain loads, does not exist: it gets no
  model type, convention must not restore it, and nested association completion on it offers
  nothing.
- **Components.** `ComponentCollection::init()` sets `$Controller->{$alias}` only for entries
  in the effective `$components`, and `Controller::__get()` does not lazy-load components.
  The runtime alternative, `$this->Components->load('Foo')`, does not set the property either;
  code writes `$this->Foo = $this->Components->load('Foo')`, and PhpStorm types that
  assignment itself. So when the effective `$components` is known, an alias it does not load
  gets no component type from convention.
- Completion follows the same rule: the component and model convention lists are hidden
  whenever the effective `$components` or `$uses` is known.

**Known** means some indexed class in the chain declares the property. Normally that is always
true, since `\Controller` declares `$components = array('Session', 'Flash')` and
`$uses = true`. When the CakePHP 2 framework source is not part of the project, `\Controller` is
not indexed, and if no application class declares the property either, its effective value is
unknown. Then convention stays the fallback for that property, for type resolution and
completion, as today.

A declaration also has to be **complete** for the property to be known: the index read the
whole value, so a parsed empty value is told apart from a value it could not read. A
declaration is complete when its value is a form the reader understands (an array literal;
`true`, `false` or `null` for `$uses`; a string for an association property) and every entry
in it was read. `public $components = array(self::AUTH, 'Session')`, `public $uses = self::MODELS`
or `array_merge(...)` are incomplete.

- An incomplete declaration is still the nearest declaration: it hides the parents'
  declarations of that property under PHP inheritance, and the loads it could read still
  apply. But its kind is unknown, so convention stays the fallback for that kind, for type
  resolution and completion.
- The same goes for a single entry: an entry whose `className` is not a literal string or a
  `::class` constant, `'Html' => array('className' => SOME_CONST)`, leaves that alias unknown.
  It resolves to its loaded name unioned with convention, not to its loaded name alone.
- For controllers, the merged effective value is unknown when any declaration that goes into
  the merge (the controller's, `{Plugin}AppController`'s or `AppController`'s) is incomplete.

`$this->loadModel('Name')` in an action adds a TABLE load outside the property, unioned as
before, and is a model the controller has even when `$uses` does not list it. A `loadModel()`
call in `beforeFilter()` (common in CakePHP 2) is unioned, not an override, matching the
previous feature's rule.

### Helpers in templates

In CakePHP 2, helpers are configured on the controller, and the controller hands its helper
list to the view. So the receiver chain for `$this->Html` in a template is the controller (or
controllers) that renders it, not `AppView`.

- Find the rendering controllers through the existing template-to-controller mapping:
  `ViewFileIndexService.referencingElementsInSmartReadAction()` on the template's key, then the
  containing `PhpClass` of each referencing element. This is the mapping
  `ViewToControllerGotoRelatedProvider` uses.
- Elements render in the including template's `View` instance, so they get that template's
  rendering controllers. The view file index already records `$this->element('x')` calls in
  templates and elements under the element's key (`ViewFileDataIndexer`), so walk back through
  the includes: a reference in a controller is a rendering controller, and a reference in a
  template or element (no containing `PhpClass`) means looking up that file's key in turn, with
  a visited set since elements can include each other. The element's receivers are the union
  of the controllers the walk reaches.
- Templates and elements the walk reaches no controller from fall back to `AppController`.
- Layouts get the controllers that render with them. Verified against 2.10: `Controller`
  declares `public $layout = 'default'` and `public $layoutPath = null`, both are in
  `View::$_passedVars`, and `View::_getLayoutFileName()` looks up
  `Layouts/{layoutPath}/{layout}` (no `layoutPath` segment when it is null). A controller sets
  either one by declaring the property, by assigning it (in an action or `beforeFilter()`),
  or, for the layout only, through `render()`'s second argument. None of these is indexed
  today, so:
  - A controller's **layout names** are the effective `$layout` property (the nearest
    declaration in its class chain, PHP inheritance, `default` from `\Controller`, or
    `default` when `\Controller` is not indexed), plus every literal value assigned to
    `$this->layout` or passed as `render()`'s second argument in its chain. Its **layout
    paths** are the effective `$layoutPath` property (null by default) plus every literal value
    assigned to `$this->layoutPath`. Assignments use `findFieldAssignments()`, like the
    existing `$this->view` assignments, which already collects every literal value the
    expression can take.
  - The controller can render any layout key `Layouts/{path}/{name}` from those two sets (a
    product, since assignments in different methods can combine), with no path segment for a
    null path. A name may contain slashes itself (`'admin/default'`) and may be plugin-dotted
    (`'TestPlugin.admin'`, which looks in the plugin's `Layouts/`).
  - A layout's receivers are the controllers whose keys include its key. So the lookup for a
    layout file goes from its key to candidate `(path, name)` splits, then to the controllers
    with that name and path. Index layout names by name (so `default` and an inherited
    declaration need a walk over subclasses) or resolve every controller's keys once and cache
    the reverse map; choose during implementation.
  - With the union rules, `$this->Html` in `default.ctp` resolves to
    `HtmlHelper|BootstrapHtmlHelper` when only some controllers that render it alias `Html`,
    which matches what can render it.
  - `canonicalizeFilenameToKey()` already drops a data view extension directory (`json`,
    `xml`), so the `layoutPath` that `RequestHandlerComponent` sets for those maps onto the plain
    layout key. Other paths stay part of the key.
  - A layout no controller can render falls back to `AppController`.
  - A template or element can also assign `$this->layout` or `$this->layoutPath`, since
    `View::render()` renders the template before it picks the layout (`$this->layout = 'ajax'`
    in a template is a common CakePHP 2 idiom). Index those assignments in view files too,
    with the same `findFieldAssignments()` call, and resolve a reference from a view file
    through the element walk: the layout's receivers include that template's rendering
    controllers. A layout path assigned in a view file combines with the layout names of the
    same file's controllers, as for an assignment in the controller.
- The receiver list is a union, so the per-receiver rules from the union fix apply: an alias
  only loses its convention candidates when every rendering controller has an override load
  of it.
- `View::__construct()` copies `$helpers` from the controller over the view's own (it is in
  `View::$_passedVars`), so `AppView`'s `$helpers` never applies when a controller renders the
  view. The view class is not a receiver.

**Getting the template to `complete()`.** Today `ViewHelperInViewTypeProvider.getType()` returns
`#<key><fieldName>`, so `complete()` does not know which template the field is in, and cannot
find its rendering controllers. `getType()` has the containing file, and can compute the
template key without index access: `templatesDirectoryOfViewFile()` (VFS only, already used by
the view variable indexer) gives the templates directory, and
`ViewFileIndexService.canonicalizeFilenameToKey()` turns the path into the key that
`referencingElementsInSmartReadAction()` takes. So for CakePHP 2 templates:

- `getType()` encodes the template key in the deferred signature, for example
  `#<key>2<templateKey><key><fieldName>`, with a marker distinguishing it from the existing
  CakePHP 3 to 5 form. Keys are template paths and contain no provider key character.
- `complete()` decodes the key, looks up the rendering controllers for it (through the element
  walk for elements, and each controller's layout keys for layouts), and resolves
  the field against them. Unreferenced files reach no controller, which gives the
  `AppController` fallback.
- CakePHP 3 to 5 templates keep the current signature and the `viewType(settings)` receiver.

`complete()` is called often, so cache the rendering controllers per template key, including
the result of the element walk and the layout receivers, with the same
modification tracker the view variable cache uses.

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

CakePHP 3 to 5 need no layout mapping for now. Their templates, elements and layouts all
resolve helpers against `AppView`, which is right for helpers loaded in
`AppView::initialize()`. Layouts would need the same treatment only once the controller-side
helper configuration above is supported, since a layout then has the helpers of whichever
controller renders it. The layout is chosen with `$this->viewBuilder()->setLayout()` in 4 and 5,
`viewBuilder()->layout()` or `$this->layout` in 3, and the view file index already reads
`viewBuilder()` calls for `setTemplate()` and `setTemplatePath()`, so `setLayout()` would be a
small addition there.

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

**The nested signature has to carry the receiver's resolved type.** Today
`ControllerFieldTypeProvider.nestedPropertyType()` encodes only the first and last field names,
`#<key><firstField><key><lastField>`, and `getCakeTwoClass()` checks both by name. That loses
the receiver: in `$this->Movie->Director->Studio`, `Director` may be an association alias
(`'Director' => array('className' => 'Person')`), so `Studio` must be looked up among
`Person`'s associations, not among those of a model named `Director`. And the first link must
honour the controller's `$uses` (with `$uses = array('Artist')`, `$this->Movie` does not exist).

So each link carries its own receiver's type, as the CakePHP 3 `v1` association signature
does after the union fix:

- `getType()` for `$receiver->Field`, where the receiver is itself a field reference, encodes
  the receiver's types and the field name. The receiver's types come from its own `getType()`
  result plus `ControllerFieldTypeProvider.configuredFieldSignatures()` for a controller field
  (the receiver's type at `getType()` time does not include that deferred signature).
- `complete()` resolves the receiver types with `completeType()`, keeps the ones that are
  CakePHP 2 models (`isCakeTwoModelClass()`), and resolves the field against each model's
  associations through the service, falling back to `getCakeTwoClass()`-style convention per
  receiver that does not configure it. Each receiver in a union is resolved separately.
- Chains of any length then resolve link by link: the receiver of `Studio` is the signature of
  `$this->Movie->Director`, which resolves through its own receiver. Unlike the CakePHP 3 `v1`
  path, which drops its own provider's signatures from the receiver to avoid recursion, this
  path has to keep them, or every link after the first falls back to convention. That is safe
  because each embedded signature is built from a strictly shorter expression, so resolution
  terminates; add a depth limit as a guard anyway.
- `ControllerModelOrTableCompletionContributor.nestedLookup()` identifies receivers by
  `isAnyTableClass()` and falls back to a name check. Extend it to accept CakePHP 2 model
  receivers, so nested completion lists the receiver model's configured associations.

**Associations are the one CakePHP 2 kind that keeps a by-name type fallback when their
configuration is known.** At runtime an association the model's properties do not list does
not exist (`Model::__get()` only returns what `__isset()` loads), so strictly an existing but
unlisted model should not resolve. But the usual way to add an association at runtime is
`bindModel()` called on the model from outside, typically from a controller:

```php
$this->Movie->bindModel(array('hasMany' => array('Review')));
$this->Movie->Review->find('all');
```

That call cannot be indexed against `Movie` (see Configuration sources), so a strict rule would
drop the type, completion and navigation of working code that resolves by name today. For
`$uses` and `$components` the runtime paths are visible (`loadModel()` is indexed, and
`$this->Foo = $this->Components->load('Foo')` is an assignment PhpStorm types itself), which is
why those can be strict and associations cannot. So:

- **Type resolution:** an association alias the model's effective configuration loads resolves
  to its loads only. Any other uppercase field on a model keeps the by-name fallback
  (`getCakeTwoClass()`-style: the name is a CakePHP 2 model), whether or not the configuration
  is known. This matches CakePHP 3 associations after #292, where an unconfigured alias
  resolves by convention.
- **Completion:** when the model's effective association properties are known, nested
  completion and completion inside the model offer only the configured associations, the
  `with` join models and the model's own `bindModel()` loads. Externally bound associations are
  not offered; they still resolve when typed. When the configuration is unknown (no indexed
  class in the chain declares the association properties, for example `\Model` is not in the
  project), completion keeps the convention list of all models.

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
- `ConfiguredMemberDataIndexer.textNeedles` gains `components` and `uses`. The prefilter skips a
  file before parsing unless one of the needles occurs, and a controller whose only sources are
  `$components` or `$uses` contains none of the current needles unless `load` happens to occur.
  `hasAndBelongsToMany` is already covered, since the check ignores case and the name contains
  `belongsTo`. `uses` matches nearly every CakePHP 2 file because of `App::uses()`, which is
  acceptable for a prefilter.
- The association properties accept a comma-separated string value.
- `$hasAndBelongsToMany` entries with a `with` option also record the join model load.
- `$this->bindModel(...)` is a new `METHOD_CALL` source, read with the `addAssociations()` array
  reader and the CakePHP 2 association key set. Its loads need a marker that keeps them unioned
  regardless of the enclosing method; the source method name can serve, if the record keeps it.
- Record the property name for `PROPERTY_ARRAY` loads, and record a declaration of a property
  even when it loads nothing (`array()`, `true`, `false`, `null`), since the effective-value
  rule needs to know where a property is declared. Record with each declaration whether it is
  complete: the value was a form the reader understands and no entry was skipped.
  `readLoadArray()` skips unreadable entries silently today, and `readPropertyArrays()` ignores
  a value that is not an array literal, so both need to report what they skipped. An entry
  whose `className` could not be read also needs a marker, since `classNameOption()` returns
  null for it, the same as for an entry with no `className`. This is a value format change:
  bump the index version.
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
- An incomplete declaration makes its kind unknown, and an unreadable `className` makes its
  alias unknown, as described under Known.
- Telling CakePHP 2 classes from CakePHP 3+ ones: a chain that reaches `\Controller` (not
  `\Cake\Controller\Controller`) is CakePHP 2.

### Type providers, completion and navigation

- `ControllerFieldTypeProvider`: the configured-field path already covers CakePHP 2 controllers,
  since `$this` is a global FQN such as `\MovieController`, and `componentOrModelTypeFromFieldName()`
  already includes the CakePHP 2 names. Check that the TABLE kind resolves `$uses` entries.
  Its `complete()` adds the `componentOrModelTypeFromFieldName()` union unless an initialize
  load was found; for CakePHP 2 it must instead add the component or model part of that union
  only when the effective `$components` or `$uses` is unknown, so the service has to report
  per kind whether the property is known.
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
- Empty redeclarations against populated parents, for the redeclaration tests below.
- A `TestPlugin` under `app/Plugin/TestPlugin` with a component, a helper and a model, and a
  matching `PluginConfig` in the test setup.
- A template under the controller's view directory, and an element, for helper receivers.

Tests:

- Indexer: each property maps to its kind; the comma-separated association string; declarations
  of `$uses = true`, `false` and `array()`; `array()` and `[]` syntax; `with` in its string,
  dotted and array forms; `$this->bindModel()` leaves under each CakePHP 2 key, an unknown key
  ignored, and `bindModel()` on a receiver other than `$this` not recorded; a declaration with
  a constant entry, a non-literal value, or a non-literal `className` is recorded as
  incomplete, and `array()` as complete and empty.
- Components: an aliased entry resolves to its `className` only; a plain entry resolves by name;
  an alias merged from `AppController` resolves in the child; completion lists only the loaded
  components; an alias the effective `$components` does not load gets no component type, even
  when a component class of that name exists.
- Models: `$uses` entries resolve; completion with `$uses` hides other models; a controller
  without `$uses` gets its default model; `$uses = array('Artist')` on `MoviesController` has
  no `Movie` (no model type, even though the `Movie` model exists); `$uses = false` has no
  models; `AppController`'s `$uses` is appended; a `loadModel('Movie')` in an action makes
  `$this->Movie` resolve despite `$uses = array('Artist')`.
- Unknown properties: with no `\Controller` stub in the fixture and no `$components` or `$uses`
  declared by any application class, component and model aliases fall back to convention for
  type resolution and completion.
- Incomplete declarations: `$components = array(self::AUTH, 'Session')` resolves `Session` and
  keeps convention for other component aliases; `$uses = self::MODELS` keeps convention for
  models; `'Html' => array('className' => SOME_CONST)` resolves `$this->Html` to `HtmlHelper`
  unioned with convention. Each still hides the parent's declaration of the same property.
- Helpers: an aliased helper on the controller resolves in its template; an element included
  from that template picks up the alias, including through a nested element; an element that
  nothing includes falls back to `AppController`; a helper's own `$helpers` still resolves.
- Layouts: a layout a child controller assigns with `$this->layout`, one it passes as
  `render()`'s second argument, and one it declares with `public $layout = 'admin'` each pick
  up that controller's alias; a `$layout` declared on `AppController` applies to the
  controllers that do not redeclare it; `default.ctp` unions the alias with the convention
  class when only one controller aliases it, and not when that controller declares another
  `$layout` and never assigns `default`; a controller with `public $layoutPath = 'admin'`, and
  one assigning `$this->layoutPath`, renders `Layouts/admin/default.ctp` with its alias; a
  `json` layout path maps onto the plain layout; `$this->layout = 'print'` in
  `Movie/view.ctp` makes `print.ctp` pick up `MovieController`'s alias, and the same through an
  element; a layout no controller can render falls back to `AppController`. Indexer tests for
  each layout and layout path source.
- Associations: an aliased association resolves inside the model and through
  `$this->Movie->Alias` in a controller; a redeclared association property replaces the parent's.
- Unlisted association, completion: a `Movie` model whose association properties do not list
  `Studio`, with an existing `Studio` model class. Completion on `$this->Movie->` in a
  controller, and on `$this->` inside `Movie`, does not offer `Studio`, but does offer the
  configured associations.
- External `bindModel()`, type: a controller action calls
  `$this->Movie->bindModel(array('hasMany' => array('Review')))` and then uses
  `$this->Movie->Review`. `$this->Movie->Review` resolves to the `Review` model (methods
  complete, goto works) through the by-name fallback, even though `Movie`'s association
  configuration is known and does not list `Review`; `Review` is not offered in completion on
  `$this->Movie->`.
- In-model `bindModel()`: a `bindModel()` call inside `Movie` adds its alias, resolved through its
  `className` if given, and offers it in completion, without hiding the property-configured
  associations.
- `with` join models: `$this->MoviesTag` inside `Movie`, and `$this->Movie->MoviesTag`, resolve to
  the join model for the string form and the array form of `with`, and are offered in
  completion.
- Nested chains: `$this->Movie->Director->Studio` where `Director` is an aliased association
  resolves `Studio` against the aliased class's associations. On a controller with
  `$uses = array('Artist')`, `$this->Movie` has no model type and `$this->Movie->` offers no
  nested association completion.
- Navigation: `$this->Meta` goes to `MovieMetadataComponent`; strings in `$components` and
  `$uses` navigate through the service.
- Empty redeclarations. These check both that the indexer records a declaration that loads
  nothing and that the resolver applies the merge rules exactly; each has a distinct expected
  result, for type resolution and completion:
  1. **`$components = array()` in a child of an intermediate controller** that declares
     `$components`, with `AppController` declaring its own: the intermediate's components are
     gone, `AppController`'s remain. With an `AppController` that does not declare
     `$components`, the child has `Session` and `Flash`.
  2. **An empty association array in a child model**, `$belongsTo = array()` under a parent
     with a populated `$belongsTo`: the child has none of the parent's `belongsTo`
     associations (its other association properties still inherit).
  3. **`$uses = false`** with a populated `AppController` `$uses`: no models at all, not even
     the default model; model completion offers nothing by convention.
  4. **`$uses = array()`**: with a populated `AppController` `$uses`, the controller has
     `AppController`'s models and not its own default model; with an `AppController` that does
     not declare `$uses`, it has only its default model.

## Implementation steps

1. Done: checked the CakePHP 2.10 merging and lazy-loading behaviour; see "Verified against
   CakePHP 2.10".
2. Index: the new property sources and text needles, the comma-separated association string,
   `with` join models, in-model `bindModel()`, the property name, empty declarations and the
   complete flag. Indexer tests.
3. Resolver: CakePHP 2 convention candidates and the property-level rules.
4. Components and models on controllers. Component and model tests.
5. Associations: the receiver-carrying nested signature, `$this->Author` inside a model, nested
   completion for CakePHP 2 models, the by-name type fallback and strict completion. Association
   tests.
6. Template helpers: the template key in the deferred signature, the rendering controllers,
   the element walk and caching. Helper tests.
7. Layouts: index `$layout` and `$layoutPath` declarations and assignments (in controllers and
   view files) and `render()`'s second argument, layout receivers including `default`, layout
   paths and the element walk for view file assignments. Layout tests.
8. Optional: plugin path filtering for navigation, if step 1 or the fixtures show it is worth it.
9. Run the full suite.

Commit after each step.

## Open questions

- Is plugin-path filtering for same-named classes worth doing, given that types cannot
  carry it? (Step 8.)
- Is it worth resolving a helper's `$helpers` aliases through the rendering controllers, given
  the controller's alias wins at runtime?
- CakePHP 3 to 5 have the same gap as incomplete declarations: `readLoadArray()` skips an entry
  such as `protected $helpers = [self::X]`, and a helper with any other initialize load then
  hides the convention list (`ViewHelperInViewHelperCompletionContributor`). The complete flag
  added here could fix that too; out of scope for this feature.

Resolved by step 1: the merge rules (property-level, `AppController` only, recursive option
merge; associations unmerged) and helper lazy-loading in views (yes, so template completion
keeps the convention list).

Resolved in review: `with` join models are a source in the first pass; associations keep the
by-name type fallback because external `bindModel()` calls cannot be indexed, while completion
is limited to configured associations, `with` join models and in-model `bindModel()` loads.
