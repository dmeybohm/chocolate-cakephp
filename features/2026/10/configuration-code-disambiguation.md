# Configuration code disambiguation

## Context

Dynamic property resolution is purely convention-based today, for every kind of dynamic member:

- **Components and tables on controllers.** For `$this->Foo` on any class whose name ends in
  `Controller`, `ControllerFieldTypeProvider.getType()` returns the union produced by
  `componentOrModelTypeFromFieldName()` in `Classes.kt`: every plausible `FooComponent` and
  `FooTable` FQN across the core, app, and configured plugin namespaces.
  `ControllerComponentCompletionContributor` and `ControllerModelOrTableCompletionContributor`
  offer every component and table subclass in the project.
- **Helpers in templates and helpers.** `ViewHelperInViewTypeProvider` returns the union from
  `viewHelperTypeFromFieldName()` for any uppercase property on `$this` in a view file, and
  `ViewHelperInViewHelperTypeProvider` does the same inside helper classes. Completion lists all
  helper subclasses.
- **Associations on tables.** `AssociatedTableTypeProvider` resolves `$this->Movies->Articles` by
  the name `Articles` alone.

Nothing reads the code that actually configures these classes. `initialize()` appears only in
the skip list of rendering methods. The visible consequences:

1. Aliased loads cannot resolve at all. `loadComponent('Meta', ['className' =>
   MovieMetadataComponent::class])`, `belongsTo('Authors', ['className' => 'Users'])`, and
   `loadHelper('Fmt', ['className' => 'MovieFormatter'])` all leave the alias untyped.
2. Same-named classes in the app and a plugin both appear in the union, so completion and
   navigation are ambiguous even when the class loads only one of them with a plugin prefix.
3. Completion lists members the class never loads.

This feature indexes the configuration calls and uses them to resolve dynamic members. The
guiding rule, decided up front: **a load in `initialize()` overrides the by-convention resolution
for that alias. A load anywhere else is unioned, not overriding.** `initialize()` runs
unconditionally on every request, so what it loads is what the class has. Loads in
`beforeFilter()`, in actions, or inside conditionals may be dynamic re-loads or may not run at
all, so they widen the result instead of replacing it. Convention remains the fallback for
aliases that no indexed class in the inheritance chain loads.

Scope for this iteration is CakePHP 3, 4 and 5 (the `cake3Enabled` code path) and all four
member kinds: components, tables loaded on controllers, helpers, and associations. The design
keeps CakePHP 2 in view so it slots into the same index and resolver later without a redesign.

## Configuration sources

Every source but one has the same shape: a `$this->method('Alias', [options])` call in a class
body, where the alias becomes a dynamic property on instances of that class. The exception is
`addAssociations()`, which takes a nested array of such loads and is described below.

| Kind        | Class type | Method(s)                                            | Versions  | Property namespace                  |
|-------------|------------|------------------------------------------------------|-----------|-------------------------------------|
| COMPONENT   | Controller | `loadComponent`                                      | 3, 4, 5   | `Controller\Component\{N}Component` |
| TABLE       | Controller | `loadModel`                                          | 3, 4      | `Model\Table\{N}Table`              |
| HELPER      | View       | `loadHelper`, `addHelper`                            | 3, 4, 5 / 4.1+, 5 | `View\Helper\{N}Helper`     |
| ASSOCIATION | Table      | `belongsTo`, `hasOne`, `hasMany`, `belongsToMany`, `addAssociations` | 3, 4, 5 | `Model\Table\{N}Table`  |

The method name determines the kind. The indexer does not check the class type; a
`loadComponent()` call in a non-controller is recorded and simply never looked up.

`Table::addAssociations()` is also an ASSOCIATION source. It takes one nested array keyed by
association type, for example `['belongsTo' => ['Authors' => ['className' => 'Users'], 'Movies'],
'hasMany' => ['Comments']]`. Each leaf is recorded as its own `METHOD_CALL` load: a string value is
a plain load, a string key with an array value is a load with options. Only the four recognized
top-level keys (`belongsTo`, `hasOne`, `hasMany`, `belongsToMany`) are read; the framework
dispatches each key as a method name, so any other key is invalid and is ignored. The
enclosing-method rule applies as for any other call. The array walking is shared with the
`$helpers` reader.

