# Chocolate CakePHP Translation Plan

**Branch:** add-translations
**Created:** 2025-10-23
**Status:** In Progress

## Overview

This document outlines the plan to add internationalization (i18n) support to the Chocolate CakePHP IntelliJ plugin. The plugin will be translated into 8 languages using AI-assisted translation with IntelliJ's resource bundle system.

### Target Languages

1. Spanish (es)
2. French (fr)
3. German (de)
4. Japanese (ja)
5. Portuguese (pt)
6. Danish (da)
7. Korean (ko)
8. Chinese (zh)

## Architecture

### Resource Bundle Structure

We'll use IntelliJ's standard resource bundle pattern:

```
src/main/resources/messages/
├── ChocolateCakePHPBundle.properties          # Base (English)
├── ChocolateCakePHPBundle_es.properties       # Spanish
├── ChocolateCakePHPBundle_fr.properties       # French
├── ChocolateCakePHPBundle_de.properties       # German
├── ChocolateCakePHPBundle_ja.properties       # Japanese
├── ChocolateCakePHPBundle_pt.properties       # Portuguese
├── ChocolateCakePHPBundle_da.properties       # Danish
├── ChocolateCakePHPBundle_ko.properties       # Korean
└── ChocolateCakePHPBundle_zh.properties       # Chinese
```

### Bundle Accessor Class

Create `src/main/kotlin/com/daveme/chocolateCakePHP/ChocolateCakePHPBundle.kt` to provide type-safe access to messages.

## Translatable String Inventory

### 1. Plugin Metadata (plugin.xml)

| Location | Current English Text | Message Key |
|----------|---------------------|-------------|
| Plugin name | Chocolate CakePHP | plugin.name |
| Plugin description | Autocompletion and navigation for CakePHP... | plugin.description |
| Settings name | Chocolate CakePHP | settings.title |
| Settings - Plugins | Plugins | settings.plugins.title |
| Settings - Data Views | Data Views | settings.dataviews.title |
| Action: Create Default View | Create Default CakePHP View File | action.createDefaultView.text |
| Action: Create Default View (desc) | Create a default view file for the CakePHP framework | action.createDefaultView.description |
| Action: Create Custom View | Create Custom CakePHP View File | action.createCustomView.text |
| Action: Create Custom View (desc) | Create a custom view file for the CakePHP framework | action.createCustomView.description |
| Action: Toggle Controller/View | Toggle Controller / View | action.toggleControllerView.text |
| Action: Toggle Controller/View (desc) | Toggle between controller and related view. | action.toggleControllerView.description |

### 2. Configuration Form (ConfigForm.form)

| UI Element | Current English Text | Message Key |
|------------|---------------------|-------------|
| Section title | CakePHP 3+ | config.cake3.title |
| Checkbox | Auto-detect CakePHP 3+ for this project | config.cake3.autoDetect.label |
| Checkbox | Force enable CakePHP 3+ for this project | config.cake3.forceEnable.label |
| Label | App Namespace | config.cake3.appNamespace.label |
| Help text | Classes under this namespace will be available for autocomplete. This is auto-detected. Force enable Cake 3 support to edit. | config.cake3.appNamespace.help |
| Label | App Directory | config.cake3.appDirectory.label |
| Help text | This is the top-level directory where your source code is located. | config.cake3.appDirectory.help |
| Label | Template Extension | config.cake3.templateExtension.label |
| Help text | This is the extension used for view files. By default this is **ctp**. **NOTE:** this is only for CakePHP 3. For CakePHP 4+, **php** extension is used. | config.cake3.templateExtension.help |
| Button | Default | config.button.default |
| Section title | CakePHP 2 | config.cake2.title |
| Checkbox | Enable Cake 2 support | config.cake2.enable.label |
| Label | App Directory | config.cake2.appDirectory.label |
| Label | Template Extension | config.cake2.templateExtension.label |

### 3. Plugin Form (PluginForm.form)

| UI Element | Current English Text | Message Key |
|------------|---------------------|-------------|
| Section title | Plugins | pluginForm.plugins.title |
| Help text | Add configuration for each plugin you want to add autocomplete for below. | pluginForm.plugins.help |
| Section title | Themes | pluginForm.themes.title |
| Help text | Add configuration for each theme you want to add navigation for below | pluginForm.themes.help |

### 4. Data Views Form (DataViewsForm.form)

| UI Element | Current English Text | Message Key |
|------------|---------------------|-------------|
| Section title | CakePHP Data Views | dataViewsForm.title |
| Help text | Add any custom data view file extensions you want to check for below. This will be checked when navigating from a controller to a view file corresponding to an action. | dataViewsForm.help |

### 5. Navigation Popups (ViewNavigationPopup.kt)

| Context | Current English Text | Message Key |
|---------|---------------------|-------------|
| Popup title | Select Target to Navigate | navigation.selectTarget.title |
| Create action | Create {label} | navigation.create.label |
| Create custom | Create Custom View File | navigation.createCustom.label |
| Create data view | Create {label} View File | navigation.createDataView.label |

### 6. View File Creation (CreateViewFileAction.kt)

| Context | Current English Text | Message Key |
|---------|---------------------|-------------|
| Dialog title | View file path | createView.dialog.title |
| Dialog title alt | Create View File | createView.dialog.createTitle |
| Command name | Create View File | createView.command.name |
| Error: Failed dirs | Failed to create directories | createView.error.createDirectories |
| Error: Failed find dir | Failed to find directory | createView.error.findDirectory |
| Error: File exists | File already exists | createView.error.fileExists |

### 7. Edit Dialogs

Need to examine:
- EditEntryDialog.form
- EditPluginEntryDialog.form
- EditThemeEntryDialog.form

