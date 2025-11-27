# Implementation: "Go to Related Item" for Controller ↔ View Navigation

## Overview

Enabled IntelliJ's **Navigate → Related Symbol** (Ctrl+Alt+Home / ⌃⌘↑) feature for Controller ↔ View navigation by upgrading the existing `ControllerMethodLineMarker` to use `RelatedItemLineMarkerProvider`.

## Problem Statement

The plugin had existing navigation features:
- **Toggle Action**: Ctrl+Alt+Shift+HOME for direct Controller ↔ View toggle
- **Gutter Icons**: Line markers with navigation on click

However, the standard IntelliJ keyboard shortcut "Go to Related Item" (Ctrl+Alt+Home / ⌃⌘↑) was not integrated.

## Research Findings

### Key Discovery

The existing code already created `RelatedItemLineMarkerInfo` objects via `NavigationGutterIconBuilder.createLineMarkerInfo()`, which returns a `RelatedItemLineMarkerInfo` that supports the Related Symbol feature.

The only missing piece was using the correct base class: `RelatedItemLineMarkerProvider` instead of `LineMarkerProvider`.

### Alternative Strategies Considered

1. **Create a separate `RelatedItemLineMarkerProvider`** (Rejected)
   - Would duplicate navigation logic
   - Would create redundant gutter icons
   - More complex to maintain

2. **Use `GotoRelatedProvider` extension point** (Rejected)
   - Would not provide gutter icons
   - Would duplicate logic from existing line marker
   - Requires separate implementation

3. **Minimal change to existing implementation** (Selected) ✓
   - Change base class from `LineMarkerProvider` to `RelatedItemLineMarkerProvider`
   - Update method from `collectSlowLineMarkers` to `collectNavigationMarkers`
   - Adapt single-element processing
   - Zero logic changes required

## Implementation Details

### Changes Made

**File**: `src/main/kotlin/com/daveme/chocolateCakePHP/controller/ControllerMethodLineMarker.kt`

#### 1. Updated Imports
```kotlin
// Before:
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider

// After:
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
```

#### 2. Changed Base Class
```kotlin
// Before:
class ControllerMethodLineMarker : LineMarkerProvider

// After:
class ControllerMethodLineMarker : RelatedItemLineMarkerProvider()
```

#### 3. Removed Unused Method
```kotlin
// Removed (not used by RelatedItemLineMarkerProvider):
override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? = null
```

#### 4. Updated Method Signature
```kotlin
// Before:
override fun collectSlowLineMarkers(
    elements: MutableList<out PsiElement>,
    result: MutableCollection<in LineMarkerInfo<*>>
)

// After:
override fun collectNavigationMarkers(
    element: PsiElement,
    result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
)
```

#### 5. Adapted to Single-Element Processing
```kotlin
// Before: Processed list of elements in a loop
for (element in elements) {
    // ...
}

// After: Process single element directly
val file = element.containingFile ?: return
// ...
```

#### 6. Updated Return Types
All helper methods updated to return `RelatedItemLineMarkerInfo<*>` instead of `LineMarkerInfo<*>`:
- `markerForAllViewFilesInAction()`
- `markerForSingleRenderCallInAction()`
- `markerForSingleViewBuilderCallInAction()`
- `markerForViewAssignment()`
- `relatedItemLineMarkerInfo()`
- `addLineMarkerUnique()`

### Why This Works

1. **NavigationGutterIconBuilder** already creates `RelatedItemLineMarkerInfo` objects
2. **RelatedItemLineMarkerInfo** contains the `createGotoRelatedItems()` method
3. **RelatedItemLineMarkerProvider** automatically integrates with IntelliJ's Related Symbol feature
4. **No logic changes** needed - all existing navigation, custom handlers, and popup behavior preserved

## Testing

### Existing Tests - All Passed ✓

