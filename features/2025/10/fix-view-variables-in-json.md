# Fix: View Variable Completion for Data View Files (JSON/XML)

## Problem Statement

View variable completion works correctly for standard view files like:
- `templates/ControllerName/action_name.php`

But **does NOT work** for data view files in subdirectories like:
- `templates/ControllerName/json/action_name.php`
- `templates/ControllerName/xml/action_name.php`

When a controller does `$this->set(compact('myVar'))`, the variable `$myVar` should be completed in the corresponding view file, but this fails for data view files.

## Root Cause Analysis

The issue lies in how view file paths are canonicalized to keys for indexing. The flow is:

1. **Indexing Phase** (`ViewFileDataIndexer`):
   - For implicit render, creates index key like: `Movie/film_director`
   - This key is stored in the ViewFile index

2. **Lookup Phase** (`ViewVariableCompletionContributor` and `ViewVariableTypeProvider`):
   - For a file at `templates/Movie/json/film_director.php`
   - `ViewFileIndexService.canonicalizeFilenameToKey()` is called (line 59-63 in ViewVariableCompletionContributor)
   - This function:
     a. Removes the templates directory prefix
     b. Removes the `.php` extension
     c. **Calls `removeImmediateParentDir()` for each data view extension** (lines 117-123 in ViewFileIndexService)

3. **The Problem**:
   - For `Movie/json/film_director`, `removeImmediateParentDir("json")` converts it to `Movie/film_director`
   - This SHOULD match the index key `Movie/film_director` from the controller
   - BUT there's a discrepancy somewhere...

## Key Code Locations

### Path Canonicalization
- **File**: `ViewFileIndexService.kt`
- **Function**: `canonicalizeFilenameToKey()` (lines 103-125)
- **Logic**: Removes templates dir, extension, then removes immediate parent dir for each data view extension

### Data View Extensions
- **File**: `Settings.kt`
- **Property**: `dataViewExtensions` (lines 76, 231-234)
- **Default**: `["json", "xml"]`

### String Helper
- **File**: `Strings.kt`
- **Function**: `removeImmediateParentDir()` (lines 131-141)
- **Logic**: Removes only the immediate parent directory if it matches the given name
- **Test**: StringsTest.kt line 62-63 shows `"foo/json/bar/json/foo.php"` → `"foo/json/bar/foo.php"`

### Type Provider Path
- **File**: `ViewVariableTypeProvider.kt`
- **Issue**: Line 50 creates relative path with `substringBeforeLast(".")` which **doesn't use canonicalizeFilenameToKey**
  - This might be creating a different key format!

## Investigation Needed

1. **Compare the two key generation paths**:
   - Path A: `ViewVariableCompletionContributor` line 59-63 uses `canonicalizeFilenameToKey()`
   - Path B: `ViewVariableTypeProvider` line 38-51 uses `VfsUtil.getRelativePath()` + `substringBeforeLast(".")`
   - These might produce different keys for data view files!

2. **Check ViewFileDataIndexer**:
   - Does implicit render indexing create the right keys for data view files?
   - Line 296-300: `fullImplicitViewPath()` - does this account for data views?

3. **Verify the lookup logic**:
   - `ViewVariableIndexService.lookupVariablesFromViewPathInSmartReadAction()` (line 150-197)
   - Does it properly canonicalize the filename before lookup?

## Hypothesis

The **ViewVariableTypeProvider is using a different path canonicalization strategy** than ViewVariableCompletionContributor:

- **Completion**: Uses `canonicalizeFilenameToKey()` which strips data view dirs
- **Type Provider**: Uses `substringBeforeLast(".")` which does NOT strip data view dirs

So for `templates/Movie/json/film_director.php`:
- Completion key: `Movie/film_director` ✓
- Type provider key: `Movie/json/film_director` ✗

This mismatch means type resolution fails even if completion finds the variable.

## Solution Plan

### Fix 1: Update ViewVariableTypeProvider (Primary Fix)

