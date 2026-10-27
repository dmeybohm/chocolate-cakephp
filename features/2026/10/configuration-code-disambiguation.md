# Configuration code disambiguation

## Context

Dynamic property resolution on controllers is purely convention-based today. For `$this->Foo` on
any class whose name ends in `Controller`, `ControllerFieldTypeProvider.getType()` returns the
union produced by `componentOrModelTypeFromFieldName()` in `Classes.kt`: every plausible
`FooComponent` and `FooTable` FQN across the core namespace, the app namespace, and every
configured plugin namespace. `ControllerComponentCompletionContributor` offers every component
subclass in the project, and `ControllerFieldGotoDeclarationHandler` navigates to every class the
convention union names.

Nothing reads the code that actually configures a class. `initialize()` appears only in the
skip list of rendering methods. This has three visible consequences:

1. Aliased loads cannot resolve at all. `$this->loadComponent('Meta', ['className' =>
   MovieMetadataComponent::class])` leaves `$this->Meta` untyped.
2. Same-named components in the app and a plugin both appear in the union, so completion and
   navigation are ambiguous even when the class loads only one of them with a plugin prefix.
3. Completion lists components the class never loads.

This feature indexes the configuration calls and uses them to resolve dynamic members. The
guiding rule, decided up front: **a `loadComponent()` in `initialize()` overrides the
by-convention resolution for that alias. A load anywhere else is unioned, not overriding.**
`initialize()` runs unconditionally on every request, so what it loads is what the class has.
Loads in `beforeFilter()`, in actions, or inside conditionals may be dynamic re-loads or may not
run at all, so they widen the result instead of replacing it. Convention remains the fallback for
aliases that no indexed class in the inheritance chain loads.

Scope for this iteration is CakePHP 3, 4 and 5 (the `cake3Enabled` code path), components first.
The design keeps CakePHP 2, helpers, and tables/behaviors in view so they slot into the same
index and resolver later without a redesign.

## Semantics

### Resolution of `$this->Foo` on a controller class `C`

1. Collect the inheritance chain of `C` from `PhpIndex`: `C`, its superclass, and so on, stopping
   at `\Cake\Controller\Controller` or when a class is unknown. Guard against cycles.
2. For each class in the chain, read its indexed loads and partition them by alias into
   *initialize loads* (enclosing method is `initialize`) and *other loads* (any other method).
3. If alias `Foo` has an initialize load somewhere in the chain, find the most-derived class
   whose `initialize()` loads `Foo`. The base type is the union of all of that class's initialize
   loads of `Foo` (see below). Initialize loads of `Foo` in ancestors of that class are excluded,
   matching `ComponentRegistry` behaviour where a later load replaces the earlier instance. The
   convention union is not added. Any other loads of `Foo` anywhere in the chain are unioned onto
   that base type, so a dynamic re-load in an action widens the type rather than being ignored.
4. If `Foo` has only other loads, the type is the union of their resolutions and today's
   convention union. For a plain `loadComponent('Foo')` this collapses to the convention result;
   the union only changes the outcome for aliased or plugin-prefixed loads outside `initialize()`.
5. If no class in the chain loads `Foo`, fall back to today's convention union. This keeps the
   plugin working when the load happens in a vendor base class that is not indexed, in a
   component's own `$components` list, or through `$this->components()->load()`.

Loads are indexed from anywhere in the class body. The enclosing method name is stored with each
load so the resolver can apply the partition above, and so CakePHP 2's `beforeFilter()` loads
and future diagnostics have the same information available.

The override is per alias. Only the alias that `initialize()` loads loses its convention
candidates; every other property on the class, including table properties like
`$this->Movies`, resolves exactly as today. Dropping the table candidate for a loaded alias is
correct, not a loss: the framework sets the loaded component on the controller under that alias,
so a same-named table is shadowed at runtime too. `ControllerModelOrTableCompletionContributor`
is not touched by this feature, so it may still offer a table name that a loaded component
shadows. That is a known completion limitation of this iteration; the resolved type of such a
property is still the component.

Within one method, the same alias loaded more than once (for example in both branches of an
`if`) produces one record per call and the resolutions are unioned. Source order inside a method
is ignored. Across the chain, the assumption is that every `initialize()` calls
`parent::initialize()`, which the framework requires for `AppController` loads to work at all.
A child that omits the parent call still inherits the parent's indexed loads here; tracking the
parent call is possible later since the indexer sees the method body, but it is not worth the
complexity for a case that is a bug in the application.

### Resolving a single load

A load is recorded as `(alias, loadedName, className?, methodName)`:

