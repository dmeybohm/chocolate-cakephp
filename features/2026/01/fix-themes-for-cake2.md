# CakePHP 2 Themes Support

This feature branch adds support for CakePHP 2 themes.

## Bug Fix: Themed View to Controller Navigation

### Problem

Navigation from themed views back to controllers was broken in CakePHP 2. The bug was in `templatesDirectoryOfViewFile()` in `CakePaths.kt`.

For a themed view like `app/View/Themed/MyTheme/Movie/index.ctp`:
- **Buggy behavior:** Returned `CakeTwoTemplatesDir(Themed)` - the `Themed` directory itself
- **Expected:** Should return `CakeTwoTemplatesDir(app/View/Themed/MyTheme)` - the specific theme directory

This caused the relative path calculation in `ViewToControllerGotoRelatedProvider` to compute the wrong path, breaking the index lookup.

### Solution

The fix detects when we're inside a theme directory (a child of `Themed`) and returns that theme directory as the templates root instead of the `Themed` directory itself.

The key change was:
- Before: Checked if `child.name == "Themed"` and returned `child`
- After: Check if `parent?.name == "Themed"` and return `child` (which is the theme directory like "MyTheme")

### Files Modified

1. `src/main/kotlin/com/daveme/chocolateCakePHP/cake/CakePaths.kt` - Fixed `templatesDirectoryOfViewFile()`
2. `src/test/fixtures/cake2/app/Controller/MovieController.php` - Added `themed_index()` method
3. `src/test/fixtures/cake2/app/View/Themed/MyTheme/Movie/themed_index.ctp` - New fixture
4. `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake2/ThemedViewToControllerTest.kt` - New test

## Implementation Progress

### Session #1

- Fixed the themed view to controller navigation bug
- Added test fixtures and test case
- All cake2 tests pass
- Full test suite passes
