# Anchor the app at the configured composer.json

## Status

Planned 2026-11-02; revised the same day after review (see Review below). Supersedes the unmerged
branch `anchor-configured-plugin-paths` (on `origin`, last commit cce3fce0), whose feature log is not
on this branch; the problem statement below is self-contained and its assumption tests and decoy
scenarios are carried over here. Branched from `main` after #321.

## The problem

Since #321 a configured plugin path is matched as a suffix of a directory's path, so that a plugin
under an app nested in the IDE project, such as `cake2/lib/Shop` with `lib/Shop` configured, is
recognised. A suffix also matches any other directory with the same tail: an `examples/` tree that
mirrors the app, a copy of a plugin inside another plugin's `vendor/` after `composer install` was
run there, or any directory named `Shop` when the configured path is just `Shop`. Those files are
indexed as the plugin's views and controllers, named as the plugin in completion and navigation, and
counted in the layout union.

The superseded branch tried to anchor the suffix to an "app root", found by checking that the
directory left after removing the path contains the app directory. In CakePHP 3+ every plugin also
has a `src` directory, so that test cannot tell an app from a plugin, and the exclusions it needed
(a parent named `plugins`, a fallback to the project directory for paths typed the old way) were
each found leaky in review: with `vendor/acme/blog` configured, a copy at
`packages/Parent/vendor/acme/blog` was still Blog whenever `packages/Parent/src` existed. The
underlying fault is that the plugin never settled what "the app" is.

### How the app root drifted

The March 2026 commit that split `AppRootDirectory` from `PluginRootDirectory` (45e6bd5c) said the
app root is the project root and plugin paths are relative to it, as the settings form said. The
function it added, `appRootDirectoryFromViewFile`, instead walks up from a view's plugin root to the
nearest directory containing `src` or `app`, because every test fixture nests the app under
`cake2/`, `cake5/` and so on. That shipped in v1.1.0. #321 then found recognition resolving
configured paths from the project directory while lookup used the walked-up root, and picked the
walk, so recognition had to become a suffix match. Two heuristics for the same directory, both
inferred from layout, is what this branch replaces.

## Definitions

- **Project directory.** What PhpStorm opened, `project.guessProjectDir()`. The only directory the
  user sees and types paths against.
- **ROOT.** The directory of the CakePHP application: the one CakePHP's `config/paths.php` calls
  `ROOT`. It holds `composer.json`, `vendor/`, `config/`, `plugins/` and the app directory. There is
  exactly one per running application. CakePHP finds plugins from it, by name under `ROOT/plugins/`
  and through `vendor/cakephp-plugins.php`, whose paths Composer's plugin installer writes from the
  composer.json directory (pinned by `testComposerInstalledPluginIsFoundUnderTheComposerJsonDirectory`).
  The IDE plugin already defines the app from this file: autodetection takes the CakePHP version
  from its `require`, the app directory from its `psr-4` mapping, and the namespace from the
  `config/app.php` beside it. Today its location is hardcoded as the project directory.
- **App directory.** `ROOT/src` (CakePHP 3+) or `ROOT/app` (CakePHP 2), named by the `appDirectory`
  and `cake2AppDirectory` settings. The setting has a second role: `appOrSrcDirectoryFromSourceFile`
  walks up from a source file matching any child by that name, so inside a plugin it matches the
  plugin's own `src`. That is how plugin controllers are recognised, and it stays. The app itself is
  exactly `ROOT` plus the setting.
- **Unit root.** The root of the app or of one plugin, with its own `src`, `templates` and
  `webroot`: `PluginRootDirectory` in the code, found from layout. The app is one unit among the
  plugins, which is why "contains `src`" cannot identify it.
- **Plugin identity.** The unit a file belongs to: a directory under `plugins/`, the app's override
  directory for a plugin, or a configured plugin's path resolved from `ROOT`.

## The model

