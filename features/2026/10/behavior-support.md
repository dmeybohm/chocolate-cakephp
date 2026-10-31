# Behavior support

## Context

Feature 2 in `features/ideas.md`. A CakePHP table attaches behaviors in `initialize()`:

```php
$this->addBehavior('Timestamp');
$this->addBehavior('Tree', ['parent' => 'parent_id']);
$this->addBehavior('TestPlugin.Sluggable');
$this->addBehavior('Audit', ['className' => 'ChangeLog']);
```

A behavior mixes two things into the table: methods, so that `$this->Articles->touch($entity)`
and `$this->Articles->recover()` work, and finders, so that `find('children', for: 1)` and
`find('treeList')` work. The plugin knows nothing about either. In a real project,
`$this->Articles->touch($entity)` resolves through `Table::__call` and PhpStorm types it `mixed`,
offers no behavior methods in completion, flags the call with the "method accessed via magic"
weak warning, and cannot navigate to the behavior. `find('<caret>')` offers only the finders the
table class declares itself, and `addBehavior('Timestamp')` is a string with no navigation.

CakePHP 2 has the same mechanism under another name: `public $actsAs = ['Containable', 'Tree']`
on a model, with the behavior's methods callable on the model through `Model::__call`.

Two pieces of groundwork this plan builds on are merged, or will be before implementation starts:

- **Configuration code disambiguation** (#306 and its follow-ups,
  [configuration-code-disambiguation](configuration-code-disambiguation.md)) gave the plugin
  `ConfiguredMemberIndex`, which records the `$this->load…()` calls a class makes together with
  the enclosing method, and `ConfiguredMemberService`, which walks a class chain and applies the
  "a load in `initialize()` overrides, a load anywhere else is unioned" rule. The log explicitly
  left `addBehavior()` out: "Behaviours mix in methods, not properties, and belong to the
  separate behaviour support idea." This is that idea.
- **Entity virtual fields** ([entity-virtual-fields](entity-virtual-fields.md), merged as #315
  on 2026-10-31) settled how the plugin answers for a member PhpStorm would otherwise resolve
  through a magic method: a provider keyed on the native signature with
  `interceptsNativeSignature()` false, `complete()` answering for every signature a reference
  gets, the `#P` provider being the only one per key, and the receiver encodings table. It also
  added `ConfiguredMemberService.associationLoads()`, which this feature extends so that
  associations a behavior adds reach entity properties as well as query methods.

The branch starts from `main` at `1871b27d`, the #315 merge, so both pieces are in place.

## How CakePHP attaches a behavior

Checked against the vendored 3.10.5, 4.6.5 and 5.4.3 sources under
`src/test/php/cake*/vendor/cakephp/cakephp`, and 2.10 under `build/cakephp-vendor/2`.

### Loading, 3 to 5

- `Table::addBehavior($name, $options)` calls `BehaviorRegistry::load()`, which is
  `ObjectRegistry::load()`. The alias is the part of `$name` after the dot (`pluginSplit()`), or
  `$name` itself when a `className` option is given. The class is
  `App::className($class, 'Model/Behavior', 'Behavior')`, falling back to
  `App::className($class, 'ORM/Behavior', 'Behavior')`: so `Timestamp` is
  `App\Model\Behavior\TimestampBehavior` if the app has one, else
  `Cake\ORM\Behavior\TimestampBehavior`; `TestPlugin.Sluggable` is
  `TestPlugin\Model\Behavior\SluggableBehavior` and never falls back to the app; a `className`
  option takes a plain name, a plugin-dotted name or an FQN, exactly as it does for components.
- `Table::addBehaviors([...])` takes `['Timestamp', 'Tree' => ['parent' => ...]]`: an integer
  key is a plain load, a string key with an array value is a load with options. This is the
  shape `addAssociations()` leaves already have in the indexer.
- `Table::removeBehavior($name)` and `Table::hasBehavior($name)` take the alias.
  `Table::getBehavior($name): Behavior` (3.6+) returns the loaded instance and throws for an
  unloaded alias; its declared return type is the base class, so PhpStorm offers only the base
  class's methods on `$table->getBehavior('Tree')->`. `Table::behaviors()` returns the registry,
  whose `get($alias)` does the same.
- Loading the same alias twice with different options throws. Loading it twice with no options
  returns the existing instance.

### Which methods are mixed in, 3 to 5

`BehaviorRegistry::_getMethods()` asks the instance for `implementedFinders()` and
`implementedMethods()`, lowercases the keys, and records `[alias, methodName]` for each. A key
present from a behavior already loaded throws a `LogicException`, so two attached behaviors
never provide the same name. `Table::__call` checks `hasMethod()` and dispatches; it only runs
when the table does not declare the method, so a table's own method always wins.
`Table::callFinder()` checks `method_exists($this, 'find' . $type)` first, then the behavior
finder map, so a table's own `findX()` always wins too. `Query::find()` delegates to the table's
`callFinder()`, so a finder a behavior adds is available on a query as well.

`Behavior::implementedMethods()` returns the `implementedMethods` config key when it is set,
else the reflection map. Likewise for finders. The config key is set when:

- the behavior declares it in `$_defaultConfig`, as every built-in does: `TreeBehavior` maps
  finders `path`, `children`, `treeList` and methods `childCount`, `moveUp`, `moveDown`,
  `recover`, `removeFromTree`, `getLevel`, `formatTreeList`; `TimestampBehavior` maps
  `timestamp` and `touch` and no finders; `TranslateBehavior` maps the finder `translations`
  and the methods `setLocale`, `getLocale`, `translationField`, `getStrategy` (5; 3 and 4
  differ slightly). A public method missing from the map, such as `TranslateBehavior::setStrategy()`,
  is not mixed in.
