# Entity virtual fields

## Context

Feature 1 in `features/ideas.md`. A CakePHP 3, 4 or 5 entity exposes a virtual field by
declaring an accessor: `protected function _getFullName()` makes `$user->full_name` work. The
plugin knows nothing about accessors today. In a real project, `$user->full_name` resolves
through `Entity::__get`, so PhpStorm types it `mixed`, offers nothing for it in completion, and
cannot navigate from the field to the accessor. Developers paper over this with hand-written
`@property` tags on each entity, which is what `cakephp-ide-helper` generates for them.

CakePHP 2 has nothing comparable. Its `$virtualFields` are SQL expressions on a model, so
everything here is gated on `settings.cake3Enabled`.

## How CakePHP resolves a virtual field

From `Cake\Datasource\EntityTrait`, checked against the vendored 3.10.5, 4.6.5 and 5.4.3
sources (`src/test/php/cake*/vendor/cakephp/cakephp`):

- `__get($field)` calls `get()`, and `__set($field, $value)` calls `set()`. Both ask
  `static::_accessor($field, 'get' | 'set')` for a method name and call it when one exists.
  `__isset()` and `has()` go through `get()` as well, so a virtual field counts as present
  when its getter returns a non-null value.
- `_accessor()` scans `get_class_methods($class)` once per concrete class. Every method whose
  name starts with `_get` or `_set` (the three characters after the underscore decide) is
  registered under three keys built from `$field = lcfirst(substr($method, 4))`:
  `Inflector::underscore($field)`, `$field` itself, and `ucfirst($field)`. So `_getFullName()`
  answers `full_name`, `fullName` and `FullName`. Only the snake_case spelling is idiomatic.
- `get_class_methods()` runs inside the class, so protected and private methods count, as do
  methods inherited from a parent entity and methods brought in by a trait. The convention is
  `protected`.
- `Cake\ORM\Entity` itself is skipped (`static::class === self::class` returns no accessor),
  and `EntityTrait` declares no `_get*` or `_set*` methods of its own, so there is nothing to
  filter out from the framework side.
- The getter receives the stored value for the same field name, or `null` when there is none,
  and its return value is the field's value. The virtual field's type is therefore the getter's
  return type: declared, from `@return`, or whatever PhpStorm infers from the body. The setter
  receives the assigned value and returns what gets stored.
- `$_virtual` only affects `toArray()`, JSON output and `visibleFields()`. An accessor works
  without it, so the plugin does not require it and does not read it for types.
- `Inflector::underscore()` is `preg_replace('/(?<=\w)([A-Z])/', '_\1')` then `mb_strtolower`.
  For a method-name suffix (letters, digits, underscores) this matches the plugin's existing
  `camelCaseToUnderscore()`: `fullName` to `full_name`, `uRLSlug` to `u_r_l_slug`, `name2`
  stays `name2`. A `StringsTest` case pins the agreement.

## What PhpStorm does today

- **The magic `__get` resolves the field.** `EntityTrait::__get` is declared `@return mixed` on
  3.x and 4.x and `: mixed` on 5.x, so `$user->full_name` has type `mixed` in a real project.
  PhpStorm's own type for the reference is the field signature `#P<receiver>.full_name`, and
  resolving it falls back to `__get` when `findFieldsByName` finds nothing. That is the
  mechanism `fix-magic-get-type-dilution` documents, and the consequence is the same here: a
  `PhpTypeProvider4` on our own key can only add a type next to `mixed`. The precise type has
  to come through the native `#P` signature, the way `ConfiguredMemberFieldTypeProvider` does
  it, so that `doCompleteType` returns before the native fallback.
- **The stubs have no `Entity` at all.** `cake5/vendor/cakephp.php`, and the cake4 and cake3
  stubs, declare no `Cake\ORM\Entity`, `Cake\Datasource\EntityTrait` or `EntityInterface`, so
  the fixture entities (`Movie extends Entity`) extend an unresolved class in stub mode. Stub
  mode would therefore never see the `mixed` dilution. The stubs need an `Entity` that declares
  `__get` and `__set` the way each version does, so both modes exercise the same path. This
  was the lesson of the dilution work.
- **A declared field wins.** When an entity declares the property, or carries a `@property`
  tag for it, `findFieldsByName` succeeds and PhpStorm's type is exact. The plugin should leave
  those fields alone, as the configured-member provider leaves declared fields alone.

## Scope

In the order to implement them, each with cake5 tests first and then cake4 and cake3:

### 1. The type of `$entity->virtual_field`

