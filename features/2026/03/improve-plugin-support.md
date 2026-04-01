# RootDirectory -> PluginRootDirectory + AppRootDirectory

## Goal

Split `RootDirectory` into two distinct types:
- `PluginRootDirectory` - contextual root of the plugin/app a view is in
- `AppRootDirectory` - always the main application root (project root for all CakePHP versions)

This fixes the conflation where `rootDirectoryFromViewFile` returned different things
for CakePHP 2 vs 3+, and makes plugin paths in settings always relative to project root.

## Key Changes

1. **CakePaths.kt**: Replace `RootDirectory` with `PluginRootDirectory` and `AppRootDirectory`
2. **CakePaths.kt**: Fix `appRootDir` in `allTemplatePathsFromTopSourceDirectory` to always be project root
3. **CakePaths.kt**: Rename and simplify root directory functions
4. **CakePaths.kt**: Add `appRootDirectoryFromViewFile` for cross-plugin/theme lookups
5. **CakePaths.kt**: Fix `assetDirectoryFromViewFile` to fall back for themed views
6. **AssetCompletionContributor.kt**: Use `AppRootDirectory` for plugin asset lookups
7. **AssetGotoDeclarationHandler.kt**: Use `AppRootDirectory` for plugin asset lookups
8. **PluginForm.form**: Update hints - paths are now always relative to project root

## Implementation Progress

### Session #1
