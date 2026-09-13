# Element data arrays and view-file `set()` in the view variable index

## Problem

An element file gets its completable variables only from controller `$this->set()` calls. The lookup
walks backwards from the element through the templates that render it (ViewFileIndex) to controller
actions (ViewVariableIndex). Two real CakePHP sources of variables are invisible:

1. **Data passed in the element call**: `$this->element('Director/filmography', ['director' => $x, 'count' => 3])`.
   `ViewFileDataIndexer.parseMethodCall` reads only the first argument; the data array is discarded.
2. **`$this->set()` inside view files.** Verified in CakePHP 5.3.1 source and the book: `View::set()` writes
   the same `viewVars` map the controller fills; `_renderElement` renders with
   `array_merge($this->viewVars, $data)`, and the layout is rendered afterwards with the same map. So a
   template's `set()` reaches its elements and the layout, and passed data wins over a same-named view var.

## Decisions

- This is an index change, not a lookup-time PSI shortcut. The `$this->set()` argument parser is refactored
  into a generic, reusable parser shared by `set()` and `element()` data.
- Element call sites are indexed wherever they occur: templates, layouts and other elements (nested).
- Element data forms: array literal, `compact(...)`, and a `$var` holding either (added in session #2 after the
  first three landed).
- `$this->set()` inside view files is indexed too, under the view file's own key.
- ViewFileIndex is untouched (stays at version 19). ViewVariableIndex bumps 17 -> 18.

## Index key scheme

Three key kinds share `VIEW_VARIABLE_INDEX_KEY`. Builders live next to `controllerMethodKey`:

| Kind | Builder | Example | Meaning |
|---|---|---|---|
| controller action | `controllerMethodKey(path, method)` | `Movie:index` | vars set by the action |
| element data | `elementDataKey(viewKey)` | `element-data:element/Director/filmography` | data arrays passed into that element |
| view set | `viewSetKey(viewKey)` | `view-set:Movie/film_director` | `$this->set()` calls inside that view file |

Controller names cannot contain `-`, so the prefixes cannot collide. `viewKey` is exactly the ViewFileIndex
canonical key, so the lookup derives both from the `filenameKey` it already has.

## Lookup semantics

For a file `X` being completed, the sources are visited in this order:

1. `element-data:X` — data passed directly to `X`. Queried for the **original key only**: CakePHP passes
   `$data` just to the named element; it does not flow into elements that element renders.
2. `view-set:X`, then `view-set:<ancestor>` for every view file the BFS re-keys on the way to controllers.
   `viewVars` is shared downwards, so a template's `set()` reaches nested elements. Slight superset: a
   `set()` late in a file counts for the whole file and for sibling elements.
3. Controller action keys reached by the existing BFS (unchanged).

Completion layering: controller vars, then view-set vars (ancestor first, original last), then element data,
so passed data overrides a same-named view var, matching `array_merge`. Types are unioned.

## Design

- **Step 1** `ASTNodes.kt`: shared call reader (`MethodCallParts`, `readMethodCall`, `collectMethodCalls`,
  `parameterNodes`, `stringLiteralValue`, `isCompactCall`).
- **Step 2** `ViewVariableArgumentParser`: AST-only parser for the "data" argument (ARRAY, COMPACT,
  optional VARIABLE_ARRAY). `parseSetCalls` becomes a thin `set()`-specific composition (PAIR, TUPLE,
  MIXED_TUPLE stay in the indexer). `SetCallInfo` is deleted; the parser emits `RawViewVar` directly.
- **Step 3** `TemplateNames.kt`: `extractTemplateNames` + `TemplateNameContext` moved out of
  `ViewFileDataIndexer` so both indexers share ternary/match/`$var` template-name resolution.
- **Step 4** `ViewVariableASTDataIndexer.map()` also indexes non-controller files under a templates dir:
  one AST walk collecting `$this->element(name, data)` and `$this->set(...)`.