Ran all 15 ControllerLineMarkerTest tests:
```
ControllerLineMarkerTest > test that line marker is not added to setTemplatePath calls PASSED
ControllerLineMarkerTest > test that line marker is added to chained viewBuilder calls PASSED
ControllerLineMarkerTest > test that line marker respects preceding setTemplatePath in same method PASSED
ControllerLineMarkerTest > test that line marker is added to view field assignments PASSED
ControllerLineMarkerTest > test that line marker is added to view field assignments with nested path PASSED
ControllerLineMarkerTest > test that a line marker is added next to render call when a corresponding view file doesn't exist PASSED
ControllerLineMarkerTest > test that line marker navigates to explicit render() calls PASSED
ControllerLineMarkerTest > test that line markers contain one entry for each type of view PASSED
ControllerLineMarkerTest > test that line marker is added to viewBuilder setTemplate calls PASSED
ControllerLineMarkerTest > test that a line marker is added to the method name when a corresponding view file doesn't exist PASSED
ControllerLineMarkerTest > test that line marker handles chained calls with comment between PASSED
ControllerLineMarkerTest > test that line marker navigates to explicit render() calls when nested PASSED
ControllerLineMarkerTest > test that line marker adds markers to render calls PASSED
ControllerLineMarkerTest > test that line marker handles chained calls with extra whitespace PASSED
ControllerLineMarkerTest > test that line marker handles chained calls with newline PASSED
```

**Result**: BUILD SUCCESSFUL - All tests passing confirms existing functionality is fully preserved.

### New Functionality

The implementation now supports:
- **Navigate → Related Symbol** popup (Ctrl+Alt+Home / ⌃⌘↑) on controller methods
- Shows related view files in the Related Symbol popup
- Maintains all existing features:
  - Gutter icons with tooltips
  - Click navigation to view files
  - Multi-view popup selection
  - Custom navigation handler (Ctrl+Click to create)
  - Support for all CakePHP versions (2, 3, 4, 5)

## Benefits

1. **Minimal Code Change**: Only base class and method signatures updated
2. **Zero Logic Changes**: All navigation logic remains unchanged
3. **Preserved Features**: All existing functionality still works
4. **Automatic Integration**: Related Symbol feature works immediately
5. **No Test Disruption**: All existing tests pass without modification
6. **Better UX**: Users can now use standard IntelliJ shortcut for navigation

## Complementary Features

Users now have multiple navigation options:
1. **Ctrl+Alt+Shift+HOME** - Direct toggle action (existing)
2. **Ctrl+Alt+Home / ⌃⌘↑** - Navigate → Related Symbol popup (new)
3. **Gutter icon click** - Navigate from icon (existing)
4. **Ctrl+Click on gutter** - Create new view file (existing)

All methods work bidirectionally (Controller ↔ View).

## Related Documentation

- Research from: `features/2025/10/goto-related-items-changes.md`
- IntelliJ Platform API: `RelatedItemLineMarkerProvider`, `RelatedItemLineMarkerInfo`
- Test infrastructure: `BaseTestCase.kt` provides `gotoRelatedItems()` helper

## Implementation Status

✅ **Controller → View Complete** - Minimal solution implemented and tested successfully.

---

## Part 2: View → Controller Navigation

### Limitation Discovered

The initial implementation only works for **Controller → View** navigation because `RelatedItemLineMarkerProvider` requires gutter icons (line markers), and it's awkward to place line markers in view files:
- Where would the icon go? First line? PHP tag? Nowhere feels natural
- View files already have a toolbar icon for navigation
- Adding gutter icons would create visual clutter

**Result**: The "Go to Related Item" shortcut works from controllers but not from views.

### Design Decision

**Do NOT add line markers to view files.** Instead, use `GotoRelatedProvider` which contributes to the Navigate → Related Symbol popup WITHOUT showing gutter icons.

### Alternative Approaches Considered

1. **Add ViewMethodLineMarkerProvider with gutter icons** (Rejected)
   - Awkward placement of icons in view files
   - Visual clutter (toolbar icon already exists)
   - Inconsistent with view file UI conventions

2. **Use GotoRelatedProvider** (Selected) ✓
   - NO gutter icons (clean!)
   - Integrates with Navigate → Related Symbol popup
   - Works on any element in view file
   - Lightweight (only computes when user requests it)
   - Complements existing controller line markers

