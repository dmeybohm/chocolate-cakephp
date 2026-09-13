# Fix scanner problems found in PR #287 review

Branch: `fix-scanner-problems` (on top of `simplify-json-parsing`).

The review of the fault-tolerant composer.json scanner found that two
ordinary mid-edit states still wipe detection, and that the namespace
comparison carried over from the old parser is brittle. This branch fixes
those three.

## Problem 1: an unterminated string swallows the rest of the document

`readString` only stops at the next `"`. A missing closing quote in the
middle of the file therefore flips quote parity for everything after it:
every later string becomes "outside" text and every brace is hidden inside
a "string". With the usual composer.json order, an unclosed quote in
`require` drops `autoload`, and the app directory falls back to `src`.

**Fix:** stop a string at a raw newline (`\n` or `\r`) as well as at the
closing quote. JSON does not allow raw newlines in strings, so a valid
document is unaffected, and an unterminated string now damages at most
the rest of its own line. The newline is not consumed, so the tokenizer's
whitespace skip picks it up normally.

## Problem 2: a stray extra `}` closes the root and stops the scan

When a `}` pops the last frame, the scanner `break`s so that trailing
garbage after the root is ignored. But an extra `}` in the middle of the
file, left behind when a nested block is deleted, also pops the root, and
everything after it is discarded, including `require` if it comes later.

**Fix:** never pop the root frame. An extra `}` or `]` at root level is
ignored and scanning continues as if still inside the root. This trades
"trailing garbage after the root is ignored" for "content after an extra
brace is still scanned", which is the far more common mid-edit state. The
scanner test for trailing garbage is updated to reflect that keys after
the extra brace are now reported at root level.

## Problem 7: namespace and psr-4 key are not canonicalised the same way

`targetNamespace = "${namespace}\\".removeFromStart("\\")` only strips one
leading backslash and unconditionally appends one. A namespace that
already ends in a backslash in `config/app.php` (`'namespace' => 'App\\'`,
which CakePHP tolerates) becomes `App\\` with two backslashes and never
matches the psr-4 key `App\`. The psr-4 key itself is used verbatim, so a
key written with a leading backslash never matches either.

**Fix:** canonicalise both sides with the same function before comparing:
strip all leading and trailing backslashes, then append exactly one.
`\App`, `App`, `App\`, and `\App\` all become `App\`, on both sides.

## Problem 3: duplicate keys are first-wins instead of last-wins

The old Map-based parser, and Composer's own `json_decode`, keep the last
value for a repeated key. `firstOrNull` kept the first. The same applied
one level up: a repeated `require` or `autoload` block was merged with the
earlier one rather than replacing it.

**Fix:** `membersOfLastObject(entries, path)` walks the path one level at a
time, each time taking the last opener of that key inside the last object
at the parent path, and returns only the direct members of that final
object. `cakePhpRequired` and `appDirectory` are computed from those
members, with `lastOrNull` for the psr-4 key. A `require` that is not an
object (a string, say) yields no members and so is not detected, matching
the old `as? Map ?: return false`.

## Problem 5: negative gating test could pass vacuously

`assertFalse(strings?.contains("Movies") == true)` passes when
`lookupElementStrings` is null. From the 2023.2 test framework bytecode,
null means a single completion was auto-inserted without a popup (zero
completions gives an empty list, not null). So a wrongly offered lone
`Movies` completion would have been missed.

**Fix:** `assertNotNull` first, then `assertFalse(contains)`, mirroring the
positive test. PHP's own member completions at `$this->` keep the popup
non-empty.

## Problem 6: signed hex accepted in `\u` escapes

`toIntOrNull(16)` accepts a leading sign, so `\u-041` decoded to a wrapped
garbage character instead of taking the "keep the backslash literally"
branch.

**Fix:** `fourHexDigits` checks each of the four characters against
`0-9a-fA-F` explicitly and returns -1 otherwise, with no substring
allocation.

## Tests

- `JsonScannerTest`: unterminated string on one line does not hide later
  lines; extra `}` in the middle keeps later entries; trailing-garbage
  test updated.
- `ComposerJsonTest`: unterminated string and stray brace above
  `autoload`/`require` keep both facts; namespace with trailing, leading
  and both backslashes matches; psr-4 key with leading backslash matches.

## Implementation Progress

### Session #1

All three fixes implemented in one commit after the plan.

- `JsonScanner.kt`: `readString` returns at `\n`/`\r` without consuming
  it; `RBrace`/`RBracket` only pop when the stack has more than one frame,
  and the `break` is gone. KDoc tolerance list updated.
- `ComposerJson.kt`: new private `canonicalNamespace` (trim backslashes,
  append one) applied to both the configured namespace and each psr-4 key.
- Tests added: 3 in `JsonScannerTest` (unterminated value, unterminated
  key, mid-document extra brace), trailing-garbage test renamed and its
  expectation updated (keys after a stray brace now surface at root);
  6 in `ComposerJsonTest` (four namespace/key backslash variants, and the
  two structural mid-edit cases above `autoload`/`require`).
- Results: JsonScannerTest 21/21, ComposerJsonTest 21/21, SettingsTest
  21/21, AutoDetectionGatingTest 3/3, NestedAppDirectoryTest 2/2.

### Session #2

Problems 3, 5 and 6 implemented in one commit.

- `ComposerJson.kt`: `membersOfLastObject` replaces the flat path filters;
  psr-4 lookup uses `lastOrNull`.
- `JsonScanner.kt`: `fourHexDigits`/`hexDigit` replace the
  `substring` + `toIntOrNull(16)` check.
- `AutoDetectionGatingTest.kt`: negative test asserts the popup is
  non-null before asserting Movies is absent.
- Tests added: 2 in `JsonScannerTest` (signed escape, short escape at end
  of input); 4 in `ComposerJsonTest` (duplicate psr-4 key, duplicate
  autoload block, duplicate require block in both orders, non-object
  require).
- Results: JsonScannerTest 23/23, ComposerJsonTest 25/25,
  AutoDetectionGatingTest 3/3, SettingsTest 21/21.

Still open from the review: `cakePhpRequired` treats a literal `null`
value as present (problem 4).
