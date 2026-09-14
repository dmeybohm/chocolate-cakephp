# View-variable source identity and execution order (part 2)

## Context

PR #290 adds element data and view-file `$this->set()` calls to the view-variable index. The first
part intentionally uses canonical view keys to connect rendering files and variable sources. Review
found two remaining cases where a canonical key alone does not contain enough context:

1. `view-set:` records from an unrelated physical template can be returned for the same canonical
   path.
2. Every `set()` call in an ancestor is treated as visible to every element or partial it renders,
   even when the call occurs after that render.

This follow-up should address both together because the reverse traversal needs to retain the
physical source file and the particular render edge before it can apply source-order filtering.

## Problem 1: view-set source collisions

`viewSetKey(viewKey)` stores only a canonical relative path such as `view-set:Movie/review`.
`lookupRawVarsByKey()` currently reads that key from `GlobalSearchScope.allScope(project)`. If an app
template and a dependency, plugin, theme, or data-view variant canonicalize to the same path, records
from all of those files can be merged into the variables of an element rendered by only one of them.

Changing the scope to `projectScope` is not sufficient: an app and a project-local plugin can both
be project content and still share a canonical path. The reverse ViewFileIndex lookup already knows
the physical file containing each reference, so view-set lookup should be restricted to that exact
source file.

Controller keys are not part of this change. They retain their existing lookup scope. Element-data
keys also retain the project scoping added in the first part; their records intentionally represent
alternative calls targeting the same element.

## Problem 2: set() visibility depends on execution order

For an immediate element or partial render, only `set()` calls executed before that call are visible:

```php
$this->set('early', 1);
echo $this->element('card');
$this->set('late', 2);
```

`card` receives `$early`, but not `$late`. Including `$late` creates an invalid completion and can
suppress a legitimate undefined-variable inspection.

Layout references have different timing. A template selects its layout while executing, but the
layout is rendered after the template finishes, so later `set()` calls must still reach the layout:

```php
$this->layout = 'default';
$this->set('forLayout', 1);
```

A simple `set.offset < reference.offset` filter would therefore fix element calls while breaking
layout propagation. The traversal must retain the reference kind and apply the appropriate timing
rule.

Multiple call sites are alternatives rather than a single sequential layer. If a file renders the
same element before and after a `set()`, the element can receive the variable on one invocation but
not the other. Completion and type lookup should conservatively union the variables and types visible
across those invocations.

## Proposed design

### Preserve reference context

Extend the lookup-time `PsiElementAndPath` result, or replace it with a more explicit DTO, so each
reverse edge retains:

- the physical `VirtualFile` or stable source path;
- the existing `ViewReferenceData.elementType` and `methodName`;
- the reference offset;
- the resolved canonical key used for the next reverse lookup.

The required reference kind and offset are already stored in ViewFileIndex. Propagating them through
lookup should not require changing its persisted format or bumping its version.

Represent a view-set contribution as an invocation-specific source rather than only a string key,
for example `ViewSet(key, sourceFile, visibleBeforeOffset)`. `visibleBeforeOffset` is the immediate
render-call offset for element/partial calls and unbounded for layout rendering.

### Restrict records to the physical source

When reading a `ViewSet` source, return records only from its physical ancestor file. Prefer the
exact-file parameter supported by `FileBasedIndex.processValues()` if it has the required semantics;
otherwise collect by key and filter the returned `VirtualFile`s before loading PSI or expanding
entries.

Keep the existing index-access boundary: callbacks may collect stored data only. PSI loading,
dynamic argument expansion, recursive view-variable lookup, and type resolution must remain outside
`processValues()` callbacks.

### Apply timing rules after lookup

For an immediate `$this->element()` or view `$this->render()` edge, expand only view-set call records
whose call offset precedes the reference offset. Preserve current source-order overwrite behavior
within that filtered set.

For a layout edge, include every relevant `set()` call in the template because the layout renders
after template evaluation. Explicitly classify every ViewFileIndex reference kind rather than using
PSI class alone; method and field references can have different runtime timing.

Controller-to-template edges do not contribute view-file set records and need no cutoff.

### Preserve alternative paths

Do not deduplicate view-set sources solely by canonical key. At minimum, source identity must include
the physical file and timing cutoff. Multiple edges from one file may produce different visible
sets, and unrelated files may share the same canonical key.

For completion, existence checks, and type lookup:

- merge sequential `set()` calls within one invocation in source order;
- union alternative invocations and reverse-render paths;
- retain element-call data precedence over inherited view variables;
- retain the recursion and traversal bounds used for cyclic element graphs.

Control-flow feasibility remains an approximation. Textual order should be respected, but this work
does not attempt to prove whether a conditional branch or loop executes.

## Implementation outline

1. Propagate `ViewReferenceData` metadata and physical source identity from
   `ViewFileIndexService.referencingElementsInSmartReadAction()`.
2. Define the timing classification for element, render/partial, layout, and controller references.
3. Make `ViewVariableSource.ViewSet` carry the exact source file and optional visibility cutoff.
4. Restrict `view-set:` reads to that physical file while retaining the raw-record collection
   boundary.
5. Filter ordered `ViewVariableCall` records by the cutoff before dynamic expansion and merging.
6. Adjust reverse traversal deduplication and result aggregation so alternative call sites union
   rather than overwrite one another.
7. Apply the same source selection to completion, single-variable type lookup, and undefined-variable
   existence checks.
8. Update comments and the original feature log when implementation begins. Bump an index version
   only if the persisted key/value format ultimately changes.

## Validation plan

Start with CakePHP 5, then mirror the relevant coverage across CakePHP 2–4.

- An element receives a variable set before its call.
- An element does not receive a variable set after its call; completion, type lookup, and the
  undefined-variable inspection agree.
- A view rendered with `$this->render()` follows the same immediate-call ordering rule.
- A layout receives variables set after layout selection but before template completion.
- Two calls to the same element, with a `set()` between them, are treated as alternatives and union
  their possible variables/types.
- Nested elements apply the cutoff independently at each reverse edge.
- An app template does not receive `view-set:` records from a same-key dependency, project-local
  plugin, theme, or data-view variant.
- A plugin or themed template still receives its own view-set variables.
- Cyclic element references terminate and retain the existing lookup bound.
- Index callbacks do not perform PSI loading, dynamic expansion, type resolution, or nested index
  queries.

Run focused view-variable indexer, completion, type-provider, and inspection tests first, followed by
the full offline suite.

## Out of scope

- Redesigning logical element-data keys or removing their element-directory dependency; that remains
  covered by `resolve-element-paths-at-lookup.md`.
- Full PHP control-flow analysis.
- Changing controller variable key semantics.
- General plugin-qualified element resolution beyond what is necessary to keep view-set sources
  isolated.

## Implementation progress

Not started.