### View → Controller Architecture

**Extension Point**: `com.intellij.gotoRelatedProvider`

**Key Interface**:
```kotlin
interface GotoRelatedProvider {
    fun getItems(psiElement: PsiElement): Collection<GotoRelatedItem>
}
```

**Infrastructure Already Exists**:
- `ViewFileIndexService.referencingElementsInSmartReadAction()` - Finds controller references to view
- `ViewFileIndex` - File-based index mapping views → controller render calls
- `ToggleBetweenControllerAndViewAction` - Already uses this infrastructure for toggle feature

### Implementation: ViewToControllerGotoRelatedProvider

**File**: `src/main/kotlin/com/daveme/chocolateCakePHP/view/ViewToControllerGotoRelatedProvider.kt`

**Key Logic**:
1. Check if element is in a view file using `isCakeViewFile()`
2. Get templates directory and relative path
3. Create canonical filename key for indexing
4. Call `ViewFileIndexService.referencingElementsInSmartReadAction()`
5. Convert controller PSI elements to `GotoRelatedItem` objects
6. Group under "Controllers" category

**Registration** in `plugin.xml`:
```xml
<gotoRelatedProvider implementation="com.daveme.chocolateCakePHP.view.ViewToControllerGotoRelatedProvider"/>
```

### Comparison: LineMarker vs GotoRelatedProvider

| Feature | RelatedItemLineMarkerProvider | GotoRelatedProvider |
|---------|-------------------------------|---------------------|
| Gutter Icon | YES | NO |
| Related Symbol Popup | YES | YES |
| Keyboard Shortcut | Ctrl+Alt+Home | Ctrl+Alt+Home |
| Visual Impact | High (adds icons) | None (no icons) |
| Best For | Controllers | Views |

### Complete User Experience

**From Controller Files**:
- ✓ Gutter icons show on method names (visual indicator)
- ✓ Click gutter to navigate to views
- ✓ Ctrl+Alt+Home shows Related Symbol popup with views
- ✓ Ctrl+Click gutter to create new view file

**From View Files**:
- ✓ NO gutter icons (clean, no clutter!)
- ✓ Ctrl+Alt+Home shows Related Symbol popup with controllers
- ✓ Toolbar icon still available for navigation
- ✓ Toggle action (Ctrl+Alt+Shift+HOME) still works

**Bidirectional Navigation**:
- Same keyboard shortcut works in both file types
- Consistent IntelliJ UX across the plugin
- Toggle action provides alternative workflow
- All CakePHP versions supported (2, 3, 4, 5)

### Benefits of This Approach

1. **No Visual Clutter**: View files remain clean without gutter icons
2. **Standard IntelliJ Integration**: Uses platform's Related Symbol feature
3. **Minimal Code**: Reuses existing ViewFileIndexService infrastructure
4. **Complementary**: Works alongside existing controller line markers
5. **Lightweight**: Only computes related items when explicitly requested
6. **Consistent UX**: Same keyboard shortcut works everywhere

### Testing Plan

**Test Files**: Create `ViewToControllerGotoRelatedTest` in test directories

**Test Cases**:
- View file → Single controller method
- View file → Multiple controller methods
- Plugin/theme template paths
- All CakePHP versions (2, 3, 4, 5)
- Verify correct grouping in Related Symbol popup
- Verify no impact on existing ControllerLineMarkerTest

## Final Implementation Status

✅ **Controller → View** - Complete (RelatedItemLineMarkerProvider with gutter icons)
✅ **View → Controller** - Complete (GotoRelatedProvider without gutter icons)

### Files Modified/Created

1. **Controller → View** (Part 1):
   - Modified: `src/main/kotlin/com/daveme/chocolateCakePHP/controller/ControllerMethodLineMarker.kt`
   - Changed base class to `RelatedItemLineMarkerProvider`
   - Updated method signatures
   - All existing tests pass