- `addBehavior()` passes it in the options. `Behavior::_resolveMethodAliases()` merges: when the
  defaults also set the key, the result is the custom map plus every default method the custom
  map does not mention, each under its default alias, and an empty array disables every method
  of that key; when the defaults do not set the key, the custom map replaces reflection outright.

The map is `alias => methodName`. `['trail' => 'writeAudit']` mixes `writeAudit()` in as
`$table->trail()`.

The reflection map, `Behavior::_reflectionCache()`, takes every public method of the concrete
class, inherited ones included, and skips those `Cake\ORM\Behavior` itself has (its own and the
`InstanceConfigTrait` ones: `getConfig`, `setConfig`, `configShallow`, `getConfigOrFail`,
`table()`/`getTable()`, `initialize`, `verifyConfig`, `implementedEvents`, `implementedFinders`,
`implementedMethods`) and those named in `implementedEvents()`: by default the callbacks in the
fixed `$eventMap`, which are `beforeMarshal`, `afterMarshal`, `beforeFind`, `beforeSave`,
`afterSave`, `afterSaveCommit`, `beforeDelete`, `afterDelete`, `afterDeleteCommit`,
`buildValidator`, `buildRules`, `beforeRules`, `afterRules`. A remaining method whose name
starts with `find` is a finder keyed `lcfirst(substr($name, 4))`, so `findSlugged()` is the
`slugged` finder; any other is a method keyed by its own name. Protected and private methods are
never taken. A magic method such as a behavior's own `__call` is public and not on the base
class, so reflection would list it; no real behavior relies on that, and the plugin skips names
starting with two underscores.

### Deprecation on 5.3

`BehaviorRegistry::call()` on 5.3.0 and later emits a deprecation warning: "Calling behavior
methods on the table instance is deprecated. Use `$table->getBehavior('YourBehavior')->method()`
instead." The finder path is not deprecated. So on 5.x the idiomatic forms are `find('children')`
and `$table->getBehavior('Tree')->recover()`, and the plugin's support for `getBehavior()` is as
important as the mixed-in methods. The plugin only knows the major version, so it cannot strike
the mixed-in form out on 5.3 alone; see the decisions.

### Associations a behavior adds

A behavior receives the table as `$this->_table` (also `$this->table()` on 4 and 5, and
`$this->getTable()` on 3 and 4) and may configure associations on it in `initialize()`. The 3.x
`TranslateBehavior` does this directly (`$this->_table->hasOne($name, ...)` and
`$this->_table->hasMany($targetAlias, ...)`); on 4 and 5 the same calls moved into the translate
strategy classes, which hold the table as `$this->table`, where no static reading can connect
them to a behavior. App behaviors that add associations use the direct form. These associations
belong to the table: `contain()` accepts them, `$table->Alias` reaches them, and the entity gets
the property.

### CakePHP 2

`Model::__construct()` calls `$this->Behaviors->init($this->alias, $this->actsAs)`, and
`$this->Behaviors->load('Name', $config)` attaches one at runtime. `$actsAs` is
`['Containable', 'Tree' => ['scope' => ...], 'Plugin.Name']`, the same shape as `$components`.
The class is the global `{Name}Behavior`, found in `app/Model/Behavior/`, a plugin's
`Model/Behavior/` or `lib/Cake/Model/Behavior/` (`Acl`, `Containable`, `Translate`, `Tree`).
Because the names are global, the plugin prefix only says where to look when the class is not
loaded yet: once `AuditBehavior` has been loaded from the app, `Probe.Audit` attaches that same
instance, and a bare `Hashable` attaches a plugin's class that something else loaded. So the
plugin resolves every CakePHP 2 behavior name to `\{Name}Behavior` regardless of prefix, as it
already does for components and helpers.

`BehaviorCollection::load()` maps every method `get_class_methods()` returns for the instance
that `ModelBehavior` does not have, whose name does not start with `_`, and that is not one of
the callbacks `setup`, `cleanup`, `beforeFind`, `afterFind`, `beforeSave`, `afterSave`,
`beforeDelete`, `afterDelete`, `onError`. The first behavior to map a name keeps it. A mapped
method is declared `method(Model $model, ...$args)` and called as `$model->method(...$args)`, so
the caller omits the first parameter. `$mapMethods` adds regex-matched names, which no static
reading can offer. CakePHP 2 behaviors do not add finders; `find()` types come from the model's
`$findMethods`.

## Assumption tests

The framework behaviour above is pinned in the
[CakePHP assumption suite](../../../src/test/php/README.md), run with
`scripts/run-cakephp-assumption-tests.sh test -- --filter Behavior`, so the plugin is built on
verified behaviour and a moved pin fails a test before a feature does. The catalogue in that
README has one row per assumption.

- **`BehaviorTest`** (3, 4, 5): class resolution for bare, plugin-dotted and `className` loads;
  `addBehaviors()`; the reflection map and its exclusions; a parent behavior's methods;
  `$_defaultConfig` maps on an app behavior and on the core `Tree`; the three merge cases for
  `implementedMethods` in the load options; the table's own method and finder winning; the
  duplicate-method `LogicException`; a finder on a query; a behavior attached outside
  `initialize()`; a child table's inherited behaviors; `getBehavior()`; the 5.3 deprecation
  (and its absence on 3 and 4); and an association added through `$this->_table` and
  `$this->table()` (`getTable()` on 3) reaching `contain()` and the entity.