`addBehavior()` is not a source. Behaviours mix in methods, not properties, and belong to the
separate behaviour support idea. Cake 5's `$defaultTable` and the Cake 3/4 `$modelClass` default
table are convention, not configuration, and are unchanged. `fetchTable()` returns a value rather
than setting a property and is already handled by `TableLocatorTypeProvider`.

Declarative `$helpers` arrays on View and Helper classes are the other helper source in 3, 4 and
5. They are unconditional like `initialize()`, so they follow the override rule. They share their
shape with CakePHP 2's `$components`, `$helpers` and `$uses` arrays, so implementing the
`PROPERTY_ARRAY` source for helpers now builds the CakePHP 2 path. See the implementation steps
for where it lands.

Not in this iteration: controller-side helper configuration through `viewBuilder()->addHelper()`
and `setHelpers()`, and Cake 3's `$this->helpers` on controllers. Those need the template-to-
controller mapping from `ViewFileIndex` to find the receiver, which is a separate piece of work.

## Semantics

### Receiver and chain

Resolution starts from the FQN(s) of the receiver and walks the superclass chain through
`PhpIndex`, stopping at the framework base class (`\Cake\Controller\Controller`,
`\Cake\View\View`, `\Cake\View\Helper`, `\Cake\ORM\Table`) or when a class is unknown, with a
cycle guard.

- **Controllers.** `$this` in a controller already carries the controller FQN. Receivers typed by
  docblock behave the same way.
- **Templates.** Templates have no class. `$this` in a template is typed by the plugin as
  `viewType(settings)`, which is the app's `AppView`, so helper lookups in templates resolve
  against the `AppView` chain. A `$this->loadHelper()` call inside a template itself is not a
  source in this iteration.
- **Helper classes.** `$this->Html` inside a helper resolves against the helper's own chain,
  reading its `$helpers` array (and any `initialize()` loads, which are rare for helpers).
- **Tables.** `$this->Authors` inside a table resolves against the table's chain. For
  `$this->Movies->Articles` in a controller, the receiver type is whatever the inner reference
  resolved to (`MoviesTable` by convention or by a `loadModel` load), and the association lookup
  uses that chain.

### Resolution of alias `Foo` on receiver chain `C`

1. For each class in the chain, read its indexed loads of `Foo` of every kind that is meaningful
   for the receiver (COMPONENT and TABLE for controllers, HELPER for views and helpers,
   ASSOCIATION for tables). Partition them into *initialize loads* (enclosing method is
   `initialize`, or the source is a property array) and *other loads*.
2. If `Foo` has an initialize load somewhere in the chain, find the most-derived class whose
   `initialize()` (or property array) loads `Foo`. The base type is the union of all of that
   class's initialize loads of `Foo`. Initialize loads of `Foo` in ancestors of that class are
   excluded, matching registry behaviour where a later load replaces the earlier instance. The
   convention union is not added, for any kind. Any other loads of `Foo` anywhere in the chain
   are unioned onto the base type, so a dynamic re-load in an action widens the type.
3. If `Foo` has only other loads, the type is the union of their resolutions and today's
   convention union. For a plain `loadComponent('Foo')` this collapses to the convention result;
   the union only changes the outcome for aliased or plugin-prefixed loads outside `initialize()`.
4. If no class in the chain loads `Foo`, fall back to today's convention union. This keeps the
   plugin working when the load happens in a vendor base class that is not indexed or through a
   registry call such as `$this->components()->load()`.

The override is per alias. Only the alias that `initialize()` loads loses its convention
candidates; every other property on the class resolves exactly as today. On a controller,
dropping both the component and the table candidate for a loaded alias is correct, not a loss:
the framework sets the loaded member on the instance under that alias, so a same-named member of
the other kind is shadowed at runtime too.