One setting, `composerJsonPath`, names the composer.json relative to the project directory and
defaults to `composer.json`, which is where autodetection reads it today. `ROOT` is that file's
directory. If the file does not exist, `ROOT` is the project directory, which keeps today's
behaviour for a CakePHP 2 app without composer.json and for any project autodetection cannot see.

Everything the settings describe is relative to `ROOT`: the app directories, the `pluginPath`
directory (`plugins`), and every configured plugin and theme path. Nothing is discovered or
inferred. Lookup resolves a configured path as `findRelativeFile(root, path)`, as
`pluginAndThemeTemplatePaths` already does with the directory it is given, and recognition asks
whether the file is under that same directory. The two are the same operation, so they cannot
disagree, and no directory-name test is left for a decoy to slip past:

- `plugins/Foo/vendor/acme/blog` and `packages/Parent/vendor/acme/blog` are not `ROOT/vendor/acme/blog`.
- `examples/lib/Shop` is not `ROOT/lib/Shop`.
- A path typed relative to the project directory keeps working, because `ROOT` defaults to it.
- A project that is itself a plugin, or a CakePHP 2 app without composer.json, falls back to the
  project directory as before.

What the model gives up is several apps in one project sharing a configuration, and a configured
plugin outside `ROOT`. Both are scope limits chosen here, not framework facts: CakePHP has one
`ROOT` per application and Composer records plugin paths under it (the assumption tests pin that),
but `addPlugin()` accepts any absolute path, so a plugin outside `ROOT` is possible and is simply
not supported by a configured path. See Not in scope.

### Blank paths

A configured plugin's path can be blank: `synthesizePluginEntries` turns each deprecated
`pluginNamespaces` entry into a `CakeThreePluginConfig` with an empty path, and a saved entry can be
blank too. `findRelativeFile(root, "")` is `ROOT` itself, so a containment test on a blank path would
claim every file under `ROOT` for that plugin, and every configured template directory. Everywhere
the model resolves a configured path, a blank path resolves to nothing: it names no directory for
recognition, matches no template directory in `pluginTemplatePaths`, and contributes no template
paths in `pluginAndThemeTemplatePaths`. The suffix rule today guards the same way, with
`path.isNotEmpty()` in `isDirectoryOfPluginPath` and the `isNotBlank` filter in
`cakeThreePluginName`; the guard moves to the one resolving function so it cannot be forgotten.

### Two path bases

`TemplatesDirWithPath.templatesPath` is a string shown in the navigation popup and typed back into
`CreateViewFileAction`, which resolves it from the project directory. Today the main template paths
are project-relative (`pathRelativeToProjectRoot`), while the plugin and theme paths are the
configured path plus `templates`, so relative to the app root. With the app at the project root the
two agree; with `cake5/composer.json` configured, creating a view in a configured plugin would look
for `vendor/acme/blog/templates` under the project directory and fail. Under the model every
`templatesPath` is project-relative, computed from the resolved directory as the main paths already
are, and `AllTemplatePaths.pluginTemplatePaths`, which today picks a plugin's paths by string prefix,
picks them by directory: the templates directory is under `findRelativeFile(root, pluginPath)`.
Index keys are unaffected: `canonicalizeFilenameToKey` strips the templates directory's own path.
`allViewPathsToFiles` in `CakeView.kt`, which also resolves from the project directory, has no
callers and goes.

## Plan

1. Assumption test on CakePHP 3, 4 and 5, before the plugin code, with a row in the catalogue in
   `src/test/php/README.md`: a plugin installed by Composer is found from `ROOT`. Done; see Session #1.