- **`BehaviorTest`** (2): `$actsAs` plain, with settings and plugin-prefixed; the mapped-method
  exclusions; the dropped `Model` parameter; the first behavior keeping a duplicate name; the
  model's own method winning; `Behaviors->load()` at runtime, with a `className` alias; the two
  missing-class cases for a class not yet loaded; and an already-loaded class answering to any
  name.

Fixtures are `SluggableBehavior`, `ChildSluggableBehavior`, `AuditBehavior`,
`CounterCacheBehavior` (an app behavior named like a core one) and `TaggableBehavior` under each
version's `src/Model/Behavior`, `SluggableBehavior` and `HashableBehavior` under the `Probe`
plugin, and `PagesTable`, `ChildPagesTable`, `PluginPagesTable`, `PhotosTable`, `TagsTable` and
the `Tag` entity. The CakePHP 2 app gained `AppModel`, `Page`, `SluggableBehavior`,
`AuditBehavior`, `StampableBehavior`, `Note` and the plugin's `HashableBehavior` and
`TrackableBehavior`.

Writing them corrected two readings. The first is the CakePHP 2 class resolution above: the
first draft expected a plugin prefix to isolate the lookup as it does on 3 to 5, and the
framework showed the class name alone decides once it is loaded. The second is the merge rule: `_resolveMethodAliases()` merges by
*method name*, so a custom alias for a method a default already maps replaces the default alias
rather than adding a second one. `['trail' => 'writeAudit']` over `['audit' => 'writeAudit']`
gives `trail` only; `['extra' => 'notMixedIn']` gives `extra` and `audit`. The fixture and test
descriptions below follow that.

## What PhpStorm does today

- `$table->touch($e)` is the native method signature `#M<receiver>.touch`. When
  `findMethodsByName` finds nothing, resolution falls to `__call`, whose return type is `mixed`
  (`: mixed` on 5, `@return mixed` on 3 and 4). As with fields, a provider on the plugin's own key
  can only add beside `mixed`; replacing it needs a provider keyed on `PhpTypeSignatureKey.METHOD`
  (`M`) with `interceptsNativeSignature()` false, which `fix-magic-get-type-dilution` found is a
  sanctioned pattern (the bundled Prophecy providers key on `M`, with intercepting on). The
  entity work found `PhpIndexImpl.getTypeProviders()` logs an error for two *non-intercepting*
  providers with the same key; whether an intercepting bundled provider and a non-intercepting
  one of ours can share `M` is the first thing to check.
- A reference gets many signatures (one per receiver encoding), each completed separately, and
  one that falls through to `__call` puts `mixed` back. The entity log's receiver table is the
  starting point; the method receivers (`$this->Articles->touch()`, `$this->fetchTable('Articles')->touch()`,
  `$table->touch()` with a typed parameter, `$this->touch()` inside the table,
  `$this->Articles->Comments->touch()`) are probed in session 1 and recorded below.
- `find('<caret>')` completion is `CustomFinderCompletionContributor`, which lists the `find*`
  methods of the receiver tables from `queryReceiverTables()`; navigation is
  `CustomFinderGotoDeclarationHandler` through `customFinderMethods()`. Both already resolve the
  receiver to table FQNs, so behavior finders slot in beside the table's own.
- `addBehavior('Timestamp')` is a plain string. `ControllerComponentsGotoDeclarationHandler`
  navigates from the first argument of any method in `methodCallSources` to the loaded class, so
  adding `addbehavior` to that map gives the string navigation for free once the kind resolves.
- `PhpUndefinedMethodInspection` reports a method the class lacks as a weak warning when the
  class has `__call`. The exact text and whether it fires on a `Table` subclass in the test
  platform are checked in session 1, as the entity work did for fields.

## Scope

In the order to implement them, each on cake5 first, then cake4 and cake3, then cake2 as its own
step:

### 1. Index behavior loads

`MemberKind.BEHAVIOR` in `ConfiguredMemberIndex`, read by the existing indexer:

| Source | Shape | `LoadSource` | Versions |
|---|---|---|---|
| `$this->addBehavior('Name', [options])` | like `loadComponent` | `METHOD_CALL` | 3, 4, 5 |
| `$this->addBehaviors([...])` | like `addAssociations` leaves, one level | `METHOD_CALL` | 3, 4, 5 |
| `public $actsAs = [...]` | like `$components` | `PROPERTY_ARRAY` | 2 |
| `$this->Behaviors->load('Name', $config)` | receiver is `$this->Behaviors` | `METHOD_CALL` | 2 |

The options read are `className` (as today), and the `implementedMethods` and `implementedFinders`
arrays, stored on the load as `methodAliases: Map<String, String>?` and `finderAliases` with
the same `hasUnread…` flags the `className` and `propertyName` options use: a map present but
not readable (a variable, a constant) sets the flag and is treated as unknown, a map read
as `[]` is an empty map, and an absent key is null. The alias is the part after the dot, as for
every other load, except that a `className` option makes the first argument the alias
verbatim, which `memberLoad()` already does.

The `$this->Behaviors->load()` form is the one source whose receiver is not `$this`; the
indexer's `readMethodCall()` gains a branch for a `$this->Behaviors` receiver with method
`load`, recorded as a `METHOD_CALL` load with the enclosing method name, which the override rule
then treats as an other load unless it is in `initialize()` (which does not exist on 2, so it is
always a union). `textNeedles` gains `Behavior` and `actsAs`. The index version is bumped.

