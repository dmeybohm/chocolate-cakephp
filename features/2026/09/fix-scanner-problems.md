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

## Tests

- `JsonScannerTest`: unterminated string on one line does not hide later
  lines; extra `}` in the middle keeps later entries; trailing-garbage
  test updated.
- `ComposerJsonTest`: unterminated string and stray brace above
  `autoload`/`require` keep both facts; namespace with trailing, leading
  and both backslashes matches; psr-4 key with leading backslash matches.
