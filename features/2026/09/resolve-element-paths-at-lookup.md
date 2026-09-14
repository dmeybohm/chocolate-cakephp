# Index logical element names and resolve element paths during lookup

## Status

Proposed follow-up to [element data variable support](support-element-data-variables.md), recorded
2026-09-13. This log describes the intended change; implementation has not started.

## Problem

Element-related index keys currently include a physical directory prefix such as `element/` or
`Element/`. Indexing obtains that prefix through `elementPathPrefixFromSourceFile()`, which checks
whether an `element`, `Element`, or `Elements` directory actually exists under the templates root.

A caller template can exist and be indexed before this sibling directory exists. Its element
references/data can then be omitted. Creating the element directory later leaves the caller unchanged,
so its missing entries may persist until the caller is reindexed. An initial checkout can encounter
this ordering if indexing starts before all directories have been populated.

The dependency is on the element root directory, not on intermediate directories or the target file
named by the call. For example, indexing `element('Director/filmography', $data)` currently requires
the element root, but does not require `Director/filmography.php` to exist.

## Proposed approach

Store the logical name supplied to `$this->element()` without prepending the physical element
root directory. Move the mapping between logical names and actual element paths to the PSI lookup
phase, where the current template layout, plugin/theme context, and filesystem can be consulted.

For example:

| Source call | Current element-data key | Proposed element-data key |
|---|---|---|
| `$this->element('Director/filmography', $data)` in CakePHP 4/5 | `element-data:element/Director/filmography` | `element-data:Director/filmography` |
| Same call in CakePHP 2/3 | `element-data:Element/Director/filmography` | `element-data:Director/filmography` |

Keep `element-data:` as the key-kind discriminator. Remove only the physical root prefix introduced
by the indexer; do not strip a literal `element/` segment that is part of the name supplied by the
caller. Keep nested logical paths such as `Director/filmography` intact.

At lookup time, map an actual element file such as `templates/element/Director/filmography.php` back
to the logical name `Director/filmography` to retrieve incoming references and passed data. For
navigation from a call, resolve the logical name to an actual file using the caller's context.
A missing target should yield no navigation target, while leaving the caller's indexed facts intact.

## Implementation outline

1. Define a shared representation for logical element names and their call kind/context. Element
   references must remain distinguishable from ordinary template/render references with the same
   name. Choose a discriminator or structured representation for ViewFileIndex rather than simply
   dropping its prefix into the existing shared key space.
2. Update both ViewFileDataIndexer and ViewVariableASTDataIndexer to record element names without
   checking the element directory. Continue extracting literal, ternary, match, and locally assigned
   names from the indexed file's syntax. Retain source offsets and data argument handles.
3. Update ViewFileIndexService and ViewVariableIndexService to translate between element files and
   logical keys during lookup. Apply the same mapping to backward reference traversal, completion,
   type lookup, undefined-variable checks, and affected navigation consumers.
4. Keep physical keys for view-file `set()` entries and ordinary template references unless their
   consumers require an explicit adjustment. Do not remove path segments indiscriminately from all
   view keys.
5. Collect index results before PSI/type resolution or further index queries. Moving path resolution
   to lookup must not introduce nested index access inside `processValues()` callbacks.
6. Bump the versions of both affected indexes so existing physical keys and previously omitted
   entries are rebuilt.

## Compatibility details to settle

- Preserve the existing CakePHP 2/3 and 4/5 template layouts, including nested application roots.
- Specify how plugin-qualified names, plugin-local calls, and themed elements map to logical keys
  and lookup scopes. Removing a directory prefix must not conflate unrelated roots or plugins.
  Plugin-qualified element linking already has limitations documented in the original feature log;
  distinguish preserving existing behavior from fixing those limitations.
- Determine whether the current `Elements/` fallback is intentional compatibility support and, if
  retained, handle it during lookup.
- Define leading-slash handling explicitly; the existing `RenderPath`/`fullExplicitViewPath` logic
  treats such names differently from ordinary relative element names.
- Review lookup caching so creating a previously missing target becomes visible without requiring
  edits to its callers.

## Validation plan

- Index a caller before its element root exists; verify its logical reference and data entries are
  present. Create the root and target afterward, without editing/reindexing the caller, and verify
  navigation and element-variable lookup work.
- Repeat with an existing element root but a missing nested directory/target file.
- Cover CakePHP 2, 3, 4, and 5, nested elements, plugin/theme contexts, and supported name expressions.
- Verify an ordinary template and an element sharing a logical name do not share reference keys.
- Verify a literal nested path beginning with `element/` is preserved.
- Confirm index construction does not inspect the element root or target, and lookup performs PSI
  resolution only after index callbacks return.

## Scope

This proposal addresses the external directory dependency found in the PR #290 review. The separate
issues involving indirect data-entry collisions and container-name warning suppression remain
follow-ups in [the original feature log](support-element-data-variables.md#pr-290-review-findings-2026-09-13).