Associations a behavior adds, from step 7, are also indexed here: inside any class, an
association method called on `$this->_table`, `$this->table()` or `$this->getTable()` is recorded
under that class's FQN as an `ASSOCIATION` load with a new `viaTable: Boolean` flag, with the
enclosing method name as usual. The flag keeps the load out of the behavior's own resolution
(a behavior is never a receiver of `$this->Alias`) and lets step 7 pick them up.

### 2. Resolve a table's behaviors

`ConfiguredMemberService.behaviors(project, tableFqns): List<AttachedBehavior>` walks the table
chain with `InitializeChainResolver`, kinds `{BEHAVIOR}`, so an `initialize()` load replaces an
ancestor's load of the same alias and a load elsewhere is unioned, exactly as for components.
Each result carries the alias, the resolved `PhpClass` list, the load (for its options and
offset), and whether it is an initialize load. Class resolution adds the `BEHAVIOR` case to
`resolveLoad()`, with a rule of its own rather than `conventionCandidates()`: a bare name is the
*first existing* class of the app namespace's `Model\Behavior\{N}Behavior` and then
`\Cake\ORM\Behavior\{N}Behavior`, and nothing when neither exists. That is `App::className()`,
which `testBareNameLoadsAppBehaviorBeforeCore` pins: the app class shadows the core one of the
same name, so an app `TimestampBehavior` must not show the core behavior's methods beside its
own. The configured plugins are not candidates for a bare name, even from a plugin's own table
(`testBareNameDoesNotReachPluginBehavior`); a plugin-dotted name resolves only under that plugin,
and a `className` option follows the same first-match rule for its plain name. On CakePHP 2 the
class is the global `\{N}Behavior` whatever the prefix. The convention helper keeps returning
every candidate for the other kinds, where `$this->Foo` genuinely can be any of them.

`BehaviorMembers` (new, in a `behavior` package) computes the method and finder maps of one
behavior class the way `_reflectionCache()` does, then applies the load's options the way
`_resolveMethodAliases()` does:

1. Read `implementedMethods` and `implementedFinders` from the `$_defaultConfig` array literal
   of the class or the nearest ancestor that declares the property. A key that is present but
   not an array literal is unknown.
2. If neither key is set, reflect: walk the class and its parents up to and excluding
   `\Cake\ORM\Behavior`, nearest declaration first, taking public methods, skipping names the
   base class has (looked up from `\Cake\ORM\Behavior` in `PhpIndex`, so the list follows the
   attached release), the thirteen default event names, and `__`-prefixed names. `find*` names
   go to the finder map keyed by `lcfirst` of the rest, the others to the method map keyed by
   themselves.
3. Apply the load's `methodAliases` and `finderAliases`: with defaults present, custom plus the
   unmentioned defaults, and `[]` disables; without defaults, custom replaces reflection. An
   unread option leaves the behavior with no members of that key, since guessing would offer
   methods the author may have disabled.
4. The result maps each lowercase alias to the `Method` element and the alias as written.

CakePHP 2 uses the same class with the other rules: parents up to and excluding `\ModelBehavior`,
skip `_`-prefixed names and the nine callbacks, no finders, no options, and the first parameter
dropped in the presentation.

The maps are cached per class and option set on `PhpFilesModificationTracker`, as the entity
accessor tables are.

Two helpers the features share: `behaviorMethodsOnTables(project, tableFqns)` returns the
mixed-in methods of every attached behavior minus the names any class in the table chain
declares (the shadowing rule), and `behaviorFindersOnTables()` does the same for finders minus
the table chain's own `find*` methods. Both carry the alias and the behavior class, for type
text and navigation.

### 3. Finders

`CustomFinderCompletionContributor` adds the behavior finders beside the table's own, with the
behavior's short name as tail text. `CustomFinderGotoDeclarationHandler` adds the behavior's
method for a name the tables do not declare. `TableLocatorTypeProvider` already types
`find('children')` as the query, so the type side needs nothing.

### 4. Mixed-in methods on the table

- **Completion.** `BehaviorMethodCompletionContributor` on the name leaf of a `MethodReference`,
  mirroring `ControllerModelOrTableCompletionContributor`'s pattern: resolve the receiver's
  tables as `queryReceiverTables()` does (so `$this->Articles`, `fetchTable()`, a typed
  parameter, `$this` in a table, and an association hop all work), and add one element per
  mixed-in method, built with `LookupElementBuilder.create(method, alias)` so PhpStorm shows the
  parameters, the return type and the documentation, with the behavior's short name as tail
  text. On CakePHP 2 the elements are built by hand with the parameters after the `Model` one.
  Behaviors have no convention list, so there is nothing to hide; a table whose chain attaches
  none gets nothing added.
- **Type.** `BehaviorMethodTypeProvider`, keyed `M`, `getType()` null, intercepting off, an
  empty `getBySignature()`. `complete()` parses `#M<receiver>.<method>`, resolves the receiver
  to classes the way `ConfiguredMemberFieldTypeProvider` does (a `#C`/`#S` class directly,
  anything else through `lookupCompleteType`), and answers only when every receiver class is a
  table (name ending in `Table` and a subclass of `\Cake\ORM\Table`, or a CakePHP 2 model), no
  class in any chain declares the method, and every receiver has a mixed-in method of that
  name. The answer is the union of the methods' stated return types, falling back to inference
  when none is stated, as the entity provider does. Anything else returns null and leaves native
  resolution alone. A receiver that completes only to `\Cake\ORM\Table` has no loads and is
  left alone; the platform check below showed such a signature completes to `?`, which PhpStorm
  filters, not to `mixed`, so nothing dilutes the answer.
- **Navigation.** `BehaviorMethodGotoDeclarationHandler` on the name leaf, targets the mixed-in
  method on every attached behavior that has it, per class in the receiver union as the entity
  handler does.
