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
derives both from file paths instead of settings: `{app}/Plugin/{Name}/` and
`{root}/plugins/{Name}/` (the default `CakePlugin::load()` paths), plus any configured
`pluginPath`. This feature would give CakePHP 2 plugins proper settings instead.

## Ideas

- Detect CakePHP 2 plugins automatically from `{app}/Plugin/*` and `{root}/plugins/*`, named
  after their directory, with no settings needed for the usual layout.
- A CakePHP 2 plugin setting without a namespace or `srcPath`: a name and a path, for plugins
  outside the default directories (`App::build(array('Plugin' => ...))`).
- One source of plugin identity for CakePHP 2 (name and root directory) used by template paths,
  asset paths, plugin-aware template lookup, `{Plugin}AppController` recognition, and filtering
  same-named classes by plugin for navigation of dotted names such as `'DebugKit.Toolbar'`.
- Keep existing `PluginConfig` entries that point at CakePHP 2 plugins working, or migrate them.

## Open questions

- Is automatic detection enough for most CakePHP 2 apps, so the settings UI only needs to cover
  plugins in non-default locations?
- Should the settings UI show CakePHP 2 and CakePHP 3+ plugins separately, or one list whose
  fields depend on the version?
