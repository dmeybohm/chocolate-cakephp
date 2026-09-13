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
- `$var` element data: `allowVariableIndirection = true` for the element argument. Since `set($vars)` only
  ever fed the undefined-variable suppressor (completion listed `$vars` itself, typed `mixed`, and the type
  provider found nothing), the lookup side gained `expandDynamicEntry`: an indirect entry is expanded with
  PSI from its assignment into concrete ARRAY / COMPACT entries whose offsets point into that assignment, so
  they resolve types like literal ones. Completion and the type lookup use the expansion; the suppressor
  already did name-only expansion. This fixes controller `set($vars)` completion as a side effect (tests
  `test controller set with an array variable completes the real variable names` and the compact twin).