- **Inspection.** If the session 1 check shows the weak warning on `$table->touch()`, a
  `lang.inspectionSuppressor` for `PhpUndefinedMethodInspection` suppresses it for a name that
  is a mixed-in method on any table class in the receiver, the way
  `EntityVirtualFieldInspectionSuppressor` does for fields.

### 5. The behavior instance

- **Type of `getBehavior('Tree')`.** A `BehaviorInstanceTypeProvider` on its own key: in
  `getType()`, a `MethodReference` named `getBehavior` whose first argument is a string literal
  and whose receiver type is a table class, a configured-field signature or an association
  signature returns `#<key>.<alias>.<receiverType>`, as `TableLocatorTypeProvider` does for
  `get`; `complete()` resolves the receiver to tables and the alias through `behaviors()` to the
  class list. `behaviors()->get('Tree')` is handled by the same provider when its receiver is
  the `behaviors()` call on a table. The declared `Behavior` return type is replaced, not
  joined, because `getBehavior()` returns exactly the loaded instance.
- **Strings.** Completion of the attached aliases inside the first argument of `getBehavior()`,
  `hasBehavior()` and `removeBehavior()`, and navigation from that string to the behavior class.
  Navigation from the first argument of `addBehavior()` and from its `className` value comes from
  `ControllerComponentsGotoDeclarationHandler` once `addbehavior` is in `methodCallSources`;
  `addBehaviors()` keys are left without navigation, as `addAssociations()` leaves are.
  Completion of behavior names inside `addBehavior('<caret>')` from the convention candidates
  (every `*Behavior` subclass in the app, the plugins and the core) is cheap and included.

### 6. CakePHP 4 and 3

Fixture and test copies, with `getTable()` in the cake3 behaviors and `Query` in place of
`SelectQuery`. `addHelper`-style version differences do not arise; the only one is that 3.5 and
earlier lack `getBehavior()`, and 3.10.5 is what the suite attaches.

### 7. Associations added by behaviors

Two paths in `ConfiguredMemberService` read a table chain's association loads today, and both
must see the behaviors' loads: `resolveChain()`, behind `memberType()` and `loadedMembers()`
(so `$this->Photos->Tags`, `contain('Tags')` and the query-method navigation), and
`associationLoads()`, which walks `classChain()` and reads the index on its own with its own
nearest-initialize rule, and which `EntityAssociations.propertiesOf()` uses for `$photo->tags`.
Extending only the first would leave the entity property untyped.

So the step first pulls the chain reading into one private function, `chainConfigurations()`,
that returns the per-class `ClassConfiguration` list for a chain, and makes both paths use it.
It then adds the behaviors' loads there: for each class in the chain, resolve its `BEHAVIOR`
loads with the rule above, and for each attached behavior class, read the chain of that class
for `ASSOCIATION` loads flagged `viaTable`, inserted into the configuration list at the position
of the attaching table class. A behavior's load in its own `initialize()` is an initialize load
when the `addBehavior()` is one, and an other load otherwise, since the behavior's `initialize()`
runs when the behavior is attached. `associationLoads()` reports the attaching table class as
the class that configures such a load, since that is the file a `TableAssociationLoad` consumer
navigates to; the behavior's own offset stays on the `MemberLoad`. With both paths on one
reader, test 15 covers the association property, the association field and `contain()` from
the same fixture.

### 8. CakePHP 2

The `$actsAs` property source, the `Behaviors->load()` source, the global class convention, the
CakePHP 2 member rules in `BehaviorMembers`, and the model receiver (`isCakeTwoModel()`) in the
completion contributor, the type provider and the goto handler. `CakeTwoChainResolver` already
knows how property arrays merge down a model chain; `actsAs` joins the kinds it handles. The
`Model` first parameter is dropped from the lookup presentation.

## Out of scope

- **`$mapMethods` on CakePHP 2** and a behavior's own `__call`: regex and runtime dispatch,
  nothing to read.
- **Behaviors attached through the registry on 3 to 5** (`$this->behaviors()->load()`,
  `->set()`): rare, and the registry receiver is not `$this`. The 2.x `Behaviors->load()` form is
  supported because it is the only runtime form there.
- **The translate strategies' associations** on 4 and 5: the calls are on a strategy's `$this->table`,
  with no static link to the behavior.
- **An `implementedEvents()` override**: the reflection step uses the default thirteen names,
  so a behavior that overrides the method and lists another public method as a callback has
  that method offered. It is the same name set on every version this plan covers.
- **The duplicate-method conflict** between two attached behaviors: both are offered, nothing
  is reported.
- **Event callbacks and `$this->_table` typing inside a behavior**: `_table` is declared
  `Table`, so completion on it is the base class; narrowing it to the attaching tables would need
  a reverse index and is a separate idea.

## Fixtures

Under `cake5/src5/Model/Behavior/` (new directory), then the cake4 and cake3 copies:

- `SluggableBehavior`: `public function slug(EntityInterface $entity): string`,
  `public function findSlugged(SelectQuery $query, string $slug): SelectQuery`,
  `protected function _normalize(string $value): string`, `public function beforeSave(...)`,
  `public function initialize(array $config): void`. The reflection case.
- `ChildSluggableBehavior extends SluggableBehavior` with `public function reslug(...)`. The
  inherited case.
- `AuditBehavior`: `$_defaultConfig = ['implementedMethods' => ['audit' => 'writeAudit'],
  'implementedFinders' => ['audited' => 'findAudited']]`, with `writeAudit()`, `findAudited()`
  and a `public function notMixedIn(): void`. The configured case.