Within one method, the same alias loaded more than once (for example in both branches of an
`if`) produces one record per call and the resolutions are unioned. Source order inside a method
is ignored. Across the chain, the assumption is that every `initialize()` calls
`parent::initialize()`, which the framework requires for `AppController` and `AppView` loads to
work at all. A child that omits the parent call still inherits the parent's indexed loads here;
tracking the parent call is possible later since the indexer sees the method body, but it is not
worth the complexity for a case that is a bug in the application.

### Resolving a single load

A load is recorded as `(kind, source, alias, loadedName, className?, methodName, offset)`:

- `loadComponent('Flash')`: alias `Flash`, loadedName `Flash`, no className.
- `belongsTo('Authors')`: alias `Authors`, loadedName `Authors`.
- `loadHelper('TestPlugin.Fmt')`: alias `Fmt`, loadedName `TestPlugin.Fmt`.
- `belongsTo('Authors', ['className' => 'Users'])`: alias `Authors`, className `Users`.
- `loadComponent('Meta', ['className' => MovieMetadataComponent::class])`: className is the
  resolved FQN from `ClassConstantReference.getStaticClassRefOrNull()`, which uses the file's
  `use` imports and needs no index access.
- `loadComponent($name)` or any non-literal first argument: not recorded.

A string literal `className` is stored as its raw contents. `getStringifiedClassOrNull()` must
not be used for it, because that helper prepends a backslash to any string and would turn the
plain name `'MovieMetadata'` into an FQN.

At lookup time, with settings and `PhpIndex` available:

- A `className` containing a backslash is an FQN and is used directly.
- Otherwise the name to resolve is `className` if present, else `loadedName`.
- A dotted name `Plugin.Name` resolves only to the kind's namespace under the configured plugin
  called `Plugin`, for example `${plugin.namespace}\Model\Table\NameTable`. If no configured
  plugin matches, treat the whole string as a plain name, mirroring the `parseAndLookupPlugin()`
  fallback already used for templates.
- A plain name resolves to the kind's convention candidates for that name (core, app, plugins),
  reusing the existing `*FromFieldName()` helpers in `Classes.kt`. Because `PhpIndex` is available
  here, filter the candidates to classes that exist. If none exist, keep the full candidate set so
  completion still shows something useful.

The index stores syntax only: raw strings and FQNs from the file. Namespaces, plugin
configuration, and existence checks are applied at lookup time. This matches the precedent in
`resolve-element-paths-at-lookup.md` and means a settings change never requires a reindex.

### Completion

Loaded aliases from both initialize loads and other loads are always offered, each presented
with its resolved class as the type text. The convention-based list of all subclasses of the kind
is shown only when the chain has no initialize loads of that kind at all. When the chain has no
indexed loads of any kind, behaviour is unchanged.

This applies per contributor. On a controller, the component contributor hides its convention
list when the chain has initialize component loads, and the table contributor hides its
convention list when the chain has initialize `loadModel` loads. A chain with component loads but
no `loadModel` calls, which is the common case since the default table is convention, keeps the
full table list.

This is a deliberate trade-off against the per-alias fallback rule. A member loaded only in an
unindexed vendor base class still resolves when typed by hand, but is not offered in completion
once the chain has an initialize load of its kind. Showing the full convention list at lower
priority would restore discoverability for that rare case at the cost of the noise this feature
exists to remove. A known limitation: when a chain has component loads but no `loadModel` calls,
the table contributor may still offer a table name that a loaded component shadows.

Aliases can differ from class names, so the loaded-alias path builds `LookupElementBuilder`s
directly instead of going through `completeFromClasses(removeFromEnd = ...)`.

### Navigation

- `$this->Foo` on controllers, views, helpers and tables goes through the same resolver, so an
  aliased or plugin-prefixed load navigates to exactly one class. `ControllerFieldGotoDeclarationHandler`
  already accepts any field reference, not only those on controllers, so it is the existing
  navigation path for association properties as well.
- The string literal in the first argument of a source method, and the `className` option value,
  navigate to the resolved class. `ControllerComponentsGotoDeclarationHandler` currently only
  handles the `$components` and `$helpers` property arrays; extend it to the method-call sources.