2. **View → Controller** (Part 2):
   - Created: `src/main/kotlin/com/daveme/chocolateCakePHP/view/ViewToControllerGotoRelatedProvider.kt`
   - Modified: `src/main/resources/META-INF/plugin.xml` (added gotoRelatedProvider)
   - Reuses existing ViewFileIndexService infrastructure
   - No gutter icons in view files (by design)

### How to Use

**From Controller**:
- Open any controller file
- Position cursor on a public method (action)
- Press **Ctrl+Alt+Home** (or **⌃⌘↑** on Mac)
- See Related Symbol popup with view files
- Alternatively: Click gutter icon, or use Ctrl+Alt+Shift+HOME toggle

**From View**:
- Open any view file (template)
- Press **Ctrl+Alt+Home** (or **⌃⌘↑** on Mac)
- See Related Symbol popup with controller methods
- Alternatively: Use Ctrl+Alt+Shift+HOME toggle or toolbar icon

Both directions use the same standard IntelliJ keyboard shortcut for a consistent user experience.

---

## Bug Fix Session: NullPointerException in Related Symbol Popup

### Issue Discovery

After implementing the "Go to Related Item" feature, a `NullPointerException` was encountered when using the keyboard shortcut (Ctrl+Alt+Home / ⌃⌘↑) for both View→Controller and Element→Views/Elements navigation.

**Exception Details**:
```
java.lang.NullPointerException: getIcon(...) must not be null
	at com.intellij.codeInsight.navigation.NavigationUtil$getPsiElementPopup$renderer$1.getIcon(NavigationUtil.kt:331)
```

### Root Cause Analysis

Both `ViewToControllerGotoRelatedProvider` and `ElementToUsagesGotoRelatedProvider` created custom `GotoRelatedItem` objects that overrode `getCustomIcon()` to return `null`:

```kotlin
override fun getCustomIcon(): Icon? {
    // No custom icon - use default PSI element icon
    return null
}
```

When IntelliJ's navigation popup renderer attempted to display the Related Symbol popup, it expected a non-null icon from `getIcon()`. The null return value caused the exception.

**Key Insight**: When creating custom `GotoRelatedItem` implementations with overridden methods, all overridden methods that return non-nullable types in the platform API must return actual values, not null.

### Solution

The fix was to implement proper icon logic similar to the existing `CakePhpNavigationPresentationProvider` (used in the Controller→View line marker navigation popup).

**Icon Logic Pattern**:
- Controller files → `CakeIcons.LOGO_SVG`
- View/Element files → `PhpIcons.PHP_FILE`
- Fallback (no path) → `PhpIcons.FUNCTION`

### Implementation

**1. ViewToControllerGotoRelatedProvider.kt** (lines 99-109):
```kotlin
override fun getCustomIcon(): Icon {
    // Use CakePHP logo for controller files, similar to CakePhpNavigationPresentationProvider
    val path = element.containingFile?.virtualFile?.path
    return if (path == null) {
        PhpIcons.FUNCTION
    } else if (path.contains("/Controller/")) {
        CakeIcons.LOGO_SVG
    } else {
        PhpIcons.FUNCTION
    }
}
```

**2. ElementToUsagesGotoRelatedProvider.kt** (lines 129-139):
```kotlin
override fun getCustomIcon(): Icon {
    // Use appropriate icons based on file type, similar to CakePhpNavigationPresentationProvider
    val path = element.containingFile?.virtualFile?.path
    return if (path == null) {
        PhpIcons.FUNCTION
    } else if (path.contains("/Controller/")) {
        CakeIcons.LOGO_SVG
    } else {
        PhpIcons.PHP_FILE
    }
}
```

**3. Added Imports**:
Both files needed:
```kotlin
import com.daveme.chocolateCakePHP.cake.CakeIcons
import com.jetbrains.php.PhpIcons
```

### Testing Results

All tests passed successfully after the fix:
- **15 ControllerLineMarkerTest tests** - All PASSED (no regressions)
- **7 ElementToUsagesGotoRelatedTest tests** - All PASSED
- **5 ViewToControllerGotoRelatedTest tests** - All PASSED