- `TaggableBehavior`: `initialize()` calls `$this->_table->belongsToMany('Tags')` and
  `$this->table()->hasMany('TagLinks')`. The association case.
- `vendor/test/test_plugin/src/Model/Behavior/SluggableBehavior.php` with
  `public function pluginSlug(): string`. The plugin case.

Tables, under `cake5/src5/Model/Table/`, leaving `MoviesTable`, `UsersTable` and the entity
feature's tables untouched since their tests assert exact lists:

- `CounterCacheBehavior` with `public function counted(): string`: an app behavior named like
  the core one, so first-match resolution has something to choose between.
- `PagesTable`: `initialize()` calls `addBehavior('Timestamp')`, `addBehavior('Sluggable')`,
  `addBehavior('CounterCache')`,
  `addBehavior('Audit', ['implementedMethods' => ['trail' => 'writeAudit']])` and declares its
  own `public function findSlugged(...)` and `public function touch(...)`, so `slugged` and
  `touch` are the table's, `slug`, `trail`, `timestamp` and the `audited` finder are mixed in,
  `audit` is not (the `trail` alias replaced it, by the merge rule), and `notMixedIn` is absent. A `public function enableTree(): void` calls `addBehavior('Tree')`
  outside `initialize()`, so `recover` and the `children` finder are offered as a union.
- `ChildPagesTable extends PagesTable` with no `initialize()`.
- `PluginPagesTable`: `addBehaviors(['TestPlugin.Sluggable', 'Audit' => ['implementedMethods' => []]])`,
  so `pluginSlug` comes from the plugin class only, `slug` too, and no `Audit` method is offered.
- `PhotosTable` with `addBehavior('Taggable')`, plus `TagsTable`, `TagLinksTable`, and the
  `Photo` and `Tag` entities, for step 7.

Under `cake2/app/Model/Behavior/`: `SluggableBehavior` with `public function slug(Model $model,
$title)`, `protected function _normalize()`, `public function beforeSave(Model $model, $options = array())`;
and `cake2/app/Model/Page.php` with `public $actsAs = array('Containable', 'Sluggable', 'Audit' => array('on' => true))`
and a method that calls `$this->Behaviors->load('Tree')`. The existing `Movie`, `Artist` and
`Director` models stay as they are.

The `test` task attaches the pinned release, so `Timestamp`, `Tree` and `Translate` resolve to
the real `Cake\ORM\Behavior` classes and their real `$_defaultConfig` maps.

## Tests

`cake5/BehaviorFinderTest`, `cake5/BehaviorMethodTest`, `cake5/BehaviorInstanceTest` and
`cake5/BehaviorAssociationTest`, with exact types asserted through the `fieldTypeAtCaret()`
pattern adapted to method references, then the cake4 and cake3 copies, `cake2/BehaviorTest`,
and the indexer cases in `ConfiguredMemberIndexerTest`. Fixtures have more than one candidate
in every completion test, so the popup appears and `lookupElementStrings` is not null.

Finders, with `$this->Pages` in a controller unless stated:

1. `find('<caret>')` offers `slugged`, `audited`, `children`, `path` and `treeList`, each with
   the behavior's short name as tail text, and no `notMixedIn`-derived entry.
2. `find('audited')` navigates to `AuditBehavior::findAudited()`; `find('slugged')` navigates to
   `PagesTable::findSlugged()` and not to the behavior's.
3. `$this->Pages->find()->find('<caret>')` offers the same on a query receiver.
4. `find('<caret>')` on `ChildPagesTable` offers the parent's behaviors' finders; on
   `MoviesTable` it offers `ownedBy` and nothing from behaviors.

Methods:

5. `$this->Pages-><caret>` offers `slug`, `trail`, `timestamp`, `counted`, `recover`, `moveUp`
   and the rest of `Tree`'s map, not `audit`, not `notMixedIn`, not `writeAudit` under its own
   name, not `updateCounterCache` (the app `CounterCacheBehavior` shadows the core one), not
   `touch` from the behavior (the table's own `touch` is PhpStorm's), not `_normalize`, not
   `beforeSave`, not `getConfig`.
6. `$this->PluginPages-><caret>` offers `pluginSlug` and `slug` from the plugin class and no
   `Audit` method.
7. `$this->Pages->slug($entity)` has type exactly `string`; `$this->Pages->trail()` has
   `writeAudit()`'s declared type; `$this->Pages->recover()` has type `void`;
   `$this->fetchTable('Pages')->slug($e)` and a `PagesTable $table` parameter's `$table->slug($e)`
   give `string` too; inside `PagesTable`, `$this->slug($e)` gives `string`.
8. `$this->Pages->touch()` keeps the table's own declared type, proving the provider stays out.
9. `$this->Movies->slug($e)` is not typed by the provider and stays `mixed`.
10. Goto declaration on `slug` reaches `SluggableBehavior::slug()`; on `trail` reaches
    `AuditBehavior::writeAudit()`; on `reslug` in a table attaching `ChildSluggable` reaches the
    child class's method and `slug` reaches the parent's.
11. The weak warning "Method 'slug' not found in ..." is absent on `$table->slug($e)` for a
    `PagesTable $table` parameter and on `$this->slug($e)` inside `PagesTable`, and present on
    `$table->notAMethod()` on the same receivers. (`$this->Pages->slug($e)` gets no warning
    either way, so it is not a test receiver.)

Instance:

12. `$this->Pages->getBehavior('Sluggable')->` completes `slug` and `findSlugged` and the type
    is exactly `\App\Model\Behavior\SluggableBehavior`; `getBehavior('Timestamp')` is exactly
    `\Cake\ORM\Behavior\TimestampBehavior`; `getBehavior('CounterCache')` is exactly the app
    class and not the core one; `$this->PluginPages->getBehavior('Sluggable')` is the plugin
    class.