- Leaves inside an `addAssociations()` array, whether alias keys, plain string values, or nested
  `className` values, do not get navigation in this iteration. They are indexed and resolve as
  properties, but the goto handler would need to classify a literal by its position in a nested
  array, and that is better done once with the dotted-path work in the query-method follow-up.
- Completion of names inside the first argument of a source method is a natural addition and is
  cheap once the convention candidate list is reachable. Include it if time permits.

## Index design

### `ConfiguredMemberIndex`

A new `FileBasedIndexExtension<String, List<MemberLoad>>` registered in `plugin.xml` beside the
two existing indexes.

- **Key:** the FQN of the class containing the load, with a leading backslash, exactly as
  `PhpClass.fqn` reports it. Lookups always start from a class FQN, so no reverse key is needed.
- **Value:** a list of `MemberLoad` records:

  ```
  MemberLoad(
      kind: MemberKind,        // COMPONENT, TABLE, HELPER, ASSOCIATION
      source: LoadSource,      // METHOD_CALL or PROPERTY_ARRAY
      alias: String,
      loadedName: String,      // may be plugin-dotted
      className: String?,      // from 'className' option; FQN or plain
      methodName: String?,     // enclosing method; "initialize" drives the override rule;
                               // null for PROPERTY_ARRAY
      offset: Int,             // for navigation and future diagnostics
  )
  ```

- **Input filter:** PHP files, as the other two indexes.
- **Indexer:** AST-based, reusing the `ASTNodes.kt` helpers (`collectMethodCalls`, `isThisCall`,
  `stringLiteralValue`, array-literal walking). The class FQN comes from the file's `PhpClass`
  elements, the same way `ViewVariableASTDataIndexer` already uses `psiFile.node`. Walk each
  class's body and record every `$this->` call whose method name is in the source table above,
  together with the name of the enclosing method. For `addAssociations()`, walk the nested array
  argument and record one load per leaf. For the `PROPERTY_ARRAY` source, read the `$helpers`
  field initializer. Both use one array reader: a string value is a plain load, a string key with
  an array value is a load with options. Only `$this->` receivers are recorded for method calls.
- **Externalizer:** a `DataExternalizer` following `ViewVariableRawVarsExternalizer`.
- **Version:** starts at 1 and is bumped whenever the value format changes.

Why an index rather than scanning ancestor PSI at lookup time: `complete()` runs on every type
inference pass, the chain usually includes an `AppController` or `AppView` that every lookup
needs, and the two existing indexes already establish the AST-indexer pattern and its tests.

### `ConfiguredMemberService`

A project service wrapping the index with the resolution rules above:

- `loadedMembers(classFqns: List<String>, kinds: Set<MemberKind>): Map<String, ResolvedLoad>`:
  walks the chain for each FQN, merges by alias, and resolves each load to a `PhpType` plus the
  `PhpClass` list for navigation. `ResolvedLoad` records the kind and whether it came from an
  initialize load.
- `memberType(classFqns, kinds, alias, settings): MemberTypeResult?`: returns the resolved type
  for one alias together with whether an initialize load was found. `null` means nothing in the
  chain loads the alias. The caller adds the convention union unless an initialize load was found.

All consumers (type providers, completion contributors, goto handlers) call this service.
Nothing else reads the index directly.

## Changes to existing code

### `ControllerFieldTypeProvider`

`getType()` currently returns the convention union directly for controller receivers. Change it
to return a deferred signature so `complete()` can consult the index:

```
#<key>c<receiverClassFqn><key><fieldName>
```

The leading `c` marker distinguishes it from the existing nested-model signature, which carries
two field names and no FQN. `complete()` dispatches on the marker. For `c`, it asks
`memberType()` with kinds `{COMPONENT, TABLE}`, then adds `componentOrModelTypeFromFieldName()`
unless the result reports an initialize load. The nested-model path is unchanged.

### `ViewHelperInViewTypeProvider`

Currently returns the convention union directly. Change it to return a deferred signature carrying
the field name, and in `complete()` resolve against the `viewType(settings)` chain with kind
`HELPER`, adding `viewHelperTypeFromFieldName()` unless an initialize load was found.