2. Tests, expected to fail first:
   - cake2 and cake5 `AnchoredPluginPathTest` from the superseded branch, with the same scenarios:
     the copy under `ROOT` is the plugin; `examples/lib/Shop`, `examples/vendor/acme/blog`,
     `plugins/Foo/vendor/acme/blog` and `packages/Parent/vendor/acme/blog` are not. The "applies
     under every app root" and "nested app directory anchors" cases go, since the model has one
     `ROOT`; the nested app directory case becomes a `src/app` setting resolved from `ROOT`.
   - `SettingsTest`: `ROOT` is the directory of `composerJsonPath`, and the project directory when
     the file is missing; `cake5\composer.json` resolves the same file as `cake5/composer.json`.
   - `AutoDetectionGatingTest`: with two composer files present, one requiring CakePHP 4 and one
     CakePHP 2, switching `composerJsonPath` between them changes the detected version without any
     file changing. This pins the cache dependency of step 3.
   - `AutoDetectionGatingTest`: with `composerJsonPath` set to `cake5/composer.json` and no such
     file, nothing is detected and `ROOT` is the project directory; creating the file turns
     detection on and moves `ROOT` to `cake5`, and deleting it turns detection off again, with no
     settings change in between. This pins the widened file tracker of step 3 and the fallback.
   - A blank configured path identifies no directory: with a `pluginNamespaces` entry (synthesised
     with an empty path) alongside a real configured plugin, `isInConfiguredPlugin` is false for an
     app file, `pluginTemplatePaths` for the blank entry is null, and the app's own `templates`
     directory is not offered as a plugin's. One test for recognition and one for template paths.
   - cake5: from a controller under `cake5/src5`, navigation reaches a template in the configured
     `cake5/vendor/acme/blog/templates`, and the popup's path for creating a view there is
     project-relative, with `composerJsonPath` set to `cake5/composer.json`. This pins the path
     base reconciliation of step 5.
3. Add `composerJsonPath` to `SettingsState`, and a `rootDirectory` accessor on `Settings` that
   resolves it and falls back to the project directory. Route `autodetectCakePhp` and
   `checkNamespaceInAppConfig` through it. The autodetection cache in `CakePhpAutoDetector`
   depends only on `CakePhpFilesModificationTracker`, so a changed setting would not refresh it:
   add `Settings.modificationTracker` as a second dependency, and widen the file tracker to watch
   the configured file and the `config/app.php` beside it rather than the project root's.
   Normalise backslashes in the setting to slashes before resolving it: `findRelativeFile` splits
   on `/` only, so a Windows user typing `src\composer.json` would otherwise get a silent fallback
   to the project directory. The user in #267 tried `src\app` for the app directory before
   `src/app` worked, so the form text for the new field says to use forward slashes as well.
4. Resolve from `ROOT` everywhere a configured path is used, through one function that returns the
   configured directory or null for a blank path (Blank paths):
   - `appRootDirectoryFromViewFile` returns `ROOT`; the walk goes.
   - `allTemplatePathsFromTopSourceDirectory` uses `ROOT` instead of the top source directory's
     parent.
   - The configured clauses of `cakeTwoPluginName`, `cakeThreePluginName`, `isInConfiguredPlugin`
     and `isCakeTwoPluginDirectory` test whether the file is under `findRelativeFile(root, path)`.
     The suffix match, `isDirectoryOfPluginPath`, goes. The directory-name clauses (`Plugin`,
     `plugins`, the override directory) are layout rules for units and stay.
   - The asset handlers already take the app root from `appRootDirectoryFromViewFile`.
5. Make every `templatesPath` project-relative and `pluginTemplatePaths` match by directory, as
   under Two path bases, with a blank path matching nothing (Blank paths); delete
   `allViewPathsToFiles`.
6. Test fixtures: each version's base test case sets `composerJsonPath` to `cakeN/composer.json`
   (and the `_nested` variants theirs), and a minimal composer.json fixture requiring that CakePHP
   version is added under each fixture root and copied in `setUp`. The 49 configured paths in the
   suite and the fixture file references stay as written. `test configured vendor plugin is a
   plugin` in the cake3, cake4 and cake5 `PluginAwareViewKeysTest` needs no `Application.php`.