**Total: 27 tests PASSED** ✓

### Lessons Learned

1. **Platform API Contracts**: When overriding platform methods that return non-nullable types, always provide actual values, even if the documentation suggests null might be acceptable.

2. **Consistency**: Reusing icon logic patterns from existing implementations (like `CakePhpNavigationPresentationProvider`) ensures consistent UX and reduces bugs.

3. **Testing Before Manual Verification**: Running automated tests caught the compilation issue with the wrong `PhpIcons` import path (`com.jetbrains.php.lang.PhpIcons` vs `com.jetbrains.php.PhpIcons`) before manual testing.

4. **Icon Semantics**: Different file types should have semantically appropriate icons:
   - Controllers get the CakePHP logo (framework integration)
   - Views/Elements get the PHP file icon (template files)
   - Unknown/fallback gets the function icon

### Files Modified

1. `src/main/kotlin/com/daveme/chocolateCakePHP/view/ViewToControllerGotoRelatedProvider.kt`
   - Added imports for `CakeIcons` and `PhpIcons`
   - Replaced null-returning `getCustomIcon()` with proper icon logic

2. `src/main/kotlin/com/daveme/chocolateCakePHP/view/ElementToUsagesGotoRelatedProvider.kt`
   - Added imports for `CakeIcons` and `PhpIcons`
   - Replaced null-returning `getCustomIcon()` with proper icon logic

### Verification

The fix was verified by:
1. Compilation success (no more unresolved references)
2. All 27 automated tests passing
3. Manual testing would confirm the Related Symbol popup now displays with proper icons

---

## Bug Fix Session 2: EDT Threading Violation

### Issue Discovery

After fixing the icon NullPointerException, a new exception was encountered during actual usage of the "Go to Related Item" feature:

**Exception Details**:
```
com.intellij.diagnostic.PluginException: PSI element is provided on EDT by
com.intellij.ui.popup.list.ListPopupImpl$MyList.getData("selectedItem").
Please move that to a BGT data provider using PlatformCoreDataKeys.BGT_DATA_PROVIDER
```

### Root Cause Analysis

The exception occurs because our `GotoRelatedItem` implementations were accessing PSI elements during popup rendering, which happens on the Event Dispatch Thread (EDT). IntelliJ's threading model requires that PSI element access must occur on Background Threads (BGT) only, to prevent UI blocking.

**Problematic Pattern:**

In both providers, the overridden methods accessed PSI elements directly:

```kotlin
override fun getCustomName(): String {
    val method = PsiTreeUtil.getParentOfType(element, Method::class.java)  // ❌ PSI access on EDT
    val containingClass = method.containingClass                            // ❌ PSI access on EDT
    return "${containingClass.name}::${method.name}()"
}

override fun getCustomContainerName(): String? {
    val file = element.containingFile  // ❌ PSI access on EDT
    return file?.virtualFile?.parent?.name
}

override fun getCustomIcon(): Icon {
    val path = element.containingFile?.virtualFile?.path  // ❌ PSI access on EDT
    ...
}
```

These methods are called when IntelliJ renders the Related Symbol popup, which happens on the EDT for UI responsiveness.

**Key Insight**: Data must be extracted from PSI elements when `createGotoRelatedItem()` is called (in a safe threading context), not when the popup renders the item (on EDT).

### Solution

Pre-compute all necessary data from PSI elements **before** creating the `GotoRelatedItem` object, then use the pre-computed values in the overridden methods.

**Safe Pattern:**

```kotlin
private fun createGotoRelatedItem(element: PsiElement): GotoRelatedItem {
    // ✅ Pre-compute all data here (safe threading context)
    val customName = computeCustomName(element)
    val containerName = computeContainerName(element)
    val iconPath = element.containingFile?.virtualFile?.path

    return object : GotoRelatedItem(element, "Controllers") {
        override fun getCustomName(): String = customName  // ✅ No PSI access
        override fun getCustomContainerName(): String? = containerName  // ✅ No PSI access
        override fun getCustomIcon(): Icon = computeIcon(iconPath)  // ✅ No PSI access
    }
}

private fun computeCustomName(element: PsiElement): String {
    // PSI access happens here, in a safe context
    val method = PsiTreeUtil.getParentOfType(element, Method::class.java)
    ...
}
```