### `ViewHelperInViewHelperTypeProvider`

Same change, with the receiver helper FQNs in the signature instead of the view type.

### `AssociatedTableTypeProvider`

The `v1` path (`$this->Movies->Articles`) resolves by name in `complete()` without knowing the
receiver table. Extend the signature to carry the receiver's resolved table FQN(s) when they are
known, and consult `memberType()` with kind `ASSOCIATION` before falling back to
`getAllPossibleAssociationTableClassesFromName()`. The `v2` path (`fetchTable('Movies')->Articles`)
already resolves the receiver through `lookupCompleteType()` and can call the service with that
result. Direct `$this->Authors` inside a table class is a new case for this provider and follows
the same pattern.

The service resolves the target table only. The provider keeps adding the four
`\Cake\ORM\Association` classes around that result exactly as the `v1` path does today, so
association methods such as `setForeignKey()` keep completing. Narrowing to the one association
class implied by the call (`belongsTo` to `BelongsTo`) would need the call's method name in the
record and is left as a possible refinement.

### Completion contributors

`ControllerComponentCompletionContributor`, `ControllerModelOrTableCompletionContributor`,
`ViewHelperInViewCompletionContributor` and `ViewHelperInViewHelperCompletionContributor`: call
`loadedMembers()` for the relevant kinds, add one lookup element per loaded alias with the
resolved class as type text, and skip the kind's convention list only when at least one of those
loads is an initialize load.

Association completion in controllers (`$this->Movies->Articles`) is the `nestedLookup()` path of
`ControllerModelOrTableCompletionContributor`. Today it decides whether to complete by matching
the inner field's *name* against table class FQNs, so an alias such as `$this->Films` that a
`loadModel` load maps to `MoviesTable` would produce no completion. Change it to resolve the inner
field reference's type through `lookupCompleteType()` first, check that the result is a table
class, and then query `loadedMembers()` with kind `ASSOCIATION` against that table's chain. The
name check goes away. There is no completion contributor for
`$this->Authors` inside a table class today, and this iteration does not add one; associations
inside tables get type resolution and navigation only.

### Goto handlers

`ControllerFieldGotoDeclarationHandler` and `ViewHelperGotoDeclarationHandler`: resolve through
the service first; add the convention classes unless the alias has an initialize load in the
chain. The field handler chooses kinds by the receiver type: `{COMPONENT, TABLE}` for
controllers, `ASSOCIATION` for tables. `ControllerComponentsGotoDeclarationHandler`: add a branch for a string literal that is
the first argument of a source method or the value of its `className` option.

## Future: CakePHP 2

CakePHP 2 configures through property arrays, not `initialize()`:

```php
public $components = array('Session', 'Auth' => array('authorize' => 'Controller'), 'Plugin.Name');
public $helpers = array('Html', 'Form');
public $uses = array('Movie');
```

These are `LoadSource.PROPERTY_ARRAY` records with kinds COMPONENT, HELPER and TABLE. The
`$helpers` reader built for CakePHP 3 to 5 views and helpers is the same reader; CakePHP 2 only
adds the `$components` and `$uses` field names and the global-namespace convention
(`\FooComponent`) at lookup time. The index format does not change. CakePHP 3 still accepts a
`$components` array on controllers, so that reader can be tested against CakePHP 3 fixtures
before CakePHP 2 is enabled.

## Testing

Follow the project rule: CakePHP 5 fixtures and tests first, then port to 4 and 3. `loadModel`
does not exist in CakePHP 5, so its tests start on the CakePHP 4 fixtures.

### New CakePHP 5 fixtures

Do not change `MovieController.php` or `AppView.php`. Their by-convention accesses are relied on
by many existing tests and exercise the fallback path once the override rule is in place.

Controllers, under `cake5/src5/Controller/`:

- `ConfiguredController.php` extending `AppController`, with `initialize()` that calls
  `parent::initialize()` and loads:
  - `'MovieMetadata'` (plain, exists in app),
  - `'Meta'` with `['className' => MovieMetadataComponent::class]` (alias to `::class`),
  - `'Legacy'` with `['className' => 'MovieMetadata']` (alias to plain string),
  - `'TestPlugin.InsidePlugin'` (plugin-prefixed),
  - `'Flash'` (expected to resolve to the core stub).

  It also has a `beforeFilter()` that loads `'TestPlugin.Conditional'`, and an action that
  re-loads `'Meta'` with `['className' => 'Conditional']`, to exercise the union rules.
- `ChildConfiguredController.php` extending `ConfiguredController` with no `initialize()`.
- `InsidePluginComponent.php` and `ConditionalComponent.php` in both the app and
  `TestPlugin`, with a distinct method name each.

Views and helpers, under `cake5/src5/View/`:

- `ConfiguredView.php` extending `\Cake\View\View`, whose `initialize()` calls
  `loadHelper('Html')`, `loadHelper('Fmt', ['className' => 'MovieFormatter'])` and
  `addHelper('TestPlugin.Shared')`.
- `cake5/configured_view/AppView.php` declaring `App\View\AppView extends ConfiguredView`.
  `viewType()` always names `App\View\AppView`, and a docblock cannot retype `$this` in a
  template, so the helper test class loads this file in `setUpTestFiles()` instead of the shared
  `cake5/src5/View/AppView.php`. The file's path does not matter for class resolution; only the
  template fixtures need to stay under `cake5/templates/`.
- `Helper/ConfiguredHelper.php` with `protected array $helpers = ['MovieFormatter', 'Alias' =>
  ['className' => 'ArtistFormatter']]`.
- `SharedHelper.php` in both the app and `TestPlugin`, with a distinct method name each.

Tables, under `cake5/src5/Model/Table/`:

- `ReviewsTable.php` whose `initialize()` calls `belongsTo('Movies')`,
  `belongsTo('Authors', ['className' => 'Users'])`, `hasMany('TestPlugin.Ratings')`, and
  `addAssociations(['belongsTo' => ['Editors' => ['className' => 'Users']], 'hasMany' =>
  ['Comments']])`.
- `UsersTable.php`, `CommentsTable.php` and `RatingsTable.php` in the app, `RatingsTable.php` in
  `TestPlugin`.

Vendor stub `cake5/vendor/cakephp.php`: add `loadComponent()` to `Controller`, `loadHelper()` and
`addHelper()` to `View`, the four association methods and `addAssociations()` to `Table`, and
`FlashComponent` under `Cake\Controller\Component`.

### Tests

`cake5/ConfiguredComponentTest.kt`, following the shape of `cake3/ControllerTest.kt`:

- Type of `$this->Meta` in `ConfiguredController` is exactly the app `MovieMetadataComponent`
  (completion of its methods works, `LookupElementPresentation.typeText` shows the FQN).
- Type of `$this->Legacy` matches the same class via the plain `className` string, proving the
  literal is not treated as an FQN.
- `$this->Movies` in `ConfiguredController` still resolves to `MoviesTable`, proving the override
  is per alias and table properties keep convention.
- `$this->InsidePlugin` completes the plugin component's method and not the app component's.
- `$this->Meta` and `$this->Flash` in `ChildConfiguredController` resolve through the parents.
- `$this->Conditional` completes methods from both `ConditionalComponent` classes, because the
  load is in `beforeFilter()`.
- `$this->Meta` completes methods from `MovieMetadataComponent` and from `ConditionalComponent`,
  because the action re-load unions onto the initialize load.
- Component completion on `$this->` in `ConfiguredController` lists exactly the loaded aliases
  and no unrelated component subclasses. Table completion on the same receiver still lists all
  tables, since nothing in the chain calls `loadModel`.
- Component completion on `$this->` in `MovieController`, whose chain has no loads, still lists
  all component subclasses. A controller whose only load is in `beforeFilter()` lists that alias
  and the convention candidates.
- Goto declaration on `$this->Meta` returns one target. Goto declaration on the string inside
  `loadComponent('MovieMetadata')` returns the component.

`cake5/ConfiguredHelperTest.kt`:

- `$this->Fmt` in a template resolves to `MovieFormatterHelper` through `ConfiguredView`.
- `$this->Shared` in a template completes the plugin helper's method only.
- `$this->Alias` inside `ConfiguredHelper` resolves to `ArtistFormatterHelper` via the `$helpers`
  array, and `$this->MovieFormatter` resolves by plain name.
- Helper completion in the template lists `Html`, `Fmt`, `Shared` and no other helpers.

`cake5/ConfiguredAssociationTest.kt`:

- `$this->Authors` inside `ReviewsTable` resolves to `UsersTable`.
- `$this->Ratings` inside `ReviewsTable` resolves to the plugin `RatingsTable` only.
- `$this->Editors` resolves to `UsersTable` and `$this->Comments` to `CommentsTable`, both from
  the `addAssociations()` array.
- `$this->fetchTable('Reviews')->Authors` in a controller resolves to `UsersTable`.

`cake4/ConfiguredTableTest.kt`, with a `ConfiguredController` fixture whose `initialize()` calls
`loadModel('Films', ['className' => 'Movies'])` and `loadModel('TestPlugin.Catalog')`:

- `$this->Films` resolves to `MoviesTable`; `$this->Catalog` resolves to the plugin table only.
- Table completion on `$this->` lists `Films` and `Catalog` and hides the convention list.
- Nested completion on `$this->Films->` offers the associations configured on `MoviesTable`,
  proving the inner alias is resolved by type rather than by name.

`ConfiguredMemberIndexerTest.kt` at the top level, following `ViewVariableASTParsingTest`, unit
tests the AST parsing: each source method maps to its kind, plain, aliased with string, aliased
with `::class`, plugin-dotted, non-literal argument ignored, loads outside `initialize()`
recorded with their enclosing method name, the same alias loaded twice in one method yielding two
records, `$helpers` array entries with and without options, `addAssociations()` leaves with and
without options under each of the four association type keys, an unrecognized top-level key in
`addAssociations()` ignored, two classes in one file keyed separately.

Then port the fixture additions and the integration tests to `cake4` and `cake3`.

## Implementation steps

1. `MemberLoad` data types, externalizer, `ConfiguredMemberIndex`, AST indexer for the
   `METHOD_CALL` source across all four kinds, `plugin.xml` registration. Indexer unit tests.
2. `ConfiguredMemberService` with chain walking and load resolution. Vendor stub and controller
   fixture additions for CakePHP 5.
3. Components: `ControllerFieldTypeProvider` deferred signature and `complete()` dispatch, the
   component completion contributor, both controller goto handlers. Component tests.
4. Tables on controllers: the TABLE kind in the same provider, `ControllerModelOrTableCompletionContributor`.
   CakePHP 4 `loadModel` tests.
5. Helpers: `PROPERTY_ARRAY` reader for `$helpers`, both helper type providers, both helper
   completion contributors, `ViewHelperGotoDeclarationHandler`. View and helper fixtures and tests.
6. Associations: `addAssociations()` array reading in the indexer, `AssociatedTableTypeProvider`
   changes. Table fixtures and tests.
7. Port fixtures and tests to CakePHP 4 and 3. Run the full suite.

Commit after each step. Steps 3 to 6 are independent once step 2 lands, so their order can
change if one turns out to be blocked.

## Decisions

- **Completion when the chain has initialize loads of a kind.** Hide that kind's convention list,
  as described under Completion. Revisit only if members loaded in unindexed vendor base classes
  turn out to be common.
- **Apps that load everything in `beforeFilter()`.** Common in CakePHP 3 code migrated from
  CakePHP 2. They get the union, not the override, so plugin-prefixed loads there stay ambiguous.
  Still an improvement over today. If it matters, a per-method allowlist (`initialize`,
  `beforeFilter`) could widen the override set later without an index change, since the method
  name is already stored.
- **`$components` property array on CakePHP 3 controllers.** Deferred. The `$helpers` reader in
  step 5 is the same code; enabling `$components` is a one-line addition to the field-name list
  when CakePHP 2 support begins.