7. `ControllerSideHelperTest`'s three library tests from #319 attach `vendor/test/test_plugin` from
   a temporary directory outside the project, which only matched through the suffix. Under the model
   a plugin outside `ROOT` is not the configured plugin. Rework the fixture so the library sits under
   `ROOT` in the test project while still attached as a library rather than content, or if the light
   fixture cannot do that, narrow the tests to a library under `ROOT` with a heavier fixture. The
   feature these tests pin, controllers in libraries counting in a layout, is unchanged for the
   real case of Composer's `vendor/` under `ROOT`.
8. Settings UI: a "composer.json" field with a default button on `ConfigForm.form`, beside the
   autodetection checkboxes. `ConfigForm.apply` compares the indexing-relevant settings before and
   after and requests a rebuild of `ViewFileIndex` and `ViewVariableIndex` when one changed;
   `composerJsonPath` joins that comparison, since moving `ROOT` changes plugin identity even when
   the detected version does not. Reword the plugin and theme path explanations in
   `PluginForm.form` (lines 62 and 104) and the "from Project Root" labels in
   `EditPluginEntryDialog.form` and `EditThemeEntryDialog.form` to say the paths are relative to
   the directory containing composer.json.
9. Bump `ViewFileIndex` (29) and `ViewVariableIndex` (23), since plugin identity reaches their keys
   and existing indexes were built under the suffix rule.
10. Changelog entry under Changed, with the migration note below. Targeted tests per version, then
    the full suite before opening the PR.

## Migration

A user of v1.1.0 whose app is nested in the project and who typed configured paths relative to the
nested app, because lookup resolved them that way, sets `composerJsonPath` to the nested file and
keeps their paths. One who typed them relative to the project directory, as the form said, changes
nothing. Recognition never worked for the first group before #321, which is unreleased.

## Not in scope