13. `getBehavior('<caret>')` offers `Timestamp`, `Sluggable`, `Audit` and `Tree`; so do
    `hasBehavior()` and `removeBehavior()`. Goto declaration on the string reaches the class.
14. `addBehavior('<caret>')` in a table offers the `*Behavior` subclasses by short name, and
    goto declaration on `addBehavior('Sluggable')` reaches the app class, on
    `addBehaviors(['TestPlugin.Sluggable'])` nothing.

Associations:

15. `$this->Photos->Tags` has type `\App\Model\Table\TagsTable`; `contain('<caret>')` on
    `$this->Photos->find()` offers `Tags` and `TagLinks`; `$photo->tags` is `\App\Model\Entity\Tag[]`.
16. A table that attaches `Taggable` in a method other than `initialize()` still offers `Tags`
    in `contain()` and resolves `$this->Alias->Tags`, as an other load.

Indexer, in `ConfiguredMemberIndexerTest`:

17. `addBehavior()` plain, with `className`, plugin-dotted, with `implementedMethods` as a
    literal, as `[]`, as a variable (unread), outside `initialize()`; `addBehaviors()` plain
    and keyed entries; `$actsAs` entries; `$this->Behaviors->load()`; `$this->_table->belongsTo()`,
    `$this->table()->hasMany()` and `$this->getTable()->hasOne()` recorded with `viaTable`;
    `$other->belongsTo()` not recorded.

CakePHP 2, in `cake2/BehaviorTest`:

18. `$this->Page-><caret>` in a controller offers `slug` with the parameters after `Model`, not
    `_normalize` and not `beforeSave`; `$this->Page->slug('x')` has `slug()`'s type; goto
    reaches `SluggableBehavior::slug()`; the `Behaviors->load('Tree')` behavior's methods are
    offered as a union.

The full suite runs once before the PR is opened; after that only the targeted tests run
locally and CI runs the suite.

## Plan

1. **Assumption tests** on 2, 3, 4 and 5, with their fixtures and catalogue rows. Fold what
   they correct into the sections above.
2. **Platform checks**, recorded in the progress log: whether an `M`-keyed non-intercepting
   provider coexists with the bundled intercepting ones without the duplicate-provider error,
   the receiver encodings for the method references in tests 7 and 9, and whether the
   undefined-method weak warning fires on a `Table` subclass.
3. **Step 1, the index**: `BEHAVIOR` kind, the `addBehavior()`, `addBehaviors()` and `viaTable`
   sources, the option fields, the externalizer, the version bump, the cake5 fixtures, test 17.
4. **Step 2, the service**: `behaviors()`, `BehaviorMembers`, the two helpers, the convention
   candidates.
5. **Step 3, finders**: tests 1 to 4.
6. **Step 4, methods**: completion, the type provider, navigation, the suppressor; tests 5
   to 11.
7. **Step 5, the instance**: tests 12 to 14.
8. **Step 6, cake4 and cake3** copies of the fixtures and tests so far.
9. **Step 7, associations**: tests 15 and 16, on all three versions.
10. **Step 8, CakePHP 2**: test 18.
11. **Full suite**, the README line and the changelog entry.

Commit after each step. Steps 5, 6 and 7 are independent once step 4 lands.

## Decisions

- **No strikeout for the deprecated form on 5.3.** The plugin knows the major version only, so
  it cannot tell 5.2 from 5.3; the mixed-in methods are offered the same way on every version,
  and the `getBehavior()` support in step 5 gives the recommended form equal footing. If minor
  version detection arrives, `withStrikeoutness` on the mixed-in elements is a one-line change.
- **An unread `implementedMethods` option offers nothing** rather than falling back to
  reflection, because the option exists to restrict or rename, and offering the unrestricted
  set would show methods the author turned off. The same applies to an unreadable
  `$_defaultConfig` map.
- **Shadowing is by name against the whole table chain**, including `\Cake\ORM\Table`'s own
  methods, since `__call` never runs for a method the class has. A behavior method that happens
  to share a name with a `Table` method is therefore never offered, which matches runtime.
- **The method map is keyed lowercase**, as the registry keys it, so `$table->Slug()` resolves
  too; completion offers the alias as written in the map.
- **Behaviors attached outside `initialize()` are unioned**, following the configured-member
  rule. For behaviors there is no convention list to fall back to, so the union only adds.
- **A behavior name resolves to one class, first match**, unlike the other kinds, because the
  framework loads one class and the plugin has the assumption test to say which. Returning
  every existing candidate would show an app behavior's methods beside the core behavior's it
  shadows.

## Open questions

- **Cost of `complete()` on every method call.** Every `#M` signature reaches the provider. The
  cheap rejections come first (a receiver class whose name does not end in `Table` or `Model`
  conventions, a method the class declares), and the behavior resolution is cached, but there is
  no timing run planned; `ViewVariablePerformanceTest` is the model if one is needed.
- **`$_defaultConfig` on a parent behavior** with the child overriding the property: the nearest
  declaration wins, which matches PHP property redeclaration. A child that merges with
  `parent::$_defaultConfig + [...]` in its constructor is not readable and gets the reflection
  map; a note in the progress log if a real behavior does this.

## Implementation Progress

### Session #1

