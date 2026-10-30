# CakePHP 2 plugin settings

## Context

Plugins are configured through `PluginConfig` (`pluginName`, `namespace`, `srcPath`,
`pluginPath`, `assetPath`), which is shaped for CakePHP 3 and later: a namespace, and templates
under `{pluginPath}/templates` or `{pluginPath}/{srcPath}/Template`. CakePHP 2 plugins work by
pointing a `PluginConfig`'s `pluginPath` at the plugin directory, so that
`pluginAndThemeTemplatePaths()` finds `{pluginPath}/View`. The namespace and `srcPath` fields
mean nothing for CakePHP 2, whose plugin classes are global.

`configuration-code-disambiguation-cake2` needs to know which plugin a file belongs to and the
plugin's name, for plugin-aware template lookup and for recognising `{Plugin}AppController`. It
derives both from file paths instead of settings (`cakeTwoPluginName()` in `CakeTwoPlugins.kt`):
`{app}/Plugin/{Name}/` and `{root}/plugins/{Name}/` (the default `CakePlugin::load()` paths), plus
any configured `pluginPath`, falling back to the directory name when the configured name is
empty. This feature gives CakePHP 2 plugins proper settings instead.

### Where plugin configs are consumed

- `pluginAndThemeTemplatePaths()` in `CakePaths.kt` probes every config for all three layouts
  whenever the matching version is enabled: `templates` (Cake 4+), `{srcPath}/Template` (Cake 3)
  and `View` (Cake 2). It already branches on `is PluginConfig` / `is ThemeConfig`. This is the
  one consumer where knowing the plugin's version changes behaviour.
- Dotted-name lookup (`findPluginConfigByName()`, used by `CakeView.kt` and
  `ConfiguredMemberService`) uses only `pluginPath` afterwards, except two places in
  `ConfiguredMemberService` that read `namespace` to build class FQNs. A CakePHP 2 config
  contributes an empty namespace there today, producing a harmless but bogus candidate such as
  `\Controller\Component\FooComponent`.
- Convention-based class lookup reads `namespace` from every config to build FQNs:
  `Classes.kt` (helper, component and table classes from a field name, and the helper,
  component and controller type strings), and `CakeModel.kt` (table classes from a possible
  table name). These run only when CakePHP 3+ is enabled and produce the same bogus
  candidates for a CakePHP 2 config.
- `ContainCompletionContributor` builds a list of allowed FQN prefixes from the app namespace
  and every config's `namespace`, and keeps a `Table` subclass when its FQN starts with any of
  them. A CakePHP 2 config contributes an empty prefix, which every FQN starts with, so one
  CakePHP 2 entry lets every `Table` subclass through. This is an existing bug the typed model
  fixes.
- `AssetCompletionContributor` uses `pluginPath`, `assetPath` and `effectivePluginName()` only.
- `cakeTwoPluginName()` on the disambiguation branch scans configured plugins by path.

A CakePHP 2 plugin needs a name, a path and an asset path. The CakePHP 2 model is therefore a
strict subset of the CakePHP 3+ one, and most consumers already go through the shared part.

## Design

### Data model: a sealed interface

`ThemeOrPluginConfig` stays as it is. `PluginConfig` becomes a sealed interface extending it that
adds `pluginName`, with two data classes:

- `CakeTwoPluginConfig(pluginName, pluginPath, assetPath)`. `pluginName` is required: there is
  no namespace to derive it from.
- `CakeThreePluginConfig(pluginName, namespace, srcPath, pluginPath, assetPath)`. This is the
  current class under a new name; `effectivePluginName()`'s derive-from-namespace fallback
  lives only here.

A sealed interface was chosen over a version enum on the single class because it removes
`namespace` and `srcPath` from the CakePHP 2 variant entirely. Every consumer that reads those
fields is forced by the compiler to decide what a CakePHP 2 plugin means to it, and the
serialized form excludes the irrelevant fields with no custom serializer.