- **Step 5** Scope-agnostic PSI resolution (`enclosingScope = Function ?: file`), plus a third strategy that
  resolves an unassigned template `$var` through `Variable.type.global()`, which re-enters
  `ViewVariableTypeProvider` for the template. This is how a passed `$moviesTable` inherits its controller type.
- **Step 6** `RecursionManager.doPreventingRecursion` keyed on `(filenameKey, varName)` around the type lookup
  so cyclic element calls terminate.
- **Step 7** The three duplicated BFS loops collapse into `forEachContributingSource`, which emits a sealed
  `ViewVariableSource` per key kind.

## Known limitations / follow-ups

- A `$var` holding element data is read from its last assignment only; keys added afterwards with
  `$data['extra'] = ...` are not seen. Same limitation as `set($vars)` in controllers.
- Plugin element calls (`'Plugin.foo'`) are keyed verbatim as `element/Plugin.foo` in ViewFileIndex and do
  not link to the plugin file's own key. Pre-existing.
- Layouts get no controller vars unless something references them in ViewFileIndex; element data and
  view-set vars work in layouts regardless.
- A `set()` inside an element also leaks to sibling elements rendered later in CakePHP; not modelled.

## Implementation Progress

### Session #1 (2026-09-08)

- Plan written and approved; branch `support-element-data-variables` created.
- Step 1: shared AST call reader in `ASTNodes.kt` (`MethodCallParts`, `readMethodCall`, `collectMethodCalls`,
  `parameterNodes`, `stringLiteralValue`, `isCompactCall`).
- Step 2: `ViewVariableArgumentParser` extracted; `parseSetCalls` reduced to a `set()`-specific composition.
  `SetCallInfo` deleted. The text-based `compact(` check became a real FUNCTION_CALL name check.

### Session #2 (2026-09-13)

- Rebased onto `main` after the ternary/match work and scanner fixes merged (no conflicts). Work continued in
  `worktree/support-element-data-variables`.
- Step 3: template-name resolution moved verbatim into `view/viewfileindex/TemplateNames.kt` (internal
  top-level); `ViewFileDataIndexer` output unchanged, ViewFileIndex stays at version 19.
- Step 4: `ViewVariableASTDataIndexer.map()` indexes non-controller files under a templates dir in one AST
  walk: `$this->element(name, data)` under `elementDataKey(...)`, `$this->set(...)` under `viewSetKey(...)`.
  The file's own key comes from `canonicalizeFilenameToKey`, so data-view dirs (`json/`) are stripped exactly
  as on the lookup side. ViewVariableIndex 17 -> 18. Also gained the `Settings.enabled` early return that
  `ViewFileDataIndexer` already had.
- Step 5: `ViewVariablePsiScope.kt` (`enclosingScope`, `lastAssignmentBefore`, `scopeParameter`,
  `scopeVariable`). `resolveLocalVariableType` gained two strategies after assignment/parameter: the variable
  itself via `Variable.type.global()` (re-enters `ViewVariableTypeProvider` for a template, honours `@var`
  docblocks), then the view file's own variable lookup for a `compact('name')` whose name the scope never
  mentions. Nested closures are now excluded from the assignment search, which they were not before.
- Steps 6-7: `forEachContributingSource` replaces the three BFS copies and emits `ElementCallData(original)`,
  `ViewSet(original)`, then `ViewSet(ancestor)` / `ControllerAction(key)` from the walk. The type lookup is
  wrapped in `RecursionManager.doPreventingRecursion((filenameKey, varName))`. Completion layers controller
  < ancestor set() < own set() < passed data.
- Tests: 9 indexer + 9 completion/inspection tests for cake5 (including nested elements, ternary names,
  layouts, override, cyclic elements), 3 indexer + 2 completion tests each for cake4/3/2. New fixtures:
  `element/nested_outer.php`, `element/nested_inner.php`, `element/cycle_a.php`, `element/cycle_b.php`,
  `layout/default.php` (cake5).