### Implementation

**1. ViewToControllerGotoRelatedProvider.kt**

Refactored `createGotoRelatedItem()` to pre-compute data:

```kotlin
private fun createGotoRelatedItem(element: PsiElement): GotoRelatedItem {
    // Pre-compute all data from PSI elements to avoid EDT access violations
    val customName = computeCustomName(element)
    val containerName = computeContainerName(element)
    val iconPath = element.containingFile?.virtualFile?.path

    return object : GotoRelatedItem(element, "Controllers") {
        override fun getCustomName(): String = customName
        override fun getCustomContainerName(): String? = containerName
        override fun getCustomIcon(): Icon = /* uses iconPath only */
    }
}

private fun computeCustomName(element: PsiElement): String {
    val method = PsiTreeUtil.getParentOfType(element, Method::class.java)
    if (method != null) {
        val containingClass = method.containingClass
        return if (containingClass != null) {
            "${containingClass.name}::${method.name}()"
        } else {
            "${method.name}()"
        }
    }
    val file = element.containingFile
    return file?.name ?: element.text.take(50)
}

private fun computeContainerName(element: PsiElement): String? {
    val file = element.containingFile
    return file?.virtualFile?.parent?.name
}
```

**2. ElementToUsagesGotoRelatedProvider.kt**

Applied the same pattern:

```kotlin
private fun createGotoRelatedItem(element: PsiElement, settings: Settings): GotoRelatedItem {
    // Pre-compute all data from PSI elements to avoid EDT access violations
    val containingFile = element.containingFile
    val group = if (containingFile != null && isElementFile(containingFile, settings)) {
        "Elements"
    } else {
        "Views"
    }

    val customName = computeCustomName(element)
    val containerName = computeContainerName(element)
    val iconPath = element.containingFile?.virtualFile?.path

    return object : GotoRelatedItem(element, group) {
        override fun getCustomName(): String = customName
        override fun getCustomContainerName(): String? = containerName
        override fun getCustomIcon(): Icon = /* uses iconPath only */
    }
}

private fun computeCustomName(element: PsiElement): String {
    val file = element.containingFile
    return file?.name ?: element.text.take(50)
}

private fun computeContainerName(element: PsiElement): String? {
    val file = element.containingFile
    val vFile = file?.virtualFile ?: return null
    val parent = vFile.parent ?: return null
    val grandParent = parent.parent

    return if (grandParent != null && (parent.name == "element" || parent.name == "Element" || parent.name == "Elements")) {
        parent.name
    } else {
        parent.name
    }
}
```

### Testing Results

All tests passed successfully after the threading fix:
- **15 ControllerLineMarkerTest tests** - All PASSED (no regressions)
- **7 ElementToUsagesGotoRelatedTest tests** - All PASSED
- **5 ViewToControllerGotoRelatedTest tests** - All PASSED

**Total: 27 tests PASSED** ✓

### Lessons Learned

1. **Threading Model Compliance**: IntelliJ's strict threading model requires PSI access only on background threads. EDT is reserved for UI rendering operations.

2. **Pre-computation Strategy**: When creating UI items (like `GotoRelatedItem`), extract all necessary data from PSI elements during construction, not during rendering.

3. **Separation of Concerns**:
   - **Data extraction** (PSI access) → During item creation (BGT safe)
   - **Data presentation** (UI rendering) → During popup display (EDT only)

4. **Helper Methods**: Extracting computation logic into separate methods (`computeCustomName()`, `computeContainerName()`) improves code clarity and makes the threading separation explicit.

5. **VirtualFile is Safe**: While PSI elements require BGT access, `VirtualFile` objects and their properties (like `path`) can be safely accessed from pre-computed values on EDT.

### Files Modified