- `loadComponent('Flash')`: alias `Flash`, loadedName `Flash`, no className.
- `loadComponent('TestPlugin.Inside')`: alias `Inside`, loadedName `TestPlugin.Inside`.
- `loadComponent('Meta', ['className' => 'MovieMetadata'])`: alias `Meta`, className
  `MovieMetadata`.
- `loadComponent('Meta', ['className' => MovieMetadataComponent::class])`: className is the
  resolved FQN from `ClassConstantReference.getStaticClassRefOrNull()`, which uses the file's
  `use` imports and needs no index access.

A string literal `className` is stored as its raw contents. `getStringifiedClassOrNull()` must
not be used for it, because that helper prepends a backslash to any string and would turn the
plain name `'MovieMetadata'` into an FQN.
- `loadComponent($name)` or any non-literal first argument: not recorded.

At lookup time, with settings and `PhpIndex` available:

- A `className` containing a backslash is an FQN and is used directly.
- Otherwise the name to resolve is `className` if present, else `loadedName`.
- A dotted name `Plugin.Name` resolves only to `${plugin.namespace}\Controller\Component\NameComponent`
  for the configured plugin called `Plugin`. If no configured plugin matches, treat the whole
  string as a plain name, mirroring the `parseAndLookupPlugin()` fallback already used for
  templates.
- A plain name resolves to the convention candidates for that name (core, app, plugins). Because
  `PhpIndex` is available here, filter the candidates to classes that exist. If none exist, keep
  the full candidate set so completion still shows something useful.

The index stores syntax only: raw strings and FQNs from the file. Namespaces, plugin
configuration, and existence checks are applied at lookup time. This matches the precedent in
`resolve-element-paths-at-lookup.md` and means a settings change never requires a reindex.

### Completion inside a controller

Loaded aliases from both initialize loads and other loads are always offered, each presented
with its resolved class as the type text. The convention-based list of all component subclasses
is shown only when the chain has no initialize loads at all. When the chain has no indexed loads
of any kind, behaviour is unchanged.

This is a deliberate trade-off against the per-alias fallback rule. A component loaded only in an
unindexed vendor base class still resolves when typed by hand, but is not offered in completion
once the chain has an initialize load. Showing the full convention list at lower priority would
restore discoverability for that rare case at the cost of the noise this feature exists to
remove. Table completion is a separate contributor and is unaffected.

Aliases can differ from class names, so the loaded-alias path builds `LookupElementBuilder`s
directly instead of going through `completeFromClasses(removeFromEnd = "Component")`.

### Navigation

- `$this->Foo`: `ControllerFieldGotoDeclarationHandler` goes through the same resolver, so an
  aliased or plugin-prefixed load navigates to exactly one class.
- The string literal in `loadComponent('<caret>Foo')` and the `className` option value navigate
  to the resolved component class. `ControllerComponentsGotoDeclarationHandler` currently only
  handles the `$components` and `$helpers` property arrays. Extend it to the `loadComponent()`
  call site.
