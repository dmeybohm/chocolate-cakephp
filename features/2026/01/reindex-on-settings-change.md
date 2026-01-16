# Reindex on Settings Change

## Problem

When a user opens a CakePHP project and then enables the Chocolate CakePHP plugin from the settings dialog, the indexes don't function properly until the user manually invalidates caches and restarts the IDE.

This happens because:
1. The indexes (`ViewVariableIndex`, `ViewFileIndex`) check `settings.enabled` during indexing
2. If the plugin was disabled when the project was first indexed, the indexes contain empty data
3. Changing settings via the UI doesn't trigger a reindex - the stale empty data remains

## Solution

Detect when plugin settings that affect indexing change, and request a rebuild of the plugin's indexes using `FileBasedIndex.requestRebuild()`. This causes IntelliJ to re-index the PHP files with the updated settings.

### Settings That Trigger Reindex

**In ConfigForm (main settings):**
- Enable/disable state (`cake2Enabled`, `cake3Enabled`, `cake3ForceEnabled`)
- Template extensions (`cakeTemplateExtension`, `cake2TemplateExtension`)
- App directories (`appDirectory`, `cake2AppDirectory`)
- App namespace (`appNamespace`)

**In PluginForm (plugin/theme settings):**
- Plugin configurations (`pluginConfigs`)
- Theme configurations (`themeConfigs`)

## Implementation

### ConfigForm.java Changes

Modified `apply()` to:
1. Capture relevant settings values before applying changes
2. Apply the new settings
3. Compare old vs new values
4. If any indexing-relevant setting changed, call `requestIndexRebuild()`

### PluginForm.java Changes

Modified `apply()` to:
1. Capture plugin and theme configs before applying changes
2. Apply the new settings
3. If configs changed and plugin is enabled, call `requestIndexRebuild()`

### requestIndexRebuild() Method

Both forms have a private helper method:

```java
private void requestIndexRebuild() {
    FileBasedIndex fileIndex = FileBasedIndex.getInstance();
    fileIndex.requestRebuild(ViewFileIndexServiceKt.getVIEW_FILE_INDEX_KEY());
    fileIndex.requestRebuild(ViewVariableIndexServiceKt.getVIEW_VARIABLE_INDEX_KEY());
}
```

## Files Modified

- `src/main/java/com/daveme/chocolateCakePHP/ConfigForm.java`
- `src/main/java/com/daveme/chocolateCakePHP/PluginForm.java`

## Testing

1. Open a CakePHP 2 project with the plugin disabled
2. Enable "Enable CakePHP 2 Support" checkbox in settings
3. Click Apply/OK
4. Verify that view variable completions work without invalidating caches

Similar tests for CakePHP 3+ projects with auto-detect enabled.

## Notes

- `FileBasedIndex.requestRebuild()` marks indexes for rebuild; IntelliJ schedules the work
- User will see a brief "Indexing..." progress indicator after settings change
- No IDE restart required for the changes to take effect