1. **ViewToControllerGotoRelatedProvider.kt**
   - Refactored `createGotoRelatedItem()` to pre-compute all PSI data
   - Added `computeCustomName()` and `computeContainerName()` helper methods
   - Overridden methods now use only pre-computed values

2. **ElementToUsagesGotoRelatedProvider.kt**
   - Refactored `createGotoRelatedItem()` to pre-compute all PSI data
   - Added `computeCustomName()` and `computeContainerName()` helper methods
   - Overridden methods now use only pre-computed values

### Verification

The threading fix was verified by:
1. Compilation success
2. All 27 automated tests passing
3. Manual testing would confirm no EDT violations during popup usage

### Threading Architecture Summary

**Safe Pattern for GotoRelatedItem**:

```
getItems() called
    ↓
Iterate over PSI elements (in BGT-safe context)
    ↓
For each element:
    Extract all needed data (PSI access happens HERE)
        - customName
        - containerName
        - iconPath
    ↓
    Create GotoRelatedItem with pre-computed data
    ↓
    Return list

Later... User opens popup (on EDT)
    ↓
Popup renders items (EDT context)
    ↓
Calls getCustomName() → Returns pre-computed string ✅
Calls getCustomContainerName() → Returns pre-computed string ✅
Calls getCustomIcon() → Uses pre-computed path ✅
```

This pattern ensures PSI access only occurs in safe threading contexts while maintaining smooth UI rendering.

---

## Part 3: Element → Views/Elements Navigation

### Overview

Extended the "Go to Related Item" feature to support navigation from element files to:
- **Views** that use the element via `$this->element()` calls
- **Other elements** that nest the element

### Implementation: ElementToUsagesGotoRelatedProvider

**File**: `src/main/kotlin/com/daveme/chocolateCakePHP/view/ElementToUsagesGotoRelatedProvider.kt`

**Key Decisions**:
- Use `GotoRelatedProvider` (NO gutter icons, consistent with view files)
- Leverage existing `ViewFileIndex` which already tracks `element()` calls
- Dual grouping: "Views" for regular views, "Elements" for nested elements

**Detection Logic**:
```kotlin
private fun isElementFile(file: PsiFile, settings: Settings): Boolean {
    val templatesDir = templatesDirectoryOfViewFile(project, settings, file)
        ?: return false
    val relativePath = VfsUtil.getRelativePath(virtualFile, templatesDir.directory)
        ?: return false

    // Check if the file is in the element directory
    return relativePath.startsWith("${templatesDir.elementDirName}/")
}
```

**Grouping Logic**:
```kotlin
private fun createGotoRelatedItem(element: PsiElement, settings: Settings): GotoRelatedItem {
    val containingFile = element.containingFile

    // Determine if the referencing file is itself an element
    val group = if (containingFile != null && isElementFile(containingFile, settings)) {
        "Elements"  // Nested element
    } else {
        "Views"     // Regular view
    }

    return object : GotoRelatedItem(element, group) { ... }
}
```

### Infrastructure Reuse

The implementation reuses existing infrastructure without any modifications:
- **ViewFileIndex**: Already indexes `element()` calls in views and elements
- **ViewFileIndexService**: Already provides `referencingElementsInSmartReadAction()`
- **canonicalizeFilenameToKey()**: Already handles element file paths correctly

### Test Coverage

**File**: `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake5/ElementToUsagesGotoRelatedTest.kt`

**7 comprehensive tests** - All passing ✓:
1. `test element file navigates to view that uses it`
2. `test element file navigates to another element that uses it`
3. `test element used by both views and elements shows both`
4. `test related items are grouped correctly`
5. `test nested element navigation`
6. `test non-element file returns empty`
7. `test non-element view file returns empty`

**Test Fixtures Created**:
- `cake5/templates/element/breadcrumb.php` - Simple element
- `cake5/templates/element/layout_header.php` - Element that nests breadcrumb
- `cake5/templates/Movie/index_with_elements.php` - View using both elements
- Reused: `cake5/templates/element/Director/filmography.php` (nested element)
- Reused: `cake5/templates/Movie/element_with_params.php` (view using nested element)

