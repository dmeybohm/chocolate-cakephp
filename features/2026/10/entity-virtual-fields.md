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

Two pieces merged to `main` after this plan was written, and `main` was merged in on
2026-10-31:

- **The stub vendor is gone** (#310, [remove-stub-vendor](remove-stub-vendor.md)). Every test
  attaches the pinned real CakePHP release, so there is no second mode to keep in step and no
  `Entity` stub to write.
- **Configured associations in query methods** (#311,
  [configured-associations-in-query-methods](configured-associations-in-query-methods.md))
  landed the index extension step 6 depends on, and the `UsersTable` and `ProfilesTable`
  fixtures it describes.

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
- **The tests see the real `Entity`.** Since #310 every test attaches the pinned real release
  (`build/cakephp-vendor/<major>`, fetched by the `test` task, or by `fetchCake<N>Vendor` for the
  IDE's JUnit runner), so the fixture entities (`Movie extends Entity`) resolve against the
  framework's `EntityTrait` and the `mixed` dilution shows in every run. An earlier draft of
  this plan added an `Entity` stub to the hand-written vendor files; those files no longer exist.
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

### 6. Association properties

Builds on [configured associations in query methods](configured-associations-in-query-methods.md),
merged as #311: `MemberLoad` carries `associationType`, `propertyName` and
`hasUnreadPropertyName` for each association load, `AssociationPathResolver.targetTables()`
resolves an alias to its target tables, and `ConfiguredMemberService.loadClasses()` resolves one
load. The CakePHP side below was checked against `Association::_propertyName()` and the
`BelongsTo` and `HasOne` overrides in the vendored 3.10.5 and 5.4.3 sources; the alias has its
plugin prefix removed with `pluginSplit()` before inflection, so `TestPlugin.Ratings` gives
`ratings`.

A table's associations become properties of its entity: `hasMany('Reviews')` on `MoviesTable`
makes `$movie->reviews` an array of `Review`, and `belongsTo('Authors', ['className' =>
'Users'])` on `ReviewsTable` makes `$review->author` a `User`. CakePHP derives the property
name from the alias and the association type (`Association::_propertyName()`:
`Inflector::underscore($alias)`, singularized first for `belongsTo` and `hasOne`), and the
`propertyName` option overrides it. The value comes through `__get` like any other field, so
PhpStorm types it `mixed` today for the same reason as an accessor field.

Resolution, inside the same `#P` provider and after the accessor check, since an accessor
named for the property (`_getAuthor()`) runs on the stored association value and its return
type is the field's type:

1. **Receiver entity classes**, from step 3 of the accessor resolution. The receiver must be
   an entity; the same name rejection applies first.
2. **Entity to table.** `App\Model\Entity\Review` to `App\Model\Table\ReviewsTable`: replace
   `Model\Entity` with `Model\Table`, pluralize the short name through the `Inflector`, append
   `Table`. Add `entityToTableClass()` next to `tableToEntityClass()` in `Strings.kt`, with a
   `StringsTest` case for an irregular plural (`Person` to `PeopleTable`) and a plugin
   namespace. When no such class exists, the entity has no associations and the provider
   returns null.
3. **The association loads** of that table's chain, through a new
   `ConfiguredMemberService.associationLoads(project, tableFqns)` that returns the resolved
   `MemberLoad` records with the most-derived-wins rule of `resolveChain()`, since
   `loadedMembers()` returns resolved types without the new fields. Loads with a null
   `associationType` (the CakePHP 2 HABTM join model) are skipped.
4. **The property name** of each load: `propertyName` when set; nothing for that load when
   `hasUnreadPropertyName`; else the alias through `camelCaseToUnderscore()`, singularized
   first for `BELONGS_TO` and `HAS_ONE`. The field in the signature is matched against that,
   so `$review->author` finds the `Authors` load.
5. **The type:** the target table's entity through `tableToEntityClass()` on each class the
   load resolves to (`ConfiguredMemberService.loadClasses()` gives them; `Authors` with
   `className` `Users` gives `UsersTable`, so `User`). When the entity class does not exist,
   `\Cake\ORM\Entity`, which is what the framework instantiates. As an array (`User[]`) for
   `HAS_MANY` and `BELONGS_TO_MANY`.
6. **Declared fields win**, as for accessors: a `@property` tag for the name leaves native
   resolution alone.

Completion offers one element per association property with the entity type as type text,
alongside the accessor fields, skipping declared fields. Navigation goes to the association
load call in the table (the `MemberLoad.offset` in the table's file, as the configured-member
navigation does for controller fields) and to the target entity class when it exists, so the
chooser shows both.

Nested access needs nothing extra: `$movie->reviews[0]->author` types the element as `Review`
through PhpStorm's own array handling and then resolves `author` on it.

## Out of scope

- **Column fields.** Columns come from the database schema. Bake writes `@property` tags for
  them, and PhpStorm handles those natively.
- **A table's `setEntityClass()` or `$_entityClass`** override. The target entity of an
  association is found by convention from the target table's name. An overridden entity class
  is rare and would need its own index record.
- **Whether an association property is loaded.** `$review->author` is only populated when the
  query contained `Authors`. The plugin offers and types it regardless, as `@property` tags
  from bake do.
- **`$entity->get('full_name')` and `set()`** calls with string arguments. Possible later via
  the same accessor table, but the property form is what the idea asks for.
- **CakePHP 2.** No accessors exist.

## Fixtures

- **Entities.** Extend `cake5/src5/Model/Entity/Movie.php` (then cake4 and cake3) with
  `protected function _getFullTitle(): string`, a `/** @return float */ _getRuntimeHours($value)`
  with no declared type, and a setter-only `_setTitle($title)`. Add a `MovieAccessorsTrait` and
  a `BaseMovie` parent that each contribute one accessor, to cover inherited and trait methods.
  Give `MoviesTable` a `protected function _getLabel()` so a non-entity receiver with an
  accessor-shaped method proves the receiver check. The existing tests that use
  `Movie.php` complete `someMovieMethod` and assert containment, not an exact list, so adding
  protected methods does not disturb them.
- **Association entities.** `ReviewsTable` already configures `Movies`, `Authors` (className
  `Users`), `Editors` (className `Users`, through `addAssociations()`), `Comments` and
  `TestPlugin.Ratings`. Add the entities those resolve to and that are missing:
  `cake5/src5/Model/Entity/Review.php`, `User.php`, `Profile.php`, and the plugin's
  `vendor/test/test_plugin/src/Model/Entity/Rating.php`. Leave `Comments` without a `Comment.php`
  to cover the `\Cake\ORM\Entity` fallback. `UsersTable` arrived with #311 as
  `hasMany('Reviews')` plus `hasOne('Profiles')`, and `ContainCompletionTest` asserts exactly
  that pair for nested completion, so it gets no new association. The `propertyName` cases go
  on a new `PostsTable` with a `Post` entity, which no other test loads:
  `hasMany('Reviews', ['propertyName' => 'written'])` and
  `hasMany('Comments', ['propertyName' => self::REMARKS])` with the constant declared on the
  class. Then cake4 and cake3 copies; the three versions' table fixtures gained the same
  `UsersTable` and `ProfilesTable` in #311.
- **Framework.** The `test` task fetches the pinned releases into
  `build/cakephp-vendor/<major>`. A fresh worktree has none until it runs, or until
  `fetchCake5Vendor` is run for the IDE's JUnit runner.

## Tests

`cake5/EntityVirtualFieldTest`, then the cake4 and cake3 copies with `src4`/`src` paths and
`.ctp` templates on cake3. Exact types are asserted with the `fieldTypeAtCaret()` pattern the
`Configured*` tests use, which keeps the `\`-prefixed entries so a leaked `\mixed` fails the
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

Association properties, in `cake5/EntityAssociationFieldTest`, with `$review` a `Review`
parameter unless stated:

14. `$review->movie` has type exactly `\App\Model\Entity\Movie` (`belongsTo`, singularized).
15. `$review->author` and `$review->editor` have type `\App\Model\Entity\User`: the alias is
    not the class, and the `addAssociations()` record works like a direct call.
16. `$review->comments` has type `\Cake\ORM\Entity[]` when no `Comment` entity exists, and
    `$review->ratings` has type `\TestPlugin\Model\Entity\Rating[]` (plugin target).
17. `$user->reviews` has type `\App\Model\Entity\Review[]` and `$user->profile` has
    `\App\Model\Entity\Profile` (`hasOne`, singularized). On `Post`, `$post->written` has type
    `\App\Model\Entity\Review[]` (the `propertyName` option) and `$post->reviews` is not typed
    or completed; `$post->comments` is not typed or completed either, because the option is
    unreadable, and neither is `$post->remarks`.
18. `$this->Reviews->get($id)->author` has type `User` (a method-call receiver), and
    `$movie->reviews[0]->author` resolves to `User` through the array element.
19. An entity with an accessor `_getAuthor(): string` on `Review` types `$review->author` as
    `string`, not `User`. Use a separate fixture entity or a test-local class so the other
    tests keep the association type.
20. Completion on `$review-><caret>` offers `movie`, `author`, `editor`, `comments` and
    `ratings` with entity type text, next to the accessor fields; it does not offer `authors`
    or `Authors`. Completion on `$movie-><caret>` offers nothing from associations when
    `MoviesTable` configures none.
21. Goto declaration on `$review->author` reaches the `belongsTo('Authors', ...)` call in
    `ReviewsTable.php` and `User.php`; on `$review->editor` it reaches the `addAssociations()`
    entry.
22. `StringsTest`: `entityToTableClass()` on an app entity, a plugin entity and an irregular
    singular.

The full suite runs at the end (1,366 tests after #310).

## Plan

1. **Bytecode check.** Read `PhpIndexImpl.doCompleteType` in the current PHP plugin for what
   happens when two providers share key `P` and one returns `null`. Decide between a second
   `P` provider and a dispatching one. Record the finding in the progress log.
2. **Fixtures** for cake5: the `Movie` accessors and the `MoviesTable` decoy.
3. **Probe the receiver encodings** for tests 1, 2, 3 and 11 and record them, as the dilution
   log did for controllers and helpers.
4. **Step 1, the type provider,** with tests 1 to 7 and 11 on cake5.
5. **Step 2, completion,** with tests 8 and 9.
6. **Step 3, navigation,** with test 10.
7. **Step 4 check**, then the suppressor and test 12 or a note that it is unnecessary.
8. **Cake 4 and Cake 3**: fixtures and the test copies.
9. **Step 6, association properties:** the `entityToTableClass()` helper and test 22; the
   `associationLoads()` accessor; the resolver inside the `#P` provider with tests 14 to 19;
   completion and navigation with tests 20 and 21; then the cake4 and cake3 copies.
10. **Step 5** if time allows.
11. **Full suite.** Then add the feature to the README's supported-features list.

## Interaction with configured associations in query methods

[Configured associations in query methods](configured-associations-in-query-methods.md) was
planned on a sibling worktree at the same time and merged first, as #311. Its lookups (contain
completion and navigation, the query-builder wrapper) and this feature's (a `#P`-keyed
provider, field completion, field navigation) do not meet. What the merge settled:

- **The stubs** both plans expected to edit were deleted by #310 before either landed, so that
  point of contact is gone.
- **`TableLocatorTypeProvider`.** Test 2 (`$this->Movies->get($id)->full_title`) relies on the
  `get` branch returning the entity class. #311 changed the `find` branch and the expression
  split in `complete()` and kept the `get` path, which still answers with `tableToEntityClass()`.
- **Fixtures.** #311 added `UsersTable` and `ProfilesTable` and left the entities alone. This
  feature edits `Movie.php`, adds `_getLabel()` to `MoviesTable`, and adds the entity and
  `PostsTable` fixtures listed above.
- **The index extension** (`associationType`, `propertyName`, `hasUnreadPropertyName`) is in,
  so [scope item 6](#6-association-properties) has what it needs. The accessor it adds to
  `ConfiguredMemberService` is the one place this feature touches that service.
- **README.** #311 added its line to the supported-features list. This feature's line goes
  after it, with no conflict left to expect.

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
- **Accessor and association with the same name.** Step 6 checks accessors first, matching the
  framework, which hands the stored association value to the accessor. A `_getAuthor()` with
  no declared return type infers from its body, so the type may still end up `User` through
  PhpStorm's inference on the parameter; that is correct, not a conflict.
- **Pluralization in `entityToTableClass()`.** The `Inflector` port covers the framework's
  irregular and uncountable lists at the time it was written. An entity whose plural the port
  gets wrong resolves to no table and silently gets no associations. The `StringsTest` cases
  pin the common shapes; a mismatch found later is an `Inflector` fix, not a provider one.
- **Union receivers.** `$movie` typed `Movie|Article` answers only when every class resolves,
  consistent with the configured-member provider. A field present on one and not the other
  falls back to native resolution.
