# Configured associations in query methods

## Context

Idea 7 in `features/ideas.md`. Follow-up to
[configuration code disambiguation](configuration-code-disambiguation.md) (#299), which indexes
the associations each table configures in `initialize()` (`belongsTo()`, `hasOne()`, `hasMany()`,
`belongsToMany()`, `addAssociations()`) under the table's FQN, and resolves them through
`ConfiguredMemberService` with kind `ASSOCIATION`.

That work covered the property side: `$this->Movies->Articles` in a controller,
`$this->Authors` inside a table, and the nested completion in
`ControllerModelOrTableCompletionContributor`. The query side still guesses by name:

- `ContainCompletionContributor` lists every `Table` subclass in the app and plugin namespaces,
  whatever the receiver. On `ReviewsTable`, which configures `Authors` as an alias of
  `UsersTable`, it offers `Users` and not `Authors`, and offers tables `Reviews` has no
  association with.
- `ContainGotoDeclarationHandler` takes the first dotted segment and resolves it by convention,
  so `'Authors'` on a `Reviews` query goes to `AuthorsTable` when one exists and nowhere when
  only `UsersTable` does.
- Only `contain()` is matched. `matching()`, `notMatching()`, `innerJoinWith()` and
  `leftJoinWith()` take the same association paths and get nothing.
- Nothing follows a dotted path. `'Authors.Profiles'` is cut at the first dot.

The chain walk the idea also lists, `$this->Movies->Reviews->Authors` one configured alias at a
time, already works: #299 made `AssociatedTableTypeProvider` resolve each hop through the service
(`v1` for the field before the caret, `v2` for inner hops, `v3` for `$this->X` inside a table).
This log only adds a test that pins it and closes the gap where a query built from such a chain
loses its table (see [Queries from association chains](#queries-from-association-chains)).

## Scope

Lookup side, plus one small index extension that the lookups here do not need but the
entity-side follow-up does (see [Index extension](#index-extension-association-type-and-property-name)).
The records #299 writes already carry everything the query methods need.

1. Resolve the receiver table of the query, or of the table the method is called on.
2. Offer the receiver's configured association aliases in `contain()`, `matching()`,
   `notMatching()`, `innerJoinWith()` and `leftJoinWith()`, with the resolved table as type text.
3. Follow dotted paths, one configured alias per segment, for completion and navigation.
4. Navigate from an alias to the table it resolves to, not the table its name suggests.
5. Fall back to the current all-tables list when the receiver is unknown or has no indexed
   associations, as the property side does.

Out of scope, recorded for later:

- `find('all', contain: [...])` and the `contain` key of a finder options array (CakePHP 5
  named arguments, and `find('all', ['contain' => ...])` in 3 and 4).
- Associations behaviours add through `$this->_table`. That needs `addBehavior()` indexing and
  belongs with behaviour support (idea 2).
- Narrowing the four `\Cake\ORM\Association` classes to the one the call implies. The index
  extension below records the type, so this becomes a small lookup-side change later.
- Association properties on entities (`$movie->reviews`, `$review->author`). The index
  extension is what makes them possible; the lookup belongs with
  [entity virtual fields](entity-virtual-fields.md).
- CakePHP 2. `contain()` there is the Containable behaviour and takes model names from
  `$belongsTo` arrays; the `CakeTwoChainResolver` can resolve them, but the patterns and tests
  are a separate piece of work.

## How the receiver table is found

`TableLocatorTypeProvider` already tags a query with the table it came from. `find()` on a
table whose type is complete returns the `SelectQuery` type plus the table FQN wrapped in the
`\Best\ChocolateCakePHP\QueryBuilder` prefix (`ChocolateCakeSpecificTypes.kt`), and each
query-returning method called on that keeps the wrapper. `CustomFinderCompletionContributor`
reads it back with `unwrapFromPluginSpecificTypeForQueryBuilder()`.

So the receiver of a query method resolves as:

1. `methodReference.classReference.type.lookupCompleteType(project, phpIndex, null)`.
2. Keep the types that are table classes (`$table->find()` called on a `Table` directly, the
   case `ContainMethodCondition` already accepts) and the wrapped query-builder types, unwrapped.
3. Drop framework base classes; `ConfiguredMemberService.specificReceivers` does this already.

Put this in one helper, `queryReceiverTables(methodReference)`, in `model/` and use it from
the completion contributor, the goto handler and `CustomFinderCompletionContributor`, which does
the same thing inline today.

### Queries from association chains

`$this->Movies->Reviews->find()->contain('<caret>')` has no table today. The receiver of
`find()` is typed with an `AssociatedTableTypeProvider` signature, which is incomplete, and the
`find` branch of `TableLocatorTypeProvider.getType()` returns null for incomplete types (the
`TODO` there). It only defers `ControllerFieldTypeProvider` signatures.

Extend that branch to defer `AssociatedTableTypeProvider` signatures the same way: emit
`#{key}.find.{signature}` and in `complete()` resolve the signature with
`PhpIndex.completeType()`, keep the table classes, and wrap each. The `get` branch already does
this for `$this->Movies->Articles->get()`, so follow its shape. The signature contains dots
(`v2.Articles.…`), so `complete()` cannot keep splitting the expression on `.` into three parts;
split on the first two dots only.

This is the one change outside the contain code, and it is what makes "walk the chain" and
"query methods" compose.

## Completion

### Patterns

`ContainMethodPatterns.ContainMethodCondition` accepts `contain` on a table or query type.
Generalise it to a set of method names: `contain`, `matching`, `notMatching`, `innerJoinWith`,
`leftJoinWith`. The pattern shapes stay as they are (string argument, string in an array
argument). Rename the object to `AssociationPathPatterns` only if the diff stays small;
otherwise keep the name and note it in the implementation progress.

Array *keys* are the common form for nested contain, `contain(['Authors' => ['Profiles']])` and
`contain(['Authors' => fn($q) => ...])`. The current `arrayForCompletion` pattern matches a
string under an `ArrayElement`, and a key sits under an `ArrayHashElement`. Add a pattern for a
string literal that is the key of a hash element whose array is the first argument. The value
array of such a key (`['Profiles']` above) is a nested path: its receiver is the key's target
table, not the query's. Handle the one-level case, key then value array, and leave deeper
nesting of arrays inside arrays to the path walk by treating each enclosing key as a segment.
Verify the PSI shape in a test before committing to the pattern; the goto-declaration log for
#299 noted that `addAssociations()` leaves were left for "the dotted-path work in the
query-method follow-up", and this is that work.

### Provider

Replace the body of `ContainCompletionProvider.addCompletions()`:

1. Receiver tables via `queryReceiverTables()`. None found: keep the current all-tables list,
   unchanged, so the `$query` parameter typed only as `SelectQuery` behaves as today.
2. Path so far: the string's text before the caret (the completion dummy identifier marks it),
   split on `.`. The last segment is the prefix being typed; the earlier segments are hops.
   For a string that is the value inside a key's array, the enclosing keys come first.
3. Walk the hops. For each, `ConfiguredMemberService.memberType(project, tables, {ASSOCIATION},
   hop)`. Use its `classFqns` as the next receiver set; when it is null or has no initialize
   load, add `phpIndex.getPossibleTableClasses(settings, hop)` by convention, as
   `AssociatedTableTypeProvider.getBySignature()` does. A hop that resolves to nothing ends the
   walk with no completions.
4. At the final table set, `ConfiguredMemberService.loadedMembers(project, tables, {ASSOCIATION})`.
   One lookup element per alias, type text the short name of the resolved table class, as
   `completeFromLoadedMembers` renders it in `ControllerModelOrTableCompletionContributor`.
   Reuse that helper if it can be shared without moving it; otherwise mirror it.
5. When `ASSOCIATION` is not in `loaded.initializeKinds`, append the convention list minus the
   loaded aliases, exactly as the nested lookup in the controller contributor does.
6. Set `result.withPrefixMatcher(lastSegment)` before adding elements, so that typing
   `Authors.Pro<caret>` matches `Profiles` against `Pro` and inserts only the last segment. The
   default prefix is the whole string up to the caret, which would match nothing.

Aliases come from `MemberLoad.alias`, which the indexer already strips of the plugin prefix
(`hasMany('TestPlugin.Ratings')` is alias `Ratings`), so plugin associations list under the
alias CakePHP uses in contain strings.

## Navigation

`ContainGotoDeclarationHandler` keeps its pattern checks and first-argument check and changes
how it resolves:

1. Receiver tables via `queryReceiverTables()`. None: fall back to the current convention
   lookup on the first segment, unchanged.
2. Find the segment under the caret by offset within the string contents. Walk the earlier
   segments as hops exactly as completion does (share the walk in one function, say
   `AssociationPathResolver.walk(tables, hops)`), then `memberType()` for the caret segment.
3. Targets: the resolved table classes' files, plus the convention classes when there is no
   initialize load, as `ControllerFieldGotoDeclarationHandler` combines them. Keep returning
   files (through `virtualFilesToPsiFiles`) for consistency with the handler today.

For a key of a hash element, the caret segment is the key's last dotted segment and the hops
are the key's earlier segments plus any enclosing keys.

## Tests

CakePHP 5 first, then mirror to 4 and 3. The `ReviewsTable` fixture already configures the
interesting cases: `Movies`, `Authors` (className `Users`), `TestPlugin.Ratings`, `Editors`
(className `Users`, through `addAssociations()`), `Comments`. `UsersTable` has no
associations, which gives the fallback case for the second hop. For a configured second hop,
give `UsersTable` one association in the fixture (`hasMany('Reviews')` is natural) and check
the tests that load it still pass; prefer adding a new fixture over changing an existing
assertion list.

`cake5/ContainCompletionTest.kt`, extend:

- Reviews query via `fetchTable('Reviews')->find()`: offers `Authors`, `Editors`, `Ratings`,
  `Movies`, `Comments`; does not offer `Users` or `Articles`; `Authors` has type text `UsersTable`.
- Articles query (no configured associations): offers the convention list as today, so the
  existing tests keep passing unchanged.
- `$this->Reviews->find()->contain('<caret>')` with `Reviews` a configured controller table.
- `$this->Movies->Reviews->find()->contain('<caret>')`: the association-chain receiver.
- Nested: `contain('Authors.<caret>')` on Reviews offers `Users`' associations (or, with no
  fixture change, the convention list), and inserting keeps the `Authors.` prefix.
- Array key form `contain(['Authors' => ['<caret>']])` once the pattern is in.
- `matching('<caret>')`, `innerJoinWith('<caret>')`, `notMatching`, `leftJoinWith` on Reviews.
- Same string in a `$query` parameter typed only as `SelectQuery`: convention list, as today.

`cake5/ContainGotoDeclarationTest.kt`, extend:

- `'Authors'` on a Reviews query navigates to `UsersTable.php`, not `AuthorsTable.php`.
- `'Editors'` navigates to `UsersTable.php` (the `addAssociations()` record).
- `'Authors.Reviews'` with the caret in the second segment navigates by the second hop.
- `'Ratings'` navigates to the plugin's `RatingsTable.php`.
- `matching('Authors')` navigates like `contain`.

`cake5/ConfiguredAssociationTest.kt`, add: `$this->Movies->Reviews->Authors` resolves to
`UsersTable` through the fallback hop `Reviews` and the configured hop `Authors`.

`TableLocatorTypeProviderTest` or the nearest existing type test: the query from
`$this->Movies->Reviews->find()` carries `ReviewsTable` in its wrapped type.

## Index extension: association type and property name

The entity side of associations needs two facts the index does not record. The property an
association adds to an entity is derived from the alias and the association type:
`hasMany('Reviews')` gives `$movie->reviews`, `belongsTo('Authors')` gives `$review->author`
(underscored and singularized), and `hasOne` singularizes like `belongsTo`. Its type is the
target entity for `belongsTo` and `hasOne` and an array of them for `hasMany` and
`belongsToMany`. The `propertyName` option overrides the derived name. None of that is in a
`MemberLoad` today: the four method names all map to kind `ASSOCIATION` and are forgotten, and
only the `className` option is read.

Record both at index time, where the information is free:

- `AssociationType { BELONGS_TO, HAS_ONE, HAS_MANY, BELONGS_TO_MANY }` in `MemberLoad.kt`, and
  `associationType: AssociationType?` on `MemberLoad`. Non-null exactly when the load is one of
  the four association types. Null for every other kind, and for the one `ASSOCIATION`-kind
  load that is not an association: the join model below.
  - Direct calls: from the method name in `methodCallSources`.
  - `addAssociations()`: from the top-level key `readAssociationsByType()` is iterating.
  - CakePHP 2 `$belongsTo`, `$hasOne`, `$hasMany`, `$hasAndBelongsToMany` arrays and
    `bindModel()`: from the property or key, with `hasAndBelongsToMany` as `BELONGS_TO_MANY`.
    `LoadReader.property` carries the key for `bindModel()` and the property arrays, but
    `readAssociationsByType()` passes null for `addAssociations()`, so the type is a new
    `LoadReader` field set by every caller rather than derived from `property`.
  - **The HABTM join model is not an association.** For a CakePHP 2
    `$hasAndBelongsToMany` entry with a `with` option, `readJoinModel()` emits a second load
    for the join model (`$this->ArticlesTag`), because the framework lazy-loads it as a
    property too. It goes through the same `memberLoad()` as the HABTM entry and would inherit
    `BELONGS_TO_MANY`. It gets `associationType = null`: it is a model reachable as a property,
    not one of the four types, and a consumer that derives entity properties from the type
    must skip it. `memberLoad()` takes the type as a parameter so `readJoinModel()` passes
    null explicitly, instead of a default it could forget to override.
- `propertyName: String?` and `hasUnreadPropertyName: Boolean` on `MemberLoad`, read with
  `optionValue(options, "propertyName")` next to `className` and mirroring it exactly: the
  string when the option is a literal, else null with the flag set when the option is present
  but not readable (a constant, a concatenation, a variable). An absent option and an
  unreadable one are different facts: the entity follow-up derives the conventional property
  name only when the option is absent, and offers nothing for the association when the flag is
  set, since the application overrode the name with something the index cannot see. The
  CakePHP 2 equivalent is not read; CakePHP 2 has no entities.
- `ConfiguredMemberExternalizer`: write the type as an ordinal with a sentinel for null, the
  property name through the existing nullable-string writer, and the flag as a boolean. Bump
  `ConfiguredMemberIndex.getVersion()`.
- `MemberTypeResult` does not change, and `loadedMembers()` keeps returning resolved types,
  not loads, so the new fields are not reachable through it. The entity follow-up adds an
  accessor on `ConfiguredMemberService` that returns the `ASSOCIATION` loads of a receiver
  chain with the same most-derived-wins rule `resolveChain()` applies. Nothing here reads the
  new fields: the query methods list aliases and resolve tables.

Tests, in `ConfiguredMemberIndexTest` or wherever #299 pins the index records: each of the four
direct calls records its type; each `addAssociations()` key records its type; a literal
`propertyName` option is recorded; an absent option gives null with the flag clear; a
non-literal option (`'propertyName' => self::WRITER`) gives null with the flag set; a component
or helper load has a null type. For CakePHP 2, each property array and `bindModel()` key
records its type, and the join-model load of a HABTM entry with `with` has a null type while
the HABTM load itself has `BELONGS_TO_MANY`.

## Interaction with entity virtual fields

[Entity virtual fields](entity-virtual-fields.md) is planned on a sibling worktree at the same
time. The two designs do not overlap: it adds a `#P`-keyed type provider, a field completion
contributor and a goto handler in a new `entity` package, and it does not touch
`ConfiguredMemberService`, the contain code or `TableLocatorTypeProvider`. This log adds no
type-provider key. The points of contact are files and test discipline, not behaviour:

- **The stubs.** The stub `SelectQuery` in `cake5/vendor/cakephp.php` (and the cake4 and cake3
  stubs) declares `find`, `where` and `contain` only. `matching()`, `notMatching()`,
  `innerJoinWith()` and `leftJoinWith()` have to be added, returning `static` (or `$this` with
  a `@return` tag on 3 and 4) so the query-builder wrapper survives a chain such as
  `find()->matching('Authors')->contain(...)`, which `TableLocatorTypeProvider.complete()` only
  keeps for methods whose return type contains `Query`. The entity log adds `Entity`,
  `EntityTrait` and `EntityInterface` to the same three files. Both edits are additive in
  different classes, so whichever branch merges second rebases and resolves a textual conflict
  at worst. Keep the new methods inside the existing `SelectQuery` and `Query` classes rather
  than appending at the end of the file, to stay clear of the entity additions.
- **Both vendor modes.** The entity log's rule applies here too: every new test passes with the
  stubs and with `./gradlew test -PcakeVendor=real`. In real mode the framework's own
  `SelectQuery` already has every method, so the stub additions above are what make the two
  modes agree. The `src/test/php/*/vendor` directories are untracked and absent in this
  worktree until the real-mode run fetches them.
- **`TableLocatorTypeProvider`.** The entity feature's second test types
  `$this->Movies->get($id)->full_title` and relies on the `get` branch returning the entity
  class. The change in [Queries from association chains](#queries-from-association-chains)
  touches the `find` branch and the way `complete()` splits its expression. Keep the `get`
  path byte-for-byte in behaviour and keep `GetTableLocatorTest` green, so the entity tests do
  not depend on merge order.
- **Fixtures.** The entity log changes `Movie.php` and adds `_getLabel()` to `MoviesTable`.
  This log changes `UsersTable` or adds a new table fixture, and never the entities, so the
  fixture edits are disjoint.
- **README.** The entity log ends by adding its feature to the README's supported-features
  list. Do the same here, as a separate line, and expect a trivial conflict if both land close
  together.
- **Later synergy.** The entity log leaves association properties on entities
  (`$movie->articles`) to "a separate feature". The index extension above, the per-table
  association records, and the path walk this log adds are the input for that. What is left for
  the entity side is an entity-to-table helper (the reverse of `tableToEntityClass()`), the
  name derivation from alias and type, and the lookup element itself. The walk is written as a
  reusable function rather than inline in the contributor for the same reason.

## Steps

1. `queryReceiverTables()` helper; switch `CustomFinderCompletionContributor` to it. Run the
   custom finder tests.
2. Defer association-chain signatures in `TableLocatorTypeProvider.find`. Type test.
3. Method-name set in `ContainMethodPatterns`. Pattern tests for `matching` and friends.
4. Path walk + completion provider. Completion tests for the single-segment cases.
5. Dotted paths in completion, prefix matcher. Nested completion tests.
6. Goto handler on the walk. Navigation tests.
7. Hash-key pattern and the enclosing-key segments. Tests for both completion and navigation.
8. Index extension: `AssociationType` and `propertyName` on `MemberLoad`, the indexer, the
   externalizer, the version bump. Index tests for CakePHP 3+ and 2.
9. Mirror tests to cake4 and cake3. Full suite in both vendor modes.

Commit after each step.

## Implementation Progress

### Session #1

All nine steps are done. The full suite passes with the stubs and with `-PcakeVendor=real`.

Where the implementation differs from the plan:

- **`filterUnknown()` drops deferred signatures.** It removes both association signatures and
  `ControllerFieldTypeProvider` configured-field signatures. The `find` branch now reads both
  from the unfiltered type. This also fixes `$this->Reviews->find()` in a controller, whose query
  had silently lost its table.
- **One pattern instead of four.** `associationPathPosition()` walks up from a string through
  array values, keys and enclosing keys to the call's first argument. A single pattern condition
  uses it for all forms, at any nesting depth. `ContainMethodPatterns` keeps its name, since
  renaming it would only add noise to the diff.
- **The method condition falls back to the complete type.** The signature of a chain such as
  `$this->Movies->Reviews->find()` need not mention a table or a query, so the quick text check
  alone rejected it.
- **Completion now checks the argument position.** A string in the second argument no longer
  offers tables. Navigation already had this rule.
- **Unknown receivers still follow dotted paths.** With no receiver table the first hop resolves
  by convention, so `'Authors.Profiles'` on a plain `SelectQuery` parameter completes and
  navigates by its last segment. A path with no dots behaves as before, except that navigation
  from the second segment of a dotted path no longer goes to the first segment's table.
- **Type text and the convention list.** Configured aliases use the short table name as planned.
  The fallback entries keep the existing all-tables list with the type text `Table`, rather than
  `getModelSubclasses()` as the controller contributor uses, so the existing plugin filtering is
  unchanged.
- **Fixtures.** `UsersTable` gained `hasMany('Reviews')` and `hasOne('Profiles')`, and
  `ProfilesTable` is new, in cake5, cake4 and cake3. No existing assertion needed changing.

### Session #2

Merged `remove-stub-vendor` (#310), which also brought in the controller-side helpers from `main`.
The stub vendor files are gone, so this log's notes about the stubs and both vendor modes no longer
apply: every test runs against the real framework.

The merge needed two changes git could not flag:

- `readViewBuilderHelpers()` from the helper work now passes a null association type.
- `ConfiguredMemberIndex.getVersion()` is 5. `main` and this branch had each moved it from 3 to 4
  with different formats, so an index built by `main` would otherwise have been reused.

### Session #3

Changed the planned fallback after review. An association path segment no longer falls back to a
same-named table when every receiver configures associations in `initialize()` and none loads that
alias. `contain('Articles')` on Reviews used to navigate to `ArticlesTable`, and invalid dotted
paths looked valid, although completion already hid the alias and CakePHP rejects the call.

Unknown and unconfigured receivers keep the convention fallback. The property side keeps its own,
so `$this->Articles` inside `ReviewsTable` still resolves by convention. Associations the index
cannot see, such as those added by behaviors, lose navigation on a table that configures others;
`features/ideas.md` records that under behavior support.