- Completion of component names inside `loadComponent('<caret>')` is a natural addition and is
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
      kind: MemberKind,        // COMPONENT now; HELPER, TABLE, BEHAVIOR later
      source: LoadSource,      // METHOD_CALL now; PROPERTY_ARRAY later (CakePHP 2, $components)
      alias: String,
      loadedName: String,      // may be plugin-dotted
      className: String?,      // from 'className' option; FQN or plain
      methodName: String?,     // enclosing method; "initialize" drives the override rule
      offset: Int,             // for navigation and future diagnostics
  )
  ```

- **Input filter:** PHP files, as the other two indexes.
- **Indexer:** AST-based, reusing the `ASTNodes.kt` helpers (`collectMethodCalls`, `isThisCall`,
  `stringLiteralValue`, array-literal walking). The class FQN comes from the file's `PhpClass`
  elements, the same way `ViewVariableASTDataIndexer` already uses `psiFile.node`. Walk each
  class's body and record every `$this->loadComponent(...)` call together with the name of the
  enclosing method. Only `$this->` receivers are recorded; `$controller->loadComponent()` from outside the class is rare and ambiguous.
- **Externalizer:** a `DataExternalizer` following `ViewVariableRawVarsExternalizer`.
- **Version:** starts at 1 and is bumped whenever the value format changes.

Why an index rather than scanning ancestor PSI at lookup time: `complete()` runs on every type
inference pass over a controller, the chain usually includes an `AppController` that other
features will also want to query (behaviours, the future undefined-member inspection), and the
two existing indexes already establish the AST-indexer pattern and its tests. Lookup-time PSI
scanning with a `CachedValue` would work for components alone but would need to be rebuilt once
helpers and behaviours arrive.

### `ConfiguredMemberService`

A project service wrapping the index with the resolution rules above:

- `loadedMembers(classFqns: List<String>, kind: MemberKind): Map<String, ResolvedLoad>`: walks
  the chain for each FQN, merges by alias, and resolves each load to a `PhpType` plus the
  `PhpClass` list for navigation.
- `memberType(classFqns, kind, alias, settings): MemberTypeResult?`: returns the resolved type
  for one alias together with whether an initialize load was found. `null` means nothing in the
  chain loads the alias. The caller adds the convention union unless an initialize load was found.

All three consumers (type provider, completion contributor, goto handler) call this service.
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
`ConfiguredMemberService.memberType()`, then adds `componentOrModelTypeFromFieldName()` unless
the result reports an initialize load. The nested-model path is unchanged.

`$this` inside a controller already carries the controller's FQN, so the receiver FQN is
available in `getType()` without index access. Receivers typed by docblock behave the same way.

### `ControllerComponentCompletionContributor`

After computing `controllerClassNames`, call `loadedMembers()`. Add one lookup element per
loaded alias with the resolved class as type text. Skip the convention list only when at least
one of those loads is an initialize load. Otherwise keep the current behaviour in addition.

### `ControllerFieldGotoDeclarationHandler`

Resolve through the service first; add `componentAndModelClassesFromFieldName()` unless the
alias has an initialize load in the chain. Model fields are unaffected because `memberType()` for
`COMPONENT` returns `null` for them and the fallback still includes table candidates.

### `ControllerComponentsGotoDeclarationHandler`

Add a branch for a string literal that is the first argument of `$this->loadComponent()` or the
value of its `className` option, resolving through the service.

## Future phases (design only, not in this branch)

### Helpers

`View::initialize()` uses `loadHelper()` (3.x, 4.x) and `addHelper()` (4.x, 5.x). Controllers
also configure helpers through `$this->viewBuilder()->addHelper()` and `setHelpers()`. Helper
classes list dependencies in a `$helpers` array. All of these fit `MemberKind.HELPER` with either
`METHOD_CALL` or `PROPERTY_ARRAY` as the source.

The receiver problem is different from controllers: templates have no class. `$this` in a
template is typed by the plugin as `viewType(settings)`, which is `AppView`, so helper lookups in
templates resolve against the `AppView` chain. Controller-side `viewBuilder()` helper calls would
need the template-to-controller mapping that `ViewFileIndex` already provides.

### CakePHP 2

CakePHP 2 configures through property arrays, not `initialize()`:

```php
public $components = array('Session', 'Auth' => array('authorize' => 'Controller'), 'Plugin.Name');
public $helpers = array('Html', 'Form');
public $uses = array('Movie');
```

This is `LoadSource.PROPERTY_ARRAY`. Each array entry becomes a `MemberLoad` with alias and
loadedName taken from the key or the string value, and `className` from a nested `className`
option. The resolver applies CakePHP 2's global namespace (`\FooComponent`) at lookup time, so
the index format does not change. Since CakePHP 3 still accepts the `$components` array, the
`PROPERTY_ARRAY` source can be implemented and tested against CakePHP 3 fixtures before CakePHP 2
is enabled, which is the recommended order.

### Tables and behaviours

`$this->loadModel('Articles')` (3.x, 4.x) fits `MemberKind.TABLE`. `$this->addBehavior('Tree')`
in `Table::initialize()` fits `MemberKind.BEHAVIOR` and is the foundation for the behaviour
support idea. Both reuse the same index and service.

## Testing

Follow the project rule: CakePHP 5 fixtures and tests first, then port to 4 and 3.

### New CakePHP 5 fixtures

Do not change `MovieController.php`. Its `$this->Auth` and `$this->MovieMetadata` accesses
resolve by convention today and many existing tests depend on that; they also exercise the
fallback path once the override rule is in place.

Add under `cake5/src5/Controller/`:

- `ConfiguredController.php` extending `AppController`, with `initialize()` that calls
  `parent::initialize()` and loads:
  - `'MovieMetadata'` (plain, exists in app),
  - `'Meta'` with `['className' => MovieMetadataComponent::class]` (alias to `::class`),
  - `'Legacy'` with `['className' => 'MovieMetadata']` (alias to plain string),
  - `'TestPlugin.InsidePlugin'` (plugin-prefixed),
  - `'Flash'` (expected to resolve to the core stub).

  It also has a `beforeFilter()` that loads `'TestPlugin.Conditional'`, and an action that
  re-loads `'Meta'` with `['className' => 'Conditional']`, to exercise the union rules.
- `ChildConfiguredController.php` extending `ConfiguredController` with no `initialize()`, to
  test inheritance.
- `cake5/plugins/TestPlugin/src/Controller/Component/InsidePluginComponent.php` and an
  `App\Controller\Component\InsidePluginComponent.php` with a distinct method name each, so a
  plugin-prefixed load can be shown to pick the plugin class only.
- The same pairing for `ConditionalComponent`, so a plugin-prefixed load outside `initialize()`
  can be shown to union both classes.
- `cake5/vendor/cakephp.php`: add `loadComponent()` to the `Controller` stub so the call resolves
  in PhpStorm, and a `FlashComponent` stub under `Cake\Controller\Component`.

### Tests

`cake5/ConfiguredComponentTest.kt`, following the shape of `cake3/ControllerTest.kt`:

- Type of `$this->Meta` in `ConfiguredController` is exactly the app `MovieMetadataComponent`
  (completion of its methods works, `LookupElementPresentation.typeText` shows the FQN).
- Type of `$this->Legacy` matches the same class via the plain `className` string, proving the
  literal is not treated as an FQN.
- `$this->Movies` in `ConfiguredController` still resolves to `MoviesTable`, proving the override
  is per alias and table properties keep convention.
- `$this->InsidePlugin` in `ConfiguredController` completes the plugin component's method and not
  the app component's method.
- `$this->Meta` in `ChildConfiguredController` resolves through the parent's `initialize()`.
- `$this->Flash` in `ChildConfiguredController` resolves through `AppController`.
- `$this->Conditional` in `ConfiguredController` completes methods from both the plugin and the
  app `ConditionalComponent`, because the load is in `beforeFilter()`.
- `$this->Meta` in `ConfiguredController` completes methods from `MovieMetadataComponent` and
  from `ConditionalComponent`, because the action re-load unions onto the initialize load.
- Component completion on `$this->` in `ConfiguredController` lists `Meta`, `Legacy`,
  `MovieMetadata`, `InsidePlugin`, `Conditional`, `RequestHandler`, `Flash` and does not list
  unrelated component subclasses.
- Component completion on `$this->` in a controller whose only load is in `beforeFilter()` lists
  that alias and still lists the convention candidates.
- Component completion on `$this->` in `MovieController` (no loads anywhere in its chain, since
  it extends `Controller` directly) still lists all component subclasses.
- Goto declaration on `$this->Meta` returns one target.
- Goto declaration on the string inside `loadComponent('MovieMetadata')` returns the component.

`ConfiguredMemberIndexerTest.kt` at the top level, following `ViewVariableASTParsingTest`, unit
tests the AST parsing: plain, aliased with string, aliased with `::class`, plugin-dotted,
non-literal argument ignored, loads outside `initialize()` recorded with their enclosing method
name, the same alias loaded twice in one method yielding two records, two classes in one file
keyed separately.

Then port the fixture additions and the integration tests to `cake4` and `cake3`.

## Implementation steps

1. `MemberLoad` data types, externalizer, `ConfiguredMemberIndex`, AST indexer, `plugin.xml`
   registration. Indexer unit tests.
2. `ConfiguredMemberService` with chain walking and load resolution. Vendor stub and fixture
   additions for CakePHP 5.
3. `ControllerFieldTypeProvider` deferred signature and `complete()` dispatch. Type tests.
4. `ControllerComponentCompletionContributor` loaded-alias path. Completion tests.
5. Both goto handlers. Navigation tests.
6. Port fixtures and tests to CakePHP 4 and 3. Run the full suite.

Commit after each step.

## Open decisions

- **Completion when the chain has initialize loads.** Decided: hide the convention list, as
  described under Completion. Revisit only if components loaded in unindexed vendor base classes
  turn out to be common.
- **Apps that load everything in `beforeFilter()`.** Common in CakePHP 3 code migrated from
  CakePHP 2. They get the union, not the override, so plugin-prefixed loads there stay ambiguous.
  Still an improvement over today. If it matters, a setting or a per-method allowlist
  (`initialize`, `beforeFilter`) could widen the override set later without an index change,
  since the method name is already stored.
- **`$components` property array on CakePHP 3.** Cheap to include now and it exercises the
  CakePHP 2 path early. Deferred by default to keep the first branch focused; pull it in if step 1
  finishes quickly.
