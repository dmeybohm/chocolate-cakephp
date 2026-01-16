# Fix CakePHP 2 and 3+ Nested App Directory Path Resolution

## Problem Summary

When configuring app directories as nested paths:
- **CakePHP 2**: `cake2AppDirectory` as `src/app` instead of just `app`
- **CakePHP 3+**: `appDirectory` as `foo/src` instead of just `src`

Most functionality breaks:
- Line markers don't show up
- Toolbar icons don't appear
- View/controller navigation fails

## Root Cause

### CakePHP 2
The path resolution code compared directory **names** against the configured `cake2AppDirectory` value:

```kotlin
if (child.name == settings.cake2AppDirectory)  // Fails when cake2AppDirectory is "src/app"
```

Directory names never contain slashes, so `"app" != "src/app"` always fails.

### CakePHP 3+
The path resolution code used `findChild()` which only looks for immediate children:

```kotlin
val appDirChild = child.findChild(settings.appDirectory)  // Fails when appDirectory is "foo/src"
```

The `findChild()` method doesn't support nested paths.

## Solution

Added helper functions in `CakePaths.kt` to properly handle nested directory paths:

1. **`matchesNestedAppDirectory()`**: Checks if a directory matches a potentially nested path by walking up the directory hierarchy and verifying each path component from bottom to top.

2. **`findNestedChild()`**: Finds a child directory by path, supporting nested paths by using the existing `findRelativeFile()` function.

Also added a duplicate `matchesCake2AppDirectory()` helper to `ViewFileIndexService.kt` since the helper in `CakePaths.kt` is private.

## Files Modified

1. `src/main/kotlin/com/daveme/chocolateCakePHP/cake/CakePaths.kt`
   - Added `matchesCake2AppDirectory()`, `matchesAppDirectory()`, `matchesNestedAppDirectory()`, and `findNestedChild()` helper functions
   - Updated `appOrSrcDirectoryFromSourceFile()` for both CakePHP 2 and 3+
   - Updated `templatesDirectoryOfViewFile()` for both CakePHP 2 and 3+

2. `src/main/kotlin/com/daveme/chocolateCakePHP/view/viewfileindex/ViewFileIndexService.kt`
   - Added `matchesCake2AppDirectory()` helper function
   - Updated `isCakeTwoController()` to use the helper

## New Test Files

1. `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake2/NestedAppDirectoryTest.kt`
   - Tests model completion with nested app directory
   - Tests view variable completion with nested app directory

2. `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake3/NestedAppDirectoryTest.kt`
   - Tests table completion with nested app directory
   - Tests view variable completion with nested app directory

## New Test Fixtures

### CakePHP 2 Nested Fixtures (`src/test/fixtures/cake2_nested/`)
- `src/app/Controller/AppController.php`
- `src/app/Controller/MovieController.php`
- `src/app/Model/Movie.php`
- `src/app/View/Movie/index.ctp`
- `vendor/cakephp.php`

### CakePHP 3 Nested Fixtures (`src/test/fixtures/cake3_nested/`)
- `app/src/Controller/AppController.php`
- `app/src/Controller/MovieController.php`
- `app/src/Model/Table/MoviesTable.php`
- `app/src/Template/Movie/index.ctp`
- `vendor/cakephp.php`

## Implementation Progress

### Session #1 (2026-01-16)

Implementation completed successfully:

1. Added helper functions for nested directory matching
2. Updated `appOrSrcDirectoryFromSourceFile()` for CakePHP 2 and 3+
3. Updated `templatesDirectoryOfViewFile()` for CakePHP 2 and 3+
4. Updated `ViewFileIndexService.kt` for CakePHP 2
5. Created test fixtures for both CakePHP 2 and 3 nested configurations
6. Created regression tests for both CakePHP versions

All tests pass:
- New nested app directory tests: 4 tests (2 CakePHP 2, 2 CakePHP 3)
- Full test suite: All existing tests continue to pass (no regressions)