With typed configs, `pluginAndThemeTemplatePaths()` probes only the layout the plugin has:
`View` for `CakeTwoPluginConfig`, `templates` and `{srcPath}/Template` for
`CakeThreePluginConfig`. Every namespace consumer (`ConfiguredMemberService`, `Classes.kt`,
`CakeModel.kt`, `ContainCompletionContributor`) iterates `CakeThreePluginConfig` only; a
`Settings.cakeThreePluginConfigs` accessor that filters the list keeps those call sites to a
one-word change. `cakeTwoPluginName()` on the disambiguation branch filters to
`CakeTwoPluginConfig` and drops its directory-name fallback.

### Serialization

`SettingsState.pluginConfigs` becomes a polymorphic `@XCollection` using `elementTypes`, with a
distinct `@Tag` per class so xmlb dispatches on the tag. This is the one assumption the design
rests on and has not been exercised in this codebase, so the first commit is a round-trip
serialization test in `SettingsTest` before any UI work.

Three persisted shapes exist:

- The existing `PluginConfig` tag. Deserialized into a transitional legacy class and classified
  on read (below). It is not written any more.
- A new tag for `CakeTwoPluginConfig`.
- A new tag for `CakeThreePluginConfig`.

The CakePHP 3+ class cannot keep the `PluginConfig` tag, because legacy entries must stay
distinguishable from entries the new dialog wrote. The legacy tag disappears from a user's
`ChocolateCakePHP.xml` the first time they save the settings page, since the form rewrites the
whole list through the new types.

### Classifying legacy entries

Legacy entries are classified lazily in the `Settings.pluginConfigs` getter, the same way the
deprecated `pluginNamespaces` list is synthesized today. Classifying in `loadState()` was
rejected: the rule depends on the CakePHP 3+ flag, which comes from composer auto-detection
through a cached service reading the VFS, and that is fragile during settings load.

The rule, in order:

1. The entry has a namespace: CakePHP 3+. A CakePHP 2 plugin has none, so a namespace is a
   deliberate CakePHP 3+ choice. "Has a namespace" means anything other than blank or a lone
   `\`: `PluginForm.setPluginConfig()` prefixes the namespace with a backslash when it writes
   the table row, so a CakePHP 2 entry saved through the current UI carries `\` rather than
   `""`.
2. `pluginPath` starts with `{cake2AppDirectory}/` (the setting, not the literal `app/`; plugin
   paths are always relative to the project root, as the settings form says): CakePHP 2.
3. CakePHP 2 is enabled and CakePHP 3+ is not: CakePHP 2.
4. Otherwise CakePHP 3+.

Rules 2 to 4 also pick the default version in the edit dialog, so adding a plugin in a CakePHP 2
project does not require flipping the selector each time.

**A legacy entry classified as CakePHP 2 may have no name.** The current dialog does not require
one (its duplicate check runs only when the name is non-empty), and a CakePHP 2 entry added
today typically has a blank name and a blank namespace, so `effectivePluginName()` is `""`. The
new `CakeTwoPluginConfig` requires a name and has no fallback, so classification supplies one:
the last segment of `pluginPath` with any trailing slash removed, `app/Plugin/DebugKit` giving
`DebugKit`. This is the same derivation `cakeTwoPluginName()` uses today, and the directory
name is the plugin name under CakePHP 2's loading rules. An entry with a blank name and a blank
path is dropped, since it identifies nothing. New CakePHP 2 entries get a name in the dialog,
so the derivation only ever runs on legacy entries.

Rule 3 only fires for CakePHP 2 projects where detection does not also enable CakePHP 3+.
Before version-aware detection, any `cakephp/cakephp` entry in `require` enabled CakePHP 3+, so
a CakePHP 2 app that lists `cakephp/cakephp` 2.x was left with rules 1 and 2. There, a legacy
entry for a CakePHP 2 plugin outside the app directory (root-level `plugins/Foo`, or a vendor
path) would have been classified CakePHP 3+ and lost its `View` probe. Version-aware detection,
described next, removes that case rather than a filesystem tie-breaker in the rule.

### Merging the deprecated namespace list

`Settings.pluginConfigs` also folds in the deprecated `pluginNamespaces` list, for settings that
predate `PluginConfig` and have never been re-saved (`PluginForm.apply()` clears the list).
`synthesizePluginEntries()` does this with a map keyed by namespace, which collapses every
CakePHP 2 config onto the one empty key. The merge becomes, in order:

1. Classify legacy entries, giving a list of typed configs.
2. For each deprecated namespace, append a synthesized `CakeThreePluginConfig` with that
   namespace unless some `CakeThreePluginConfig` in the list already has it.

No map is involved; typed configs are kept as they are, and CakePHP 2 configs take no part in
the comparison. The deprecated list is a list of namespaces, so it can only ever stand for
CakePHP 3+ plugins.

### Prerequisite: version-aware composer detection

Shipped as `version-aware-composer-detection` (PR #298), except the `ConfigForm` auto-detect
checkbox; see that feature's log. Shipped first, as its own branch, because it changes what the version toggles mean for existing
users.

The composer scanner already yields key/value pairs, so the constraint for `cakephp/cakephp` is
in hand. Strip the operator prefix (`^`, `~`, `>=`, `<`, `=`), and read the leading major
number: `2.10.*`, `~2.9`, `^2.0` and `>=2.5,<3.0` all give 2. Branch constraints such as
`dev-master` give no answer and keep today's behaviour.

With that:

- A 2.x constraint turns the CakePHP 3+ auto-detected flag off and a new CakePHP 2 detected flag
  on. Today both resolvers run over the same `.ctp` files in a CakePHP 2 app that has
  composer.json and the CakePHP 2 box ticked.
- CakePHP 2 gets the same auto-detect / force pair as CakePHP 3+ in `ConfigForm`, or at least
  "enabled when detected or ticked". `Settings.cake2Enabled` and the existing auto-detection
  tests change accordingly.
- Mixed mode (both enabled) becomes rare and intentional: composer requires one major version,
  so both flags on means a forced enable during a migration. Rule 1 covers migration-era
  CakePHP 2 plugins, which live under the CakePHP 2 app directory.
- Future auto-discovery of `{app}/Plugin/*` and `{root}/plugins/*` has a trustworthy gate:
  CakePHP 2 detected or manually enabled.

It does not remove the legacy deserialization class (distinguishing old entries from new ones is
independent of the rule) and does not remove the sealed model (mixed-mode projects still exist).

### Settings UI

- `EditPluginEntryDialog` (Swing `.form`) gets a version selector at the top. Selecting CakePHP
  2 hides the namespace and source path label/field pairs. The default follows the
  classification rule. For CakePHP 2 the name is required; it can be prefilled from the last
  segment of the plugin path.
- `PluginEntry`, the mutable mirror the table edits column by column, stays flat with an added
  version field; `toPluginConfig()` constructs the matching subtype. Replacing the
  column-by-column `setValueAt` in `PluginForm.setPluginConfig()` with whole-row replacement
  would be a simplification but is not required.
- `PluginTableModel` gains a Version column, so a CakePHP 2 row is distinguishable from a
  CakePHP 3+ row with an empty namespace. CakePHP 2 rows show blank namespace and source path.
- Duplicate-name validation in the dialog keeps working across both kinds, since it goes
  through `effectivePluginName()`.

### Later: auto-discovered CakePHP 2 plugins

The feature log's original idea of finding `{app}/Plugin/*` and `{root}/plugins/*` with no
settings becomes "synthesize `CakeTwoPluginConfig` entries in the `pluginConfigs` getter",
exactly as the legacy namespace list is synthesized. That would give template paths, asset
paths, plugin-aware template lookup, `{Plugin}AppController` recognition and dotted-name
navigation one source of plugin identity, and would let the disambiguation branch drop its
separate path scan. It is a second step once the model exists and detection is version-aware.

### Later: filtering same-named classes by plugin

Moved here from `configuration-code-disambiguation-cake2`, where it was the optional step 8.
CakePHP 2 classes are global, so `'DebugKit.Toolbar'` and an app's own `'Toolbar'` both resolve
to `\ToolbarComponent`, and `getClassesByFQN()` returns both classes when the app and a plugin
each define one. Filtering those results by the plugin each class's file belongs to would let:

- goto on `$this->Toolbar`, or on a `'DebugKit.Toolbar'` string in `$components`, go straight to
  the plugin's class, and a plain `'Toolbar'` to the app's, instead of offering a chooser;
- completion type text name the right class.

It cannot separate the inferred types, which are sets of FQN strings, so method completion on
`$this->Toolbar->` still unions both classes; nor same-named controllers, which share a
configuration in the resolver. The plugin of a load (its `Foo.` prefix, or none) would need to
reach `ConfiguredMemberService.loadClasses()` and the goto handlers. It belongs after this
feature because it depends on plugin identity, which this feature makes a single source.

## Implementation order

1. Separate branch: version-aware composer detection and the CakePHP 2 enable semantics.
2. Round-trip serialization test for a polymorphic `pluginConfigs` list with the three tags.
3. Sealed `PluginConfig`, the two data classes, the legacy class, and lazy classification in
   `Settings.pluginConfigs`. Update test base classes that construct `PluginConfig`
   (`Cake3BaseTestCase`, `Cake4BaseTestCase`, `Cake5BaseTestCase`, `PluginSupportTest`,
   `SettingsTest`).
4. Consumers: layout-specific probing in `pluginAndThemeTemplatePaths()`; the namespace readers
   `ConfiguredMemberService` (two sites), `Classes.kt` (six sites), `CakeModel.kt` (one) and
   `ContainCompletionContributor` (one) switched to CakePHP 3+ configs only; and (when the
   branches meet) `cakeTwoPluginName()`.
5. UI: dialog selector and field visibility, `PluginEntry` version field, table Version column.
6. Cake 2 tests: a configured `CakeTwoPluginConfig` resolves `{pluginPath}/View` templates,
   elements and assets; a legacy entry under the CakePHP 2 app directory is classified as
   CakePHP 2; a legacy entry with a blank name and a lone-backslash namespace gets its name
   from the last path segment and is found by `findPluginConfigByName()`; a legacy entry with a
   namespace stays CakePHP 3+ whatever its path; two CakePHP 2 configs survive a merge with a
   non-empty deprecated namespace list; and a CakePHP 2 config no longer widens contain
   completion to every `Table` subclass.

## Open questions

- Is auto-discovery of default-location plugins enough that the settings UI only needs to cover
  plugins in non-default locations (`App::build(array('Plugin' => ...))`)? Deferred until the
  model and detection are in place.

## Implementation Progress

### Session #1

- Step 1 shipped separately as `version-aware-composer-detection` (PR #298). Not done there:
  the "Auto-detect CakePHP 2" checkbox in `ConfigForm`. `cake2AutoDetect` defaults to on, and
  the existing checkbox now means force enable.
- Merged `main`, which also brings in `configuration-code-disambiguation-cake2`
  (`cakeTwoPluginName()` in `CakeTwoPlugins.kt`) and `cell-support`.
- Step 2 done: `PluginConfigSerializationTest` confirms that xmlb dispatches on the `@Tag` of
  each `elementTypes` class in a polymorphic `@XCollection`. A mixed list round-trips, a
  CakePHP 2 entry is written without `namespace` or `srcPath`, and entries saved by today's
  `SettingsState` load as the legacy class. It is a plain `TestCase` rather than part of the
  heavy `SettingsTest`, and for now it uses stand-in classes. Step 3 switches it to the real
  ones.
