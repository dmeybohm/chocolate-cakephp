# Simplify composer.json parsing to a key/value scanner

## Problem

`JsonParser.kt` is a 188-line hand-rolled recursive-descent JSON parser with
a 102-test suite. It has one consumer: `CakePhpAutoDetector` in
`Settings.kt`, which reads `composer.json` to learn two facts:

1. Does the `require` object contain the key `cakephp/cakephp`?
2. What string does `autoload.psr-4` map the app namespace key to
   (e.g. `"App\\"` -> `src/`)?

The parser is hand-rolled because detection re-runs on every save of
`composer.json` (see `CakePhpFilesModificationTracker`), and users save the
file in half-edited states. Library parsers reject such input outright,
which makes the auto-detected settings flip to defaults mid-edit.

## Insight

Only key/value membership under two known object paths is needed. Building
a JSON tree is unnecessary. A scanner that tracks the object-key path and
emits flat `(path, key, value)` entries is simpler and inherently more
tolerant: syntax it does not understand is skipped rather than parsed, and
it resynchronises after an error instead of stopping or throwing.

## Alternatives considered

| Alternative | Outcome |
|---|---|
| Replace with Gson (bundled in the platform) | Abandoned. Lenient mode accepts comments but rejects trailing commas and truncated input, so detection flickers mid-edit. |
| Regex / line-based extraction | Rejected. `cakephp/cakephp` also appears under `require-dev` in library projects and psr-4 keys appear under `autoload-dev`; section context needs brace tracking anyway. |
| Last-good fallback in the detector | Rejected. Adds mutable state to a service whose cached value is computed concurrently. |
| Sticky detection via `ModificationTracker.NEVER_CHANGED` | Rejected. Freezes namespace and app directory for the session; live updates are wanted. |
| Keep the tree parser, stop at first error | Done on branch `document-json-parser-fault-tolerance`, superseded by this. Still loses everything after the error. |

## Design

### `JsonScanner.kt`

```kotlin
data class JsonEntry(val path: List<String>, val key: String, val value: String?)
fun scanJsonEntries(json: String): List<JsonEntry>
```

The tokenizer emits strings, the six structural characters, and `OTHER` for
any other run of non-whitespace (numbers, booleans, null, garbage; text
discarded). Strings unescape `\\`, `\"`, `\/`, `\b \f \n \r \t`, and
`\uXXXX` with exactly four hex digits; any other backslash sequence is kept
literally. An unterminated string runs to end of input and is still emitted.

The scanner keeps a stack of frames, each an object with an optional pending
key or an array. `path` is the key of each enclosing object frame that
pushed a child; arrays are transparent. A string followed by `:` is a key;
any other string in an object is the value of the pending key, if there is
one, and otherwise ignored.

| Token in an object frame | Action |
|---|---|
| string followed by `:` | emit pending key with null value if any; this string becomes pending |
| string not followed by `:` | emit pending key with this value if any; else ignore |
| `{` or `[` | emit pending key with null if any; push child (path + pending key if any) |
| `}` or `]` | emit pending key with null if any; pop |
| other | emit pending key with null if any |
| `:` or `,` | ignore |

In an array frame, `{`/`[` push with the same path, `}`/`]` pop, everything
else is ignored. Before the root opens and after it closes, tokens are
ignored. A closer with an empty stack is ignored. End of input ends the scan
with everything emitted so far.

Tolerated by construction: trailing and double commas, missing comma,
missing colon (that key is dropped, scanning continues), missing value
(emitted as null, continues), unquoted key (skipped), truncation,
unterminated string, extra closers, trailing garbage, and an error directly
above `cakephp/cakephp`. No exceptions, no recursion.

### `ComposerJson.kt`

```kotlin
data class ComposerInfo(val cakePhpRequired: Boolean = false, val appDirectory: String? = null)
fun parseComposerJson(contents: String, namespace: String): ComposerInfo
```

`cakePhpRequired` is whether any entry has path `["require"]` and key
`cakephp/cakephp`. `appDirectory` is the value of the first entry with path
`["autoload", "psr-4"]` and key equal to the namespace with a trailing
backslash and no leading one, with any trailing slash removed.

### `Settings.kt`

`CakePhpAutoDetector.autodetectCakePhp()` calls `parseComposerJson` and
drops its try/catch and the two private helpers. `checkNamespaceInAppConfig`
is unchanged.

### Removed

`JsonParser.kt` and `JsonParserTest.kt`.

## Implementation Progress

### Session #1 (2026-09-13)

- Branch `simplify-json-parsing` created from `main`.
- Feature log written.