**File**: `ViewVariableTypeProvider.kt` lines 30-53

Replace the manual path construction:
```kotlin
val relativePath = VfsUtil.getRelativePath(
    psiFile.originalFile.virtualFile,
    templateDir.directory
) ?: return null

val incompleteType = "#${getKey()}v$cakeVersion" + SEPARATOR +
    relativePath.substringBeforeLast(".") + SEPARATOR +
    name
```

With canonicalized path:
```kotlin
val path = psiFile.originalFile.virtualFile.path
val settings = Settings.getInstance(project)
val filenameKey = ViewFileIndexService.canonicalizeFilenameToKey(
    templateDir,
    settings,
    path
)

val incompleteType = "#${getKey()}v$cakeVersion" + SEPARATOR +
    filenameKey + SEPARATOR +
    name
```

### Fix 2: Verify ViewVariableDataIndexer (Secondary)

Check if implicit render keys need any adjustment to handle data view subdirectories properly. The indexer might need to be aware that:
- `MovieController::film_director()` should map to BOTH:
  - `Movie/film_director`
  - `Movie/json/film_director` (if that file exists)
  - `Movie/xml/film_director` (if that file exists)

**BUT** the current design uses path normalization, so the indexer should create ONE canonical key `Movie/film_director` and the view files should all canonicalize to the same key.

## Test Plan

1. Create a test case in `ViewVariableTest.kt`:
   ```kotlin
   fun `test variable completion in json view`() {
       myFixture.configureByFilePathAndText("cake3/templates/Movie/json/film_director.php", """
       <?php
       echo $<caret>
       """.trimIndent())
       myFixture.completeBasic()

       val result = myFixture.lookupElementStrings
       assertTrue(result!!.contains("$moviesTable"))
   }
   ```

2. Verify fix works for both JSON and XML views
3. Ensure existing tests still pass

## Files to Modify

1. **ViewVariableTypeProvider.kt** - Update path canonicalization (Primary) ✅
2. **ViewVariableTest.kt** - Add test case for data views ✅
3. Possibly **ViewFileDataIndexer.kt** - If indexer needs updates ❌ (Not needed)

## Implementation Summary

### ✅ FIXED - Changes Made:

1. **ViewVariableTypeProvider.kt** (lines 3-55):
   - Added import: `import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService`
   - Removed import: `import com.intellij.openapi.vfs.VfsUtil` (no longer needed)
   - Replaced manual path construction with `ViewFileIndexService.canonicalizeFilenameToKey()`
   - This ensures consistent path normalization with the completion contributor

2. **ViewVariableTest.kt** (Cake3):
   - Added test: `test variable list is communicated from controller to json view` ✅
   - Added test: `test variable type is communicated from controller to json view` ✅
   - Added test: `test variable list is communicated from controller to xml view` ✅

3. **Test Fixtures**:
   - Created: `src/test/fixtures/cake3/src/Template/Movie/json/film_director.ctp`
   - Created: `src/test/fixtures/cake3/src/Template/Movie/xml/film_director.ctp`

### Test Results:

- ✅ All 13 Cake3 ViewVariable tests passing
- ✅ All 39 ViewVariable tests passing across all CakePHP versions (2, 3, 4, 5)
- ✅ No regressions detected

### Root Cause Confirmed:

The issue was exactly as hypothesized - `ViewVariableTypeProvider` was using `VfsUtil.getRelativePath()` + `substringBeforeLast(".")` which did NOT strip data view subdirectories, while `ViewVariableCompletionContributor` used `canonicalizeFilenameToKey()` which properly strips them. This mismatch caused type resolution to fail for JSON/XML views.

## Notes

- The `removeImmediateParentDir()` function is already working correctly (see StringsTest.kt)
- Settings already has `dataViewExtensions = ["json", "xml"]` configured
- The canonicalization logic in ViewFileIndexService looks correct
- The main issue was inconsistent path normalization between Completion and TypeProvider
- **Fix confirmed working** - view variables now complete and have correct types in JSON/XML data views
