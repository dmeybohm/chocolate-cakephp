# Version-aware composer detection

The first step of `cake2-plugin-settings` (see that feature's "Prerequisite: version-aware
composer detection" section), shipped as its own branch because it changes what the version
toggles mean for existing users.

## Problem

`parseComposerJson()` sets `cakePhpRequired` whenever `require` lists `cakephp/cakephp`, with
no look at the constraint. `CakePhpAutoDetector` turns that straight into
`cake3OrLaterPresent`, so a CakePHP 2 app that installs the framework through composer
(`"cakephp/cakephp": "2.10.*"`) is auto-detected as CakePHP 3+. When the user has also ticked
CakePHP 2 support, both resolvers run over the same `.ctp` files and the CakePHP 3+ path and
namespace defaults are applied to a CakePHP 2 layout.

CakePHP 2 support itself is a plain checkbox (`SettingsState.cake2Enabled`) with no detection.

## Design

### Reading the constraint

`ComposerInfo.cakePhpRequired: Boolean` is replaced by `cakePhpMajorVersions: Set<Int>?`:

- `null` when `require` does not list `cakephp/cakephp`;
- otherwise the major versions the constraint allows, possibly empty.

A pure function `constraintMajorVersions(constraint: String): Set<Int>` does the reading, so
it can be tested without composer.json around it:

- Split on `||` (and Composer's legacy single `|`) into alternatives.
- In each alternative take the first version-looking token after stripping the operator
  prefix (`^`, `~`, `>=`, `>`, `<=`, `<`, `=`, `!=`, and a leading `v`), and read its leading
  integer. Within an alternative, `>=2.5,<3.0` and `>=2.5 <3.0` both give 2 from the first
  token, which is the lower bound.
- Alternatives with no leading integer (`dev-master`, `*`, `@dev`) contribute nothing.

So `2.10.*`, `~2.9`, `^2.0` and `>=2.5,<3.0` give `{2}`, `^4.4 || ^5.0` gives `{4, 5}`,
`^2.10 || ^3.0` gives `{2, 3}`, and `dev-master` gives `{}`.

Upper bounds are not used to widen a range (`>=2.0` does not mean "2 through 5"). An
open-ended lower bound is rare for a framework dependency, and reading only the lower bound
keeps an old CakePHP 2 app on CakePHP 2.

### Detected values

`CakeAutoDetectedValues.cake3OrLaterPresent` stays, and a `cake2Present` sits next to it:

| Constraint majors   | `cake2Present` | `cake3OrLaterPresent` |
|---------------------|----------------|-----------------------|
| not required        | false          | false                 |
| empty (`dev-master`)| false          | true (today's answer) |
| contains 2          | true           | if it also has ≥ 3 or an unversioned alternative |
| only ≥ 3            | false          | true                  |

Majors below 2 count as neither.

Namespace and app directory detection are unchanged; they only feed CakePHP 3+ settings.

### Enable semantics

CakePHP 2 gets the same auto-detect / force pair as CakePHP 3+:

- New `SettingsState.cake2AutoDetect: Boolean = true`.
- The existing `SettingsState.cake2Enabled` keeps its stored name and means "force enable",
  so no migration is needed: a user who ticked it keeps CakePHP 2 on.
- `Settings.cake2Enabled` becomes `state.cake2Enabled || (state.cake2AutoDetect &&
  detected.cake2Present)`. `Settings.cake2AutoDetect` and `Settings.cake2ForceEnabled` expose
  the two flags for the form, mirroring `cake3AutoDetect` / `cake3ForceEnabled`.

`Settings.getAutoDetectedValues()` returns empty values whenever CakePHP 3+ auto-detect is off
or forced. That gate moves out of the shared helper: the detector always runs (it is cached
on the files modification tracker), and each getter checks its own version's flags. The
CakePHP 3+ getters (`cakeTemplateExtension`, `appDirectory`, `appNamespace`) keep their
current conditions.

Effect on existing users:

- CakePHP 3+ projects: unchanged, unless their constraint is unusual enough to read as 2.
- CakePHP 2 projects with a 2.x composer constraint: CakePHP 3+ turns off (unless forced),
  CakePHP 2 turns on without the box being ticked.
- CakePHP 2 projects without composer.json: unchanged; they still need the box ticked.
- Projects that were deliberately running both: still both, provided CakePHP 3+ is forced or
  the constraint allows both majors.

### Settings form

`ConfigForm` gets an "Auto-detect CakePHP 2" checkbox beside the existing "Enable CakePHP 2
support" one, which is relabelled to "Force enable" to match the CakePHP 3+ pair.
`updateCake2SettingsVisibility()` shows the CakePHP 2 panel when either is selected, and the
auto-detect box is disabled while force is on, as for CakePHP 3+. `apply()` already compares
`getCake2Enabled()` before and after, so a detection-driven change still triggers a reindex.

The `.form` file is edited by hand; the layout follows the CakePHP 3+ row.

## Tests

- `ComposerJsonTest`: `constraintMajorVersions` for each example above, plus whitespace
  variants, `v2.10.0`, a lone `*`, and an empty string; `parseComposerJson` returns `null`
  majors when `cakephp/cakephp` is absent and the set when present. Existing
  `cakePhpRequired` assertions become `cakePhpMajorVersions != null`.
- `SettingsTest`: composer.json with `2.10.*` detects CakePHP 2 only; `^4.4` detects 3+ only;
  `^2.10 || ^3.0` both; `dev-master` 3+ only; `cake2Enabled` follows detection with
  auto-detect on and ignores it with auto-detect off; force keeps it on with no composer.json;
  CakePHP 3+ auto-detect off no longer hides CakePHP 2 detection.
- Base test cases that set `cake2Enabled = false` to keep CakePHP 2 off also need
  `cake2AutoDetect = false`, or rely on fixtures (`src/test/fixtures/composer.json` requires
  `4.4.*`, so CakePHP 2 is not detected there). Cake 2 tests set `cake2Enabled = true`, which is
  force, so they keep working.

## Implementation order

1. `constraintMajorVersions` and the `ComposerInfo` change, with `ComposerJsonTest`.
2. `cake2Present` in the detector, decoupled `getAutoDetectedValues()`, `cake2AutoDetect`
   state and the new `Settings.cake2Enabled`, with `SettingsTest`.
3. `ConfigForm` checkbox pair.
4. Full test suite.

## Open questions

- Should the form show what was detected (e.g. "Detected: CakePHP 2.10.*") so users can see
  why a version turned on? Not needed for this branch.
- `app/composer.json` in CakePHP 2 skeletons that keep composer under the app directory is not
  looked at; only the project root's composer.json is. Worth checking how common that is.

## Implementation Progress

### Session #1

- Step 1 done: `constraintMajorVersions()` and `ComposerInfo.cakePhpMajorVersions`. A repeated
  `cakephp/cakephp` key inside `require` is last-wins, like the other duplicate keys. Until
  step 2, the detector sets `cake3OrLaterPresent` from `cakePhpMajorVersions != null`, so its
  behaviour is unchanged.
- Step 2 done: the detector sets `cake2Present`, and `cake3OrLaterPresent` now needs a major
  version of 3 or more (or a constraint with no version). New `cake2AutoDetect` state, which
  defaults to true. `Settings.cake2Enabled` is force or detected. `getAutoDetectedValues()` no
  longer depends on the CakePHP 3+ flags. `ConfigForm` now loads its CakePHP 2 checkbox from
  `cake2ForceEnabled`, so saving the form doesn't turn a detected CakePHP 2 into a forced one.
- The full suite is left to CI. Step 3 (the form's auto-detect checkbox) is not done yet.
- Review fix: `dev-master || ^2.10` gave majors `{2}`, which switched CakePHP 3+ off even though
  `dev-master` alone counts as CakePHP 3+. `ComposerInfo.cakePhpUnversionedAlternative` now
  records an alternative with no version, and the detector treats it as CakePHP 3+.