Plan step 1, the assumption tests, is done on all four versions: 20 tests each on 3, 4 and 5,
and 10 on 2, all passing, with the catalogue rows added. Two things the first drafts got wrong
were the tests' own doing, not the framework's: a `className` test that loaded the app and the
plugin `Sluggable` on one table tripped the duplicate-method rule that the next test pins, and
the CakePHP 2 app had no `AppModel`, so one was added beside its `AppController`. The CakePHP 2
test base's `expectException()` wrapper also asserts an empty message on PHPUnit 5.7, so the
missing-class cases use `try`/`catch`. The two framework corrections, the global CakePHP 2
class names and the merge rule, are recorded under the assumption tests.

**Plan step 2, the platform checks** (232.8660.205, read from `php.jar` with `javap` and run as a
throwaway probe test on the Cake 5 fixtures, not kept):

- **An `M`-keyed non-intercepting provider is safe.** `PhpIndexImpl.getDuplicatedProvidersErrorMessage()`
  groups the registered providers by key, but `getUniqueClassNames()` first drops every provider
  whose `interceptsNativeSignature()` is true, so only non-intercepting providers count toward
  "Duplicate type providers with key". The registered providers keyed `M` are
  `PhpGenericsMethodExtendedWithGenericTypeProvider`, `ProphecyProphesizedMethodCallTypeProvider`
  and `ProphecyRevealTypeProvider`, all intercepting (`PhpUnitMockBuilderTP` keys on lowercase
  `m`). So `BehaviorMethodTypeProvider` will be the only non-intercepting `M` provider, exactly as
  `ConfiguredMemberFieldTypeProvider` is the only non-intercepting `P` one beside the intercepting
  `PhpGenericsFieldExtendedWithGenericTypeProvider`.
- **The ambiguity branch.** In `doCompleteType`, after a non-intercepting provider returns a
  non-empty type, native resolution is skipped unless some other provider with the same key is
  intercepting (true for `M` and for `P`) *and* `PhpType.isAmbiguous()` holds, which means the
  result is empty, contains `?`, or intersects `mixed`. So a behavior method declared `: mixed`
  gets native resolution run beside the answer, which for a method the class lacks adds only `?`
  (see the next point). Every other return type is taken as is. The provider must therefore
  never answer with `?` or `mixed` on purpose; when it has nothing better it returns null.
- **No `mixed` from `__call`.** Unlike fields, where native resolution falls back to `__get`'s
  type, a `#M` signature for a method the class lacks completes to `?` (the branch after
  `getBySignature()` finds nothing adds the unknown marker, and there is no `__call` fallback).
  The probe's completed type for `touch` on every receiver was `?`. So the entity work's
  unresolvable-signature trick is not needed here: the signatures the provider does not answer
  contribute `?`, which PhpStorm filters, not `mixed`.
- **Receiver encodings** for a method `touch` that `MoviesTable` does not declare (raw
  `MethodReference.type`; each also appears wrapped in `#π(...)`, which has its own key and is
  completed by PhpStorm recursively, reaching the provider with the inner signature):

  | Receiver | Signatures with key `M` |
  |---|---|
  | `MoviesTable $table`, `$this` in the table | `#M#C\App\Model\Table\MoviesTable.touch` (and `#M#S...` for `$this`) |
  | `$this->Movies` in a controller | `#M#P#C\...\MovieController.Movies.touch`, the `#S` form, `#M#π(#P#C...Movies).touch`, and our `#M#茒c\...\MovieController茒Movies.touch` |
  | `$this->fetchTable('Movies')` | `#M#C\...\MoviesTable.touch` (from `TableLocatorTypeProvider`), `#M#M#C\...\MovieController.fetchTable.touch`, `#M#π(#M#C...fetchTable)("Movies").touch` |
  | `$this->getTableLocator()->get('Movies')` | `#M#C\...\MoviesTable.touch`, `#M#M#M#C...getTableLocator.get.touch` and the `#π` forms |
  | `$this->Reviews->Authors` | `#M#P#P#C...Reviews.Authors.touch`, our `#M#茒Reviews茒Authors.touch`, `#M#茗.v2.Authors.(...)茁.touch` and more |
  | `$this->Movies->touch()->other()` | `#M#M#P#C...Movies.touch.other`: the chained call's receiver is itself an `#M` signature the provider answers first |

  So `complete()` sees the receiver as a class (`#C`/`#S`) only for typed parameters and `$this`;
  every other shape needs `lookupCompleteType` on the receiver part, which is what
  `ConfiguredMemberFieldTypeProvider.resolveReceiverClasses()` does, and what the plan's step 4
  says. The receiver part can contain dots (`#M#P#P#C...Reviews.Authors.touch`), so the method
  name is the part after the *last* dot, as `parseFieldSignature()` already assumes.
- **The undefined-method inspection applies, on some receivers.** `PhpUndefinedMethodInspection`
  reports `$table->touch()` on a `MoviesTable $table` parameter and `$this->touch()` inside the
  table as a *weak warning* "Method 'touch' not found in \App\Model\Table\MoviesTable" (the
  `__call` on `Table` lowers it from the warning a class without `__call` gets, which the
  controller's `$this->notAControllerMethod()` showed). It reports nothing on
  `$this->Movies->touch()`, whose receiver type comes from a provider. So scope item 4's
  suppressor stays, keyed on that description, and test 11 should use a typed parameter and
  `$this` inside a table as its receivers, since the `$this->Movies` form is silent either way.

**Review of the plan** (after step 2): two gaps were found and folded into the sections above.
Behavior class resolution is first match of app then core, not every existing candidate, with
no plugin candidates for a bare name; and step 7 must extend `associationLoads()` as well as
`resolveChain()`, since the entity association properties read the chain through the former,
so both now share one chain reader.