## Implementation Checklist

### Phase 1: Infrastructure Setup
- [ ] Create `src/main/resources/messages/` directory
- [ ] Create `ChocolateCakePHPBundle.kt` message bundle accessor class
- [ ] Create base `ChocolateCakePHPBundle.properties` with all English strings

### Phase 2: Create Translations
- [ ] Generate Spanish translation (es)
- [ ] Generate French translation (fr)
- [ ] Generate German translation (de)
- [ ] Generate Japanese translation (ja)
- [ ] Generate Portuguese translation (pt)
- [ ] Generate Danish translation (da)
- [ ] Generate Korean translation (ko)
- [ ] Generate Chinese translation (zh)

### Phase 3: Code Refactoring
- [ ] Update `plugin.xml` to use message bundle keys
- [ ] Update `ConfigForm.form` to use resource bundle references
- [ ] Update `ConfigForm.java` to use ChocolateCakePHPBundle
- [ ] Update `PluginForm.form` to use resource bundle references
- [ ] Update `PluginForm.java` to use ChocolateCakePHPBundle
- [ ] Update `DataViewsForm.form` to use resource bundle references
- [ ] Update `DataViewsForm.java` to use ChocolateCakePHPBundle
- [ ] Update `CreateViewFileAction.kt` to use ChocolateCakePHPBundle
- [ ] Update `ViewNavigationPopup.kt` to use ChocolateCakePHPBundle
- [ ] Update edit dialog forms (3 files)
- [ ] Update edit dialog Java classes

### Phase 4: Testing
- [ ] Test with English locale
- [ ] Test with Spanish locale
- [ ] Test with French locale
- [ ] Test with German locale
- [ ] Test with Japanese locale
- [ ] Test with Portuguese locale
- [ ] Test with Danish locale
- [ ] Test with Korean locale
- [ ] Test with Chinese locale
- [ ] Verify fallback to English for unsupported locales
- [ ] Test all UI forms render correctly
- [ ] Test all actions display translated text
- [ ] Test all error messages are translated

## Translation Context Notes

### Technical Terms to Preserve
These terms should remain in English or use standard translations:
- CakePHP (brand name - keep as-is)
- Controller, View, Model, Component, Helper (MVC terms - translate or keep standard)
- namespace (programming term)
- autocomplete/autocompletion

### UI String Guidelines
1. Keep button text concise (especially for "Default" buttons)
2. Help text can be longer and more descriptive
3. Error messages should be clear and actionable
4. Dialog titles should be brief
5. Settings section titles should match IDE conventions

### Format Preservation
- HTML tags in form descriptions must be preserved
- Bold markers (**text**) in help text must be preserved
- Variable placeholders like {label} must be preserved

## File Modification Summary

### Files to Create (11)
1. `src/main/kotlin/com/daveme/chocolateCakePHP/ChocolateCakePHPBundle.kt`
2. `src/main/resources/messages/ChocolateCakePHPBundle.properties`
3-10. Eight language-specific `.properties` files

### Files to Modify (15+)
1. `src/main/resources/META-INF/plugin.xml`
2. `src/main/java/com/daveme/chocolateCakePHP/ConfigForm.form`
3. `src/main/java/com/daveme/chocolateCakePHP/ConfigForm.java`
4. `src/main/java/com/daveme/chocolateCakePHP/PluginForm.form`
5. `src/main/java/com/daveme/chocolateCakePHP/PluginForm.java`
6. `src/main/java/com/daveme/chocolateCakePHP/DataViewsForm.form`
7. `src/main/java/com/daveme/chocolateCakePHP/DataViewsForm.java`
8. `src/main/java/com/daveme/chocolateCakePHP/EditEntryDialog.form`
9. `src/main/java/com/daveme/chocolateCakePHP/EditPluginEntryDialog.form`
10. `src/main/java/com/daveme/chocolateCakePHP/EditThemeEntryDialog.form`
11. `src/main/kotlin/com/daveme/chocolateCakePHP/controller/CreateViewFileAction.kt`
12. `src/main/kotlin/com/daveme/chocolateCakePHP/ViewNavigationPopup.kt`
13-15. Corresponding Java classes for edit dialogs

## Technical Implementation Details

### IntelliJ Resource Bundle API
- Use `DynamicBundle` class from `com.intellij.DynamicBundle`
- Or use `AbstractBundle` from `com.intellij.AbstractBundle`
- Message keys follow dot notation: `section.subsection.key`

### Property File Format
```properties
# English base
plugin.name=Chocolate CakePHP
plugin.description=Autocompletion and navigation for CakePHP.
```

### Bundle Accessor Pattern
```kotlin
object ChocolateCakePHPBundle : DynamicBundle(BUNDLE_NAME) {
    private const val BUNDLE_NAME = "messages.ChocolateCakePHPBundle"

    fun message(key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
}
```

### Usage in Code
```kotlin
// Before
Messages.showInputDialog(project, "View file path", ...)

// After
Messages.showInputDialog(
    project,
    ChocolateCakePHPBundle.message("createView.dialog.title"),
    ...
)
```

## Post-Implementation Tasks

1. **Review Translations**: Request native speaker review for each language
2. **Documentation**: Update README.md to mention multi-language support
3. **Marketing**: Update JetBrains Marketplace listing with language support info
4. **Future Maintenance**: Document process for adding new translatable strings

## Success Criteria

- [ ] All user-facing strings are externalized to resource bundles
- [ ] All 8 target languages have complete translations
- [ ] Plugin displays text in user's IDE locale
- [ ] No English strings hardcoded in source files
- [ ] All forms render correctly in all languages
- [ ] Plugin passes all existing tests in all locales