- Findings while testing: `$crumbs = ['Home']` types as `string[]`, not `array`; the cycle test's direction
  had to match what each element actually receives.
- `$var` element data: the parser records a bare `$var` for element data exactly as for `set()`; the
  opt-out flag it briefly had was removed since every caller passed true. Since `set($vars)` only
  ever fed the undefined-variable suppressor (completion listed `$vars` itself, typed `mixed`, and the type
  provider found nothing), the lookup side gained `expandDynamicEntry`: an indirect entry is expanded with
  PSI from its assignment into concrete ARRAY / COMPACT entries whose offsets point into that assignment, so
  they resolve types like literal ones. Completion and the type lookup use the expansion; the suppressor
  already did name-only expansion. This fixes controller `set($vars)` completion as a side effect (tests
  `test controller set with an array variable completes the real variable names` and the compact twin).

## PR #290 review findings (2026-09-13)

The review identified three issues to address before merging:

1. **[P1] Nested index access during type lookup.**
   `ViewVariableIndexService.lookupVariableTypeByKey()` resolves types inside the
   `FileBasedIndex.processValues()` callback. The new `resolveLocalVariableType()` fallback can
   recursively query view indexes through `lookupAsViewVariableOfFile()`, for example when an element
   receives a controller-provided variable through `compact('movie')`. PHP type inference can also
   re-enter indexes. Resolving controller types inside the callback predates this PR, but the explicit
   recursive view-index lookup is new. Collect indexed records first, then expand and resolve them
   after `processValues()` returns. JetBrains documents a potential deadlock from
   [nested index access](https://plugins.jetbrains.com/docs/intellij/file-based-indexes.html#nested-index-access).
   The risk was identified from the call graph; no deadlock was reproduced.

2. **[P2] Indirect data entries overwrite other calls' variables.**
   `ViewVariableASTDataIndexer.addAll()` keys every entry by `variableName`, including an indirect
   entry named after its container variable. If `$data = ['first' => 1]` is passed to an element,
   then reassigned to `['second' => 2]` and passed to the same element again, only `second` survives
   lookup. A `$data` placeholder also overwrites an earlier explicit `['data' => 1]` entry for that
   element. Both cases failed regression probes, returning only `second`. Preserve indirect entries
   by call-site identity, separately from concrete variable names, so expansion retains all sources.

3. **[P2] The data container incorrectly counts as an element variable.**
   `ViewVariableIndexService.variableExistsByKey()` accepts a direct map-key match without excluding
   dynamic entries. Given `$data = ['title' => 'Example']; $this->element('card', $data)`, it reports
   `$data` as available inside `card`, although only `$title` was passed. This suppresses a legitimate
   undefined-variable warning. A regression probe confirmed the false positive. Exclude dynamic
   placeholders from the direct existence check and inspect their expanded names instead. The helper
   predates this PR; applying it to element data exposes the issue in the new feature.

### PSI and indexing audit

- Both registered indexers access `inputData.psiFile` and its full `ASTNode` tree. This largely
  predates the PR; the new view-file branch also obtains `psiFile.node`. No PSI resolution or index
  queries were found in either `map()` call graph. The shared AST parsers operate on syntax only.
  Current-file PSI access is not categorically forbidden; JetBrains recommends lexers or `LighterAST`
  for [indexing performance](https://plugins.jetbrains.com/docs/intellij/indexing-and-psi-stubs.html#improving-indexing-performance).
  PSI scope traversal and type resolution occur during lookup. The nested lookup above is the
  concrete index-access concern.
- **External directory dependency:** `elementPathPrefixFromSourceFile()` uses `findElementDir()` to
  check whether a sibling `element`, `Element`, or `Elements` directory exists. Indexing a caller
  before its first element directory exists omits its element-data entries; creating the directory
  later does not itself reindex the unchanged caller. This dependency predates the PR in the
  view-file index and is newly reused by the variable index. Derive the conventional prefix without
  checking directory existence, or invalidate affected callers on directory changes. This is a
  static finding, not a tested reproduction. See JetBrains'
  [requirement that index output depend only on its input](https://plugins.jetbrains.com/docs/intellij/file-based-indexes.html#implementing-a-file-based-index).

### Review validation

- Existing focused tests passed:
  `./gradlew test --offline --tests '*cake5.ViewVariableTest' --tests '*ViewVariableASTDataIndexerTest'`.
- Three temporary regression probes failed: two for entry collisions and one for the container-name
  false positive. The probes were removed after review; no fixes were made as part of the review.

## Plan to address the remaining PR review issues

Status: proposed, 2026-09-13. This plan covers nested index access, indirect entry collisions, and
container-name warning suppression. The element-root dependency and logical element key redesign
remain pending in [resolve-element-paths-at-lookup.md](resolve-element-paths-at-lookup.md).
Implementation of this plan must retain the current physical element keys and ViewFileIndex format.

### 1. Collect index records before resolving PSI

- Refactor `lookupRawVarsByKey()` into a shared collection boundary returning the indexed
  `VirtualFile` and its raw records. Its `processValues()` callback should only collect stored data;
  move its existing `PsiManager.findFile()` call outside the callback too.
- Make type lookup, completion, and existence checks consume those collected records. Locate source
  PSI files, expand indirect entries, and resolve types only after `processValues()` has returned.
- Preserve name-only existence checks: static names need no PSI; dynamic names need assignment
  inspection but must not invoke `RawViewVar.resolveType()` or PHP global type resolution.
- Keep the existing recursion guard around view-variable type lookup. It bounds cyclic element
  dependencies, while collecting first addresses the separate nested-index-access risk.

### 2. Preserve call sites instead of using placeholder names as storage keys

- Replace the inner `HashMap<ViewVariableName, RawViewVar>` storage with an ordered collection of
  call records. Each record holds the call offset and its raw entries; the outer index key continues
  to identify the controller action, view-set source, or element-data target.
- Use the call offset within the indexed file as call-site identity. Preserve every call in source
  order, including repeated uses of the same `$data` variable. Keep each entry's existing value
  handle/offset for assignment lookup and navigation; an assignment offset is not a call offset.
- Dynamic entries carry the local symbol to expand, but that symbol is never used as a concrete
  variable-name key. Static and indirect entries therefore cannot overwrite each other in storage.
- Update both controller `set()` collection and view-file `set()`/`element()` collection to use the
  same representation. Do not introduce synthetic string keys into the existing variable-name map.
- Update `ViewVariableRawVarsExternalizer` to serialize ordered calls and entries, and ensure the
  value has structural equality after deserialization. Bump ViewVariableIndex from 18 to 19 (or the
  next available version when implemented). Keep its index ID and outer key strings unchanged.

### 3. Share expansion and define merging explicitly

- Expand each call's indirect entries against its original source file and use offset. Reusing
  `$data` after reassignment must expand both calls independently.
- Provide a shared operation for obtaining concrete names/value handles from a call, without
  resolving types. Completion, type lookup, and existence checks should use the same expansion
  rules, including the currently supported name-only fallback forms.
- For sequential `set()` calls within one indexed source, merge expanded entries in call order:
  later assignments replace the same concrete name, while other earlier names remain available.
  Preserve the existing source-layer precedence across controllers, ancestor views, the current
  view, and passed element data. This remains the existing syntactic approximation, not full PHP
  control-flow analysis.
- Treat separate calls rendering the same element as alternative sources. Retain the union of
  concrete names and possible types across those calls rather than letting the last rendering
  erase earlier inputs. Use deterministic ordering for any representative navigation offset.
- Keep the existing union policy for type lookup across contributing sources; this change should
  not independently redesign cross-source type precedence.

### 4. Exclude container names from existence checks

- A static entry can establish that its concrete name exists. A dynamic entry establishes only the
  names obtained by expansion; its local container name is never sufficient evidence.
- `$data = ['title' => 'Example']; element('card', $data)` must expose `title` and leave `data`
  undefined in the element. Conversely, `['data' => ...]` must still expose a real variable named
  `data`, whether passed directly or through an indirect array.
- An unresolved dynamic argument contributes no inferred names. It must not suppress a warning for
  its container or prevent other valid sources from being checked.

### 5. Regression coverage and validation

Add permanent tests before changing behavior, using the three review probes as starting cases:

- Reassign and reuse `$data` in two calls to the same element; both sets of concrete names survive.
- Mix an explicit `data` key and a `$data` container in both call orders; neither loses unrelated
  variables. Include the equivalent controller/view-file `set()` cases.
- Verify that an unpassed container name keeps its undefined-variable warning, while actual passed
  keys suppress warnings. Include a positive case with a real key matching the container name.
- Cover reassigned arrays and `compact()` containers, static/dynamic overrides in both orders for
  `set()`, and the same element variable receiving different types from separate calls.
- Retain controller-to-template-to-element type propagation and cyclic element tests. Exercise the
  `compact('movie')` fallback when the calling template has no explicit `$movie` reference.
- Add a focused serialization round-trip test proving repeated call records, ordering, offsets, and
  structural equality survive persistence.
- Verify the collection boundary with a focused test seam or platform assertion that rejects PSI
  resolution/reentrant lookup while the index callback is active. Passing ordinary completion tests
  alone does not demonstrate the absence of the deadlock risk.

Run the variable indexer and view-variable tests across CakePHP 2–5, including completion, type
lookup, and inspection coverage. Review the affected `processValues()` callbacks to confirm they
only collect raw data. No element-key migration, directory-invalidation work, or parser-performance
rewrite is included in these fixes.

### Suggested implementation order

1. Restore the confirmed failing regressions and refactor the index collection boundary.
2. Introduce ordered call storage, its externalizer, and the index-version bump; adapt consumers.
3. Consolidate expansion, apply merging rules, and fix existence checks.
4. Complete the regression matrix, run focused suites, and update this log with results.

### Implementation Progress — Session #3 (review fixes)

- Replaced the inner variable-name map with ordered `ViewVariableCall` records containing the call
  offset and raw entries. Controller and view-file calls now preserve repeated containers and
  explicit keys independently. Updated serialization and bumped ViewVariableIndex 18 -> 19.
- Unified variable-index reads behind a collector returning virtual files and raw records. Its sole
  `processValues()` callback only appends those records; PSI loading, indirect expansion, and type
  resolution happen after it returns. The existing recursion guard remains in place.
- Shared concrete-entry expansion across completion, type lookup, and existence checks. Sequential
  `set()` calls overwrite concrete names in source order; separate element renderings contribute
  alternative names/types. Dynamic container names no longer count as passed variables.
- Added shared regression coverage running against CakePHP 2–5: reused arrays/compact containers,
  literal/container collisions in both orders, controller/view `set()` ordering, inherited compact
  forwarding, actual undefined-variable diagnostics, type unions, and serialization round trips.
- All 40 new regression cases pass. Existing indexer/resolution tests were adapted to the new stored
  records while preserving their syntax assertions. `./gradlew test --offline` passed all 762 tests
  with no failures or skips. After a final change to merge element types into a fresh `PhpType`,
  all 40 new regression cases passed again.
- The callback boundary was verified by code inspection; no timing-dependent deadlock test or mock
  index seam was introduced. The inherited compact case exercises the recursive lookup path.
- The physical element keys and directory-existence behavior remain unchanged; the separate logical
  element-name proposal is still pending.