### Element Directory Naming

The implementation correctly handles all CakePHP version-specific element directory names:
- CakePHP 2: `Elements/`
- CakePHP 3+: `element/` or `Element/`

This is handled automatically by `templatesDir.elementDirName`.

### User Experience

**From Element Files**:
- ✓ NO gutter icons (clean, consistent with views!)
- ✓ Ctrl+Alt+Home shows Related Symbol popup
- ✓ Results grouped into "Views" and "Elements" categories
- ✓ Works for both simple elements and nested elements

**Example Navigation**:
```
breadcrumb.php (element)
    → Ctrl+Alt+Home
    → Related Symbol popup shows:
        Views:
            - index_with_elements.php
        Elements:
            - layout_header.php
```

### Complete Navigation Matrix

| From File Type | To File Type | Mechanism | Gutter Icon | Shortcut |
|----------------|--------------|-----------|-------------|----------|
| Controller | View | RelatedItemLineMarkerProvider | YES | Ctrl+Alt+Home |
| View | Controller | GotoRelatedProvider | NO | Ctrl+Alt+Home |
| Element | View | GotoRelatedProvider | NO | Ctrl+Alt+Home |
| Element | Element | GotoRelatedProvider | NO | Ctrl+Alt+Home |

### Benefits

1. **Consistent UX**: Same keyboard shortcut works across all file types
2. **Clean UI**: No gutter icons in view or element files
3. **Smart Grouping**: Views and elements shown in separate categories
4. **Zero Infrastructure Changes**: Reuses existing ViewFileIndex
5. **Comprehensive Testing**: 7 tests covering all navigation scenarios
6. **Bidirectional**: Works for both simple and nested elements

## Final Implementation Status

✅ **Controller → View** - Complete (RelatedItemLineMarkerProvider with gutter icons)
✅ **View → Controller** - Complete (GotoRelatedProvider without gutter icons)
✅ **Element → Views/Elements** - Complete (GotoRelatedProvider without gutter icons)

### All Files Modified/Created

1. **Controller → View** (Part 1):
   - Modified: `ControllerMethodLineMarker.kt` - Changed to RelatedItemLineMarkerProvider
   - Tests: Existing 15 tests all pass

2. **View → Controller** (Part 2):
   - Created: `ViewToControllerGotoRelatedProvider.kt`
   - Modified: `plugin.xml` (added gotoRelatedProvider)
   - Tests: Created `ViewToControllerGotoRelatedTest.kt` with 5 tests

3. **Element → Views/Elements** (Part 3):
   - Created: `ElementToUsagesGotoRelatedProvider.kt`
   - Modified: `plugin.xml` (added second gotoRelatedProvider)
   - Tests: Created `ElementToUsagesGotoRelatedTest.kt` with 7 tests
   - Fixtures: Created breadcrumb.php, layout_header.php, index_with_elements.php

### How to Use - Complete Guide

**From Controller**:
- Open any controller file
- Position cursor on a public method (action)
- Press **Ctrl+Alt+Home** (or **⌃⌘↑** on Mac)
- See Related Symbol popup grouped under "Views"
- Alternatively: Click gutter icon, or use Ctrl+Alt+Shift+HOME toggle

**From View**:
- Open any view file (template)
- Press **Ctrl+Alt+Home** (or **⌃⌘↑** on Mac)
- See Related Symbol popup grouped under "Controllers"
- Alternatively: Use Ctrl+Alt+Shift+HOME toggle or toolbar icon

**From Element**:
- Open any element file (template/element/)
- Press **Ctrl+Alt+Home** (or **⌃⌘↑** on Mac)
- See Related Symbol popup with two groups:
  - "Views" - Regular views using this element
  - "Elements" - Other elements nesting this element
- Alternatively: Use Ctrl+Alt+Shift+HOME toggle or toolbar icon

All navigation uses the same standard IntelliJ keyboard shortcut for a consistent, intuitive user experience across the entire plugin.