- **Projects typed `cakephp-plugin`.** The same anchor applies and the plugin is the unit at `ROOT`,
  but autodetection reads the namespace from `config/app.php`, which a plugin lacks, so every lookup
  built on `appNamespace` looks under `App\`. Reading namespace and app directory from the first
  `autoload.psr-4` entry when the type is `cakephp-plugin` is its own small branch. After that, a
  plugin's references to itself (`Blog.sidebar`, `Blog.Posts`) need the project registered as a
  configured plugin whose path is `ROOT`, and the test app under `tests/test_app/` inverts the
  nesting, with the app inside the plugin; both belong to a third branch.
- **Per-ROOT autodetection and several apps in one project.** The model has one `ROOT`. A monorepo
  with two apps opens as two projects, or configures one.
- **Layout decoys such as `examples/app`.** The directory-name rules still match by walking up:
  `appOrSrcDirectoryFromSourceFile` takes any ancestor with a child named by the app directory
  setting, so `examples/app/Controller/PostsController.php` is still a CakePHP 2 controller,
  `examples/app/View/Posts/index.ctp` a template, and `examples/plugins/Foo` the plugin Foo, as
  today. This branch fixes the configured-path half of the decoy problem and leaves this half as it
  is. With `ROOT` configured, unit roots become enumerable: the app is `ROOT`, and plugins are the
  directories under `ROOT/plugins/` and the configured paths. A follow-up can make the walk accept a
  source directory only when it belongs to one of those units, which drops `examples/app`. It also
  drops a plugin that is neither under `plugins/` nor configured, which CakePHP would not load
  either, but that is a user-visible change and needs its own feature log.
- **Paths containing `..`.** Not resolved today and not needed under the model.
- **Validating or migrating saved plugin paths** in `EditPluginEntryDialog`.

## Review

A review of the first plan raised four points, each checked against the code:

1. The autodetection cache (`CakePhpAutoDetector.autoDetectedValues`) depends only on the file
   tracker, so changing `composerJsonPath` would not refresh it. Step 3 adds the settings tracker as
   a dependency and step 2 pins it with two composer files.
2. Template paths are stored relative to the app root but resolved from the project directory. The
   reviewer named `allViewPathsToFiles`, which turns out to have no callers; the live consumer is
   `CreateViewFileAction`, through the popup's `fullPath`. Two path bases and step 5 reconcile them,
   and step 2 pins navigation and the create path through a configured plugin under a nested `ROOT`.
3. `ConfigForm` requests an index rebuild only for the settings it compares, and an index version
   bump covers upgrades, not a user moving `ROOT`. Step 8 adds the field to the comparison.
4. The status section linked a feature log that exists only on the superseded branch. It now names
   the branch and commit, and the problem statement carries the rationale itself.

A second review of the revised plan raised two points and a wording correction:

1. A blank configured path, which `pluginNamespaces` entries produce, would resolve to `ROOT` and
   select every template directory under it. Blank paths and the tests in step 2 settle that a blank
   path resolves to nothing.
2. The cache test only switched between existing files, so the widened file tracker was unpinned.
   Step 2 adds a test that creates and deletes the configured file after the setting is made.
3. The model claimed plugins outside `ROOT` are not something CakePHP does. The assumption tests
   establish where Composer installs plugins, not a limit on `addPlugin()` paths, so the model now
   states that as a scope limit.

## Implementation Progress

### Session #1

Step 1. The two assumption tests from the superseded branch came across by cherry-pick, with their
`Pathed` and `Nester` fixtures. Added `testComposerInstalledPluginIsFoundUnderTheComposerJsonDirectory`
on CakePHP 3, 4 and 5: each app requires `acme/installed`, a `cakephp-plugin` package under
`packages/Installed` served from a `path` repository, together with `cakephp/plugin-installer`. On
every version the generated `vendor/cakephp-plugins.php` sets `$baseDir` to the directory above
`vendor`, which is the one holding `composer.json`, and records the plugin as
`$baseDir . '/vendor/acme/installed/'`; `PluginCollection::findPath('Acme/Installed')` returns
`ROOT/vendor/acme/installed/`. So the paths CakePHP itself records for Composer-installed plugins are
anchored at the composer.json directory, which is what the model takes `ROOT` to be. The lock files
moved with `composer update acme/installed cakephp/plugin-installer`. `findPath` is static on
CakePHP 3 and an instance method on 4 and 5, so the test calls it on an instance. The
`TemplateResolutionTest` classes pass on 3, 4 and 5, and the full assumption suites on 3, 4 and 5
pass, with the one pre-existing deprecation warning on CakePHP 4.

Adding the plugin installer had a side effect: its manifest lists every directory under `plugins/`,
so `Probe`, `Other` and `Unloaded` now resolve through `vendor/cakephp-plugins.php` and nothing on 3,
4 or 5 exercised the `App.paths.plugins` scan that the directory-name clause for `plugins/{Name}`
rests on. Added `testPluginsDirectoryPluginIsFoundByNameWithoutAManifestEntry` on 3, 4 and 5: with
`Configure::write('plugins', [])`, which every version takes as the manifest already loaded,
`findPath('Probe')` still returns `ROOT/plugins/Probe/` from the scan. Passes on all three. No plugin
code yet.

### Session #2

Steps 2 to 10, except the full suite run, which CI does on #323.

- **Settings.** `SettingsState.composerJsonPath` defaults to `composer.json`. `Settings.composerJsonFile`
  is that file when it exists, and `Settings.rootDirectory` is its directory or the project directory.
  The one resolving function is `configuredDirectory(root, path)` in `Files.kt`, which returns null for
  a blank path; `Settings.configuredDirectory(path)` calls it with `rootDirectory`, and
  `AllTemplatePaths.pluginTemplatePaths` calls it with the root its plugin paths were resolved from.
  Autodetection reads the configured file and the `config/app.php` beside it. Its cache also depends
  on `Settings.modificationTracker`, and `CakePhpFilesModificationTracker` watches the configured
  file's path, read at event time, plus any directory holding it, so creating or deleting `cake5/`
  counts too.
- **Recognition.** The configured clauses of `cakeTwoPluginName`, `cakeThreePluginName` and
  `configuredCakeTwoPluginOfDirectory` compare a directory with the resolved one. `isInConfiguredPlugin`
  tests `VfsUtil.isAncestor` and no longer takes the project directory. `isDirectoryOfPluginPath` is gone.
- **Template paths.** `allTemplatePathsFromTopSourceDirectory` resolves plugin and theme paths from
  `rootDirectory`. Every `templatesPath` is computed with `pathRelativeToProjectRoot`, and
  `pluginTemplatePaths` picks a plugin's paths by directory containment. `appRootDirectoryFromViewFile`
  returns `rootDirectory` for a view file. `allViewPathsToFiles` is deleted.
- **Tests.** cake2 and cake5 `AnchoredPluginPathTest` cover the decoys, the fallback to the project
  directory, the blank `pluginNamespaces` entry, navigation from `src5` to `cake5/vendor/acme/blog`,
  and the project-relative create path. These tests use the new API, so they could not be run against
  the suffix rule. The create path test checks the whole `fullPath`. A plugin view path used to get a
  double slash (`templates//Posts/missing.php`) from `ControllerPath`'s `viewPath` with an empty name
  and prefix. That predates this branch, and `viewPath` is now empty in that case. `SettingsTest` covers `rootDirectory`, and `AutoDetectionGatingTest` covers switching
  between two composer files and creating and deleting the configured one. With the settings tracker
  dependency removed and the file tracker narrowed back to the project root, both of those tests
  fail, so they pin what they are meant to.