A `#P`-keyed provider that answers for accessor-backed fields on entity classes. The
configured-member provider already keys on `PhpTypeSignatureKey.FIELD`, and the dilution log
records that two providers sharing a key fall into an ambiguity branch in `doCompleteType`.
The two domains are disjoint by construction, since `parseFieldSignature` rejects a field whose
first character is not uppercase and every idiomatic virtual field starts lowercase, but
whether "one answers, the other returns `null`" counts as ambiguous has to be read from the
bytecode first. The outcome decides the shape:

- If a `null` from one provider is not ambiguity, add `EntityVirtualFieldTypeProvider` in a new
  `entity` package, keyed `P`, with `getType()` returning `null`, the default
  `interceptsNativeSignature()` and an empty `getBySignature()`, as the configured-member
  provider has.
- Otherwise, one `P`-keyed provider dispatches on the field name's first character to the
  configured-member resolver and the entity resolver.

`complete()` for an entity field, in this order, cheapest first:

1. **Parse** `#P<receiver>.<field>`. The field name must be a plain identifier starting with a
   lowercase letter or underscore; both `full_name` and `fullName` are accepted since the
   framework accepts both. `FullName` is left to native resolution.
2. **Reject receivers by name** before any index access. A `#C`/`#S` receiver whose class name
   ends in `Controller`, `Table`, `Component`, `Helper`, `View`, `Cell` or `Behavior` is not an
   entity. Every other `#C`/`#S` receiver, and every non-class receiver (a method call result
   such as `$this->Movies->get($id)->full_name`, or a variable typed by one of our providers in
   a template), goes on. The receiver encodings in those cases are unknown and are settled by
   the same real-mode probe the dilution work used, recorded in the progress log.
3. **Resolve the receiver** to classes with `lookupCompleteType` and keep the ones that are
   entities: a subclass of `\Cake\ORM\Entity`, or a class implementing
   `\Cake\Datasource\EntityInterface`, which covers an entity that uses `EntityTrait`
   directly. Plugin entities (`TestPlugin\Model\Entity\PluginItem`) qualify by hierarchy, not
   by namespace.
4. **Skip declared fields.** If any receiver class declares the field (`findFieldByName`, which
   walks the hierarchy and sees `@property` tags), return `null` so PhpStorm's exact type
   stands. As in the configured-member provider, one class without an answer leaves them all
   to native resolution.
5. **Find the getter.** Build the accessor table for the class the way `_accessor()` does:
   walk the class, its traits and its parents up to but excluding `Cake\ORM\Entity`, take every
   method named `_get…` or `_set…`, and key it under the three spellings. The table is per
   class and cached on `PhpFilesModificationTracker`, the project's tracker for PHP changes.
   Nearest declaration wins, as `get_class_methods()` reports the overriding method.
6. **Answer with the getter's type.** The declared or `@return` type when there is one, else
   the inferred type completed through `lookupCompleteType`. A getter that reads other virtual
   fields of the same entity recurses through `doCompleteType`'s guard, which the dilution work
   confirmed is safe for index lookups in `complete()`.
7. **No getter, no answer.** A setter-only field, or a field with no accessor at all, returns
   `null` and keeps today's `mixed`.

### 2. Completion of virtual fields

An `EntityVirtualFieldCompletionContributor` on the `LeafPsiElement` under a `FieldReference`,
mirroring `ControllerModelOrTableCompletionContributor`. It resolves the receiver with
`lookupCompleteType`, keeps the entity classes, and offers one lookup element per accessor
field in snake_case only: `full_name`, not `fullName` or `FullName`. The type text is the
getter's type (so `string` for `_getFullName(): string`), with the setter-only fields offered
too since they are assignable. Fields the class declares are skipped so a `@property`-tagged
field is not listed twice. The lookup element's object is the getter (or setter) method, so
PhpStorm's own icon and documentation popup work.

Inside the entity class itself, `$this->full_name` completes the same way, since the receiver
is `#C` of the entity.

### 3. Navigation from the field to the accessor

An `EntityVirtualFieldGotoDeclarationHandler`, like `ControllerFieldGotoDeclarationHandler`,
on the name leaf of a `FieldReference` whose receiver resolves to an entity. The targets are
the `_get…` method and the `_set…` method when both exist, so the chooser shows both. On a
plain read, PhpStorm already resolves the reference to `__get` when the class has one; the
handler's targets are added alongside, and the test asserts the accessor is among them.

### 4. The undefined-field inspection

On a class with `__get`, PhpStorm's `PhpUndefinedFieldInspection` reports a field it cannot
find with the weak warning "Field accessed via magic method" rather than an error. Whether the
highlight appears for `$movie->full_title` in the test platform is checked in the first
session. If it does, a `lang.inspectionSuppressor` for that tool ID, like
`UndefinedViewVariableInspectionSuppressor`, suppresses it for accessor-backed fields on entity
receivers only. If the platform already reports nothing, this step is dropped and the log says
so.

