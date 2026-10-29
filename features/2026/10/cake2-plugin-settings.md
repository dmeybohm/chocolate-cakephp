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
`CakeThreePluginConfig`. `ConfiguredMemberService` collects namespaces from
`CakeThreePluginConfig` only. `cakeTwoPluginName()` on the disambiguation branch filters to
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

1. `pluginPath` starts with `{cake2AppDirectory}/` (the setting, not the literal `app/`; plugin
   paths are always relative to the project root, as the settings form says): CakePHP 2.
2. CakePHP 2 is enabled and CakePHP 3+ is not: CakePHP 2.
3. Otherwise CakePHP 3+.

The same rule picks the default version in the edit dialog, so adding a plugin in a CakePHP 2
project does not require flipping the selector each time.

Rule 2 only fires for CakePHP 2 projects where composer detection does not also enable CakePHP
3+. Today detection sets the CakePHP 3+ flag whenever `cakephp/cakephp` appears in `require`,
with no version check, so a CakePHP 2 app that lists `cakephp/cakephp` 2.x counts as CakePHP 3+
and only rule 1 applies there. In such a project a legacy entry for a CakePHP 2 plugin outside
the app directory (root-level `plugins/Foo`, or a vendor path) would be classified CakePHP 3+
and lose its `View` probe. The fix for that is version-aware detection, described next, rather
than a filesystem tie-breaker in the rule.

### Prerequisite: version-aware composer detection

Shipped first, as its own branch, because it changes what the version toggles mean for existing
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

## Implementation order

1. Separate branch: version-aware composer detection and the CakePHP 2 enable semantics.
2. Round-trip serialization test for a polymorphic `pluginConfigs` list with the three tags.
3. Sealed `PluginConfig`, the two data classes, the legacy class, and lazy classification in
   `Settings.pluginConfigs`. Update test base classes that construct `PluginConfig`
   (`Cake3BaseTestCase`, `Cake4BaseTestCase`, `Cake5BaseTestCase`, `PluginSupportTest`,
   `SettingsTest`).
4. Consumers: layout-specific probing in `pluginAndThemeTemplatePaths()`, namespace collection
   in `ConfiguredMemberService`, and (when the branches meet) `cakeTwoPluginName()`.
5. UI: dialog selector and field visibility, `PluginEntry` version field, table Version column.
6. Cake 2 tests: a configured `CakeTwoPluginConfig` resolves `{pluginPath}/View` templates,
   elements and assets, and a legacy entry under the CakePHP 2 app directory is classified as
   CakePHP 2.

## Open questions

- Is auto-discovery of default-location plugins enough that the settings UI only needs to cover
  plugins in non-default locations (`App::build(array('Plugin' => ...))`)? Deferred until the
  model and detection are in place.