- **Fixtures.** Each `cakeN` and `cakeN_nested` fixture has a composer.json requiring its version.
  The base test cases, the `NestedAppDirectoryTest`s and `BothVersionsLayoutNavigationTest` set
  `composerJsonPath` and copy it. The only expectation that changed is `ConfiguredPluginTest`'s
  template path, which became `cake2/lib/Shop/View`.
- **Library fixture (step 7).** `ControllerSideHelperTest.libraryController` adds the library under
  `cake5/` in the light fixture, excludes it from content and attaches it as a project library, as
  Composer's `vendor/` is. It asserts that the directory is not in content. The three library tests
  pass unchanged.
- **UI.** `ConfigForm` has a composer.json row with a Default button above the CakePHP 3+ section, and
  `apply` requests an index rebuild when the path changes. The plugin and theme explanations and the
  edit dialog labels now say paths are relative to the composer.json directory. Every path field in
  `ConfigForm`, `EditPluginEntryDialog` and `EditThemeEntryDialog` goes through
  `String.pathNormalize()` in `Strings.kt`, which turns backslashes into slashes, so a Windows user's
  `cake5\composer.json` or `src\app` is stored as `cake5/composer.json` or `src/app`. It is tested
  in `StringsTest` rather than `SettingsTest`, since settings now hold normalised paths.
- **Indexes and changelog.** `ViewFileIndex` is at 30 and `ViewVariableIndex` at 24. The changelog
  entry under Changed has the migration note.

The cake2, cake3, cake4 and cake5 packages and `SettingsTest` pass locally.

### Session #3

One auto-detect setting. The CakePHP 2 and CakePHP 3+ auto-detect checkboxes become one "Auto-detect
CakePHP from composer.json" checkbox in the composer.json section of `ConfigForm`, since both read the
same file. It is stored in the existing `cake3Enabled` state field, so saved projects keep their
value, and `Settings.cake2Enabled` now reads that field too. `SettingsState.cake2AutoDetect` is
removed; the XML serializer ignores the stale element in saved files. `Settings.autoDetect` replaces
`cake2AutoDetect` and `cake3AutoDetect`. The force-enable checkboxes stay in their version sections,
and each "Detected" label moves up beside its section title. A project that had turned off only
CakePHP 2 auto-detection detects CakePHP 2 again, which the changelog says. `SettingsTest` replaces
`test cakephp 3 auto-detect off does not hide cakephp 2 detection` with `test auto-detect off
disables both versions`, and its other auto-detect tests set `cake3Enabled` instead.