### 5. Strings inside `$_virtual` (optional)

`protected array $_virtual = ['full_name'];` lists accessor fields by name. Completion of the
snake_case accessor names inside that array, and goto declaration from the string to the
getter, are small once steps 1 to 3 exist. `$_hidden` and `$_accessible` take column names
too, which the plugin cannot know, so they are not covered. This step ships only if the earlier
ones land cleanly.

## Out of scope

- **Column fields.** Columns come from the database schema. Bake writes `@property` tags for
  them, and PhpStorm handles those natively.
- **Association properties** (`$movie->articles`) are typed by the Table's associations and are
  a separate feature. See
  [Interaction with configured associations in query methods](#interaction-with-configured-associations-in-query-methods)
  for what that feature would build on.
- **`$entity->get('full_name')` and `set()`** calls with string arguments. Possible later via
  the same accessor table, but the property form is what the idea asks for.
- **CakePHP 2.** No accessors exist.

## Fixtures

- **Stubs.** Add to `cake5/vendor/cakephp.php`, `cake4/vendor/cakephp.php` and
  `cake3/vendor/cakephp.php`: `Cake\Datasource\EntityInterface`, `Cake\Datasource\EntityTrait`
  with `__get` and `__set` declared as that version declares them (3.x `&__get($property)` with
  `@return mixed`; 4.x `&__get(string $field)` with `@return mixed`; 5.x
  `&__get(string $field): mixed`), and `Cake\ORM\Entity implements EntityInterface { use
  EntityTrait; }`.
- **Entities.** Extend `cake5/src5/Model/Entity/Movie.php` (then cake4 and cake3) with
  `protected function _getFullTitle(): string`, a `/** @return float */ _getRuntimeHours($value)`
  with no declared type, and a setter-only `_setTitle($title)`. Add a `MovieAccessorsTrait` and
  a `BaseMovie` parent that each contribute one accessor, to cover inherited and trait methods.
  Give `MoviesTable` a `protected function _getLabel()` so a non-entity receiver with an
  accessor-shaped method proves the receiver check. The existing tests that use
  `Movie.php` complete `someMovieMethod` and assert containment, not an exact list, so adding
  protected methods does not disturb them.
- **Real mode.** `./gradlew test -PcakeVendor=real` fetches the pinned releases into
  `build/cakephp-vendor/<major>`; the `src/test/php/*/vendor` directories are untracked and are
  not present in a fresh worktree. Every new test is written to pass in both modes, and the
  type tests are the ones that only mean something in real mode, where `__get` is in play.

## Tests

`cake5/EntityVirtualFieldTest`, then the cake4 and cake3 copies with `src4`/`src` paths and
`.ctp` templates on cake3. Exact types are asserted with the `fieldTypeAtCaret()` pattern from
`ConfiguredComponentTest`, which keeps the `\`-prefixed entries so a leaked `\mixed` fails the
assertion.

1. `$movie->full_title` in a controller action where `$movie` is a `Movie` parameter has type
   exactly `string`.
2. `$this->Movies->get($id)->full_title` has type `string` (a method-call receiver typed by
   `TableLocatorTypeProvider`).
3. In a template, `$movie->full_title` where the controller set `$movie` has type `string`
   (a view-variable receiver).
4. `$movie->runtime_hours` has type `float` from the `@return` tag; `$movie->fullTitle` also
   resolves to `string`.
5. An accessor inherited from the parent entity, and one from the trait, both type their fields.
6. A field the entity declares with `@property` keeps its declared type and is not duplicated in
   completion.
7. `$movie->title` with only `_setTitle()` keeps native resolution (type is whatever PhpStorm
   gives it; the assertion is only that the provider does not answer) and still completes.
8. Completion on `$movie-><caret>` offers `full_title` with type text `string`,
   `runtime_hours` with `float`, and `title`; it does not offer `fullTitle` or `FullTitle`.
   The fixture has more than one accessor, so the popup appears and `lookupElementStrings` is
   not `null`.
9. Completion on a `MoviesTable` receiver does not offer `label` despite `_getLabel()`.
10. Goto declaration on `full_title` reaches `_getFullTitle`; on the left side of
    `$movie->title = 'x'` it reaches `_setTitle`; on a field with both it offers both.
11. Inside `Movie`, `$this->full_title` has type `string`.
12. If step 4 applies: the undefined-field weak warning is absent on `$movie->full_title` and
    present on `$movie->not_a_field`.
13. `StringsTest`: `camelCaseToUnderscore` agrees with `Inflector::underscore` on
    `fullName`, `uRLSlug`, `name2` and `a`.

The full suite runs in both modes at the end, as the dilution work did (961 tests in each at
the time).

## Plan

1. **Bytecode check.** Read `PhpIndexImpl.doCompleteType` in the current PHP plugin for what
   happens when two providers share key `P` and one returns `null`. Decide between a second
   `P` provider and a dispatching one. Record the finding in the progress log.
2. **Stubs and fixtures** for cake5: the `Entity` stub and the `Movie` accessors. Confirm that
   `Movie extends Entity` now resolves in stub mode.
3. **Probe the receiver encodings** for tests 1, 2, 3 and 11 in real mode and record them, as
   the dilution log did for controllers and helpers.
4. **Step 1, the type provider,** with tests 1 to 7 and 11 on cake5.
5. **Step 2, completion,** with tests 8 and 9.
6. **Step 3, navigation,** with test 10.
7. **Step 4 check**, then the suppressor and test 12 or a note that it is unnecessary.
8. **Cake 4 and Cake 3**: stubs, fixtures and the test copies.
9. **Step 5** if time allows.
10. **Full suite in both modes.** Then add the feature to the README's supported-features list.

## Interaction with configured associations in query methods

[Configured associations in query methods](configured-associations-in-query-methods.md) is
planned on a sibling worktree at the same time. Its lookups (contain completion and
navigation, the query-builder wrapper) and this feature's (a `#P`-keyed provider, field
completion, field navigation) do not meet. The points of contact:

- **The stubs.** Both plans edit `cake5/vendor/cakephp.php` and the cake4 and cake3 stubs. That
  branch adds `matching()`, `notMatching()`, `innerJoinWith()` and `leftJoinWith()` inside the
  existing `SelectQuery` and `Query` classes. This one adds `EntityInterface`, `EntityTrait` and
  `Entity` as new classes. Add them in a block of their own, not inside or adjacent to the
  query classes, so the second branch to merge gets a clean or trivially resolved rebase.
- **`TableLocatorTypeProvider`.** Test 2 (`$this->Movies->get($id)->full_title`) relies on the
  `get` branch returning the entity class. That branch changes the `find` branch and the
  expression split in `complete()`, and commits to keeping the `get` path's behaviour and
  `GetTableLocatorTest` green, so merge order does not matter for this test.
- **Fixtures.** That branch edits `UsersTable` or adds a table fixture. This one edits
  `Movie.php` and adds `_getLabel()` to `MoviesTable`. Disjoint.
- **README.** Both append to the supported-features list. Expect a trivial adjacent-line
  conflict.

### Association properties, later

That branch also extends the configured-member index with what association properties on
entities need: `associationType` (one of the four, null for the CakePHP 2 HABTM join-model
load), `propertyName` and `hasUnreadPropertyName`, mirroring `className` and its unread flag.
Once it lands, association properties become a possible further step of this feature, built
from:

- An entity-to-table helper, the reverse of `tableToEntityClass()`, through the `Inflector`.
- An accessor on `ConfiguredMemberService` returning the `ASSOCIATION` loads of a receiver
  chain with the most-derived-wins rule of `resolveChain()`. `loadedMembers()` returns resolved
  types, not loads, so the new fields are not reachable through it.
- The property name: `propertyName` when present; nothing when `hasUnreadPropertyName`; else
  the alias underscored, singularized for `BELONGS_TO` and `HAS_ONE`.
- The type: the target table's entity through `tableToEntityClass()`, as an array for
  `HAS_MANY` and `BELONGS_TO_MANY`.
- The same `#P` provider, completion contributor and goto handler as the accessor fields, with
  the association load as the navigation target.

Not planned in this log. Recorded so the provider's receiver resolution (step 3 of
`complete()`) is written to hand back entity classes in a form a second resolver can reuse.

## Open questions

- **Cost of non-class receivers.** Every `#P` signature with a lowercase field name reaches
  step 2 of `complete()`, and the ones with a method-call receiver reach `lookupCompleteType`.
  The configured-member provider never pays this because uppercase field names are rare. If
  the probe or a timing run (the `ViewVariablePerformanceTest` style) shows a cost, the
  fallback is to answer only `#C`/`#S` receivers in the provider and let completion and
  navigation, which run on demand, handle the rest.
- **Accessor methods on an entity that are not meant as fields.** A helper such as
  `_getFormattedDate()` used internally becomes a virtual field in CakePHP too, so listing it is
  faithful to the framework. No filtering is planned.
- **Union receivers.** `$movie` typed `Movie|Article` answers only when every class resolves,
  consistent with the configured-member provider. A field present on one and not the other
  falls back to native resolution.
