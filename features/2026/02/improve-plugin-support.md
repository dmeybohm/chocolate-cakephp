# Improve CakePHP Plugin Support (Issue #263)

## Overview

Add support for CakePHP's plugin dot notation (`PluginName.resource`) in navigation and completions for assets, elements, and templates.

## Goals

1. **Navigation**: Support Ctrl+Click on `PluginName.asset`, `PluginName.element_name`, `PluginName.template`
2. **Completions**: Show plugin-prefixed completions like `MyPlugin.style` in asset methods

## Implementation

### Root Cause Analysis

The original implementation used `project.guessProjectDir()` to resolve plugin paths, which doesn't work correctly when:
- Running tests (fixtures are under `cake5/...` paths)
- The CakePHP app is in a subdirectory of the project

**Regular asset resolution** (worked):
- Uses `assetDirectoryFromViewFile()` which starts from the view file
- Walks UP from view file -> finds templates directory -> finds webroot as sibling
- Path is resolved **relative to view file location**

**Plugin asset resolution** (original, broken):
- Used `project.guessProjectDir()` to get project root
- Resolved `pluginConfig.pluginPath` from that root
- Path was resolved **from project root**

### Solution

Modify plugin path resolution to work like regular assets - resolve relative to the view file's root context, not from `project.guessProjectDir()`.

### Key Changes

1. **CakePaths.kt**:
   - Added `rootDirectoryFromViewFile()` function to get the app root from view file context
   - Modified `pluginAndThemeTemplatePaths()` to accept `appRootDir` parameter instead of using `guessProjectDir()`
   - Updated `allTemplatePathsFromTopSourceDirectory()` to calculate and pass the correct app root

2. **Settings.kt**:
   - Added `findPluginConfigByName()` extension function to look up plugins by name

3. **CakeView.kt**:
   - Added `PluginResourcePath` data class for parsing plugin dot notation
   - Added `parsePluginResourcePath()` function to extract plugin name and resource path
   - Added `allViewPathsFromPluginTemplate()` and `allViewPathsFromPluginElementPath()` for plugin-specific path resolution

4. **AssetGotoDeclarationHandler.kt**:
   - Updated to resolve plugin paths relative to the view file's root directory
   - Added fallback logic: if a parsed "plugin name" doesn't match a configured plugin, treat it as a normal path (fixes `pluginIcon.svg` case)

5. **AssetCompletionContributor.kt**:
   - Same fix as AssetGotoDeclarationHandler

6. **ElementGotoDeclarationHandler.kt**:
   - Updated to use `viewFilesFromAllViewPaths` instead of `allViewPathsToFiles` for proper template directory resolution
   - Added support for plugin dot notation in element paths

7. **TemplateGotoDeclarationHandler.kt**:
   - Added support for plugin dot notation in render(), setTemplate(), and $this->view assignments

## Test Strategy

### Test Fixtures

Created plugin test fixtures for CakePHP 5:
```
src/test/fixtures/cake5/plugins/TestPlugin/
    webroot/
        css/plugin_style.css
        js/plugin_script.js
        img/plugin_logo.png
    templates/
        element/sidebar.php
        TestController/index.php
```

### Test Class

Created `PluginSupportTest.kt` with 11 test cases covering:
- Asset navigation (CSS, JS, image with plugin prefix)
- Asset navigation in arrays
- Non-prefixed assets still work
- Unknown plugin returns empty results
- Element navigation with plugin prefix
- Template navigation with plugin prefix (render, setTemplate)

## Implementation Progress

### Session #1 (2026-02)

- Analyzed root cause of test failures
- Implemented path resolution fix across all affected handlers
- Fixed edge case where filenames with dots (e.g., `pluginIcon.svg`) were incorrectly parsed as plugin prefixes
- All 571 tests pass including 11 new plugin support tests
