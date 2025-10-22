# Plan: Add `contain()` Completion and Goto Declaration Support

## ✅ Implementation Complete

Implementation complete for CakePHP 3, 4, and 5 with 34 tests created.

**Test Results**: 34/34 tests passing (100%) ✅
- CakePHP 5: 12/12 tests passing ✅
- CakePHP 4: 12/12 tests passing ✅
- CakePHP 3: 10/10 tests passing ✅

## Overview
Implement completion and goto declaration for the CakePHP `contain()` method, which is used to eager-load associations when querying Table classes. This feature will provide autocomplete for all available Table class names and allow navigation to those Table classes.

## Key Concepts

### How `contain()` Works
- Used on Query objects to eager-load associations: `$query->contain(['Authors', 'Comments'])`
- Can be passed to `find()`: `$articles->find('all', contain: ['Authors'])`
- Supports nested associations: `$query->contain(['Authors.Addresses', 'Comments.Authors'])`
- First parameter can be string or array of strings

### Association Names
- Association names typically match Table class names without the "Table" suffix
- Example: `AuthorsTable` → association name is `'Authors'`
- Can reference tables from plugins: `'PluginName.Authors'`

## Implementation Plan (Simplified Approach)

### Phase 1: Create ContainMethodPatterns.kt
Similar to `AssetMethodPatterns.kt`, create pattern definitions:

**File**: `src/main/kotlin/com/daveme/chocolateCakePHP/model/ContainMethodPatterns.kt`

**Patterns needed**:
- `stringForCompletion`: Matches `$query->contain('<caret>Authors')`
- `stringForGotoDeclaration`: Matches `$query->contain('Authors')`
- `arrayForCompletion`: Matches `$query->contain(['<caret>Authors', 'Comments'])`
- `arrayForGotoDeclaration`: Matches `$query->contain(['Authors', 'Comments'])`

**Pattern condition**:
- Private `ContainMethodCondition` that validates:
  - Method name is "contain" (case insensitive)
  - Class reference type is Table or Query class
  - Only CakePHP 3+ enabled (check settings.cake3Enabled)

### Phase 2: Create ContainCompletionContributor.kt

**File**: `src/main/kotlin/com/daveme/chocolateCakePHP/model/ContainCompletionContributor.kt`

**Simplified Functionality**:
1. Extend both string and array patterns from `ContainMethodPatterns`
2. Get all Table classes from app namespace: `${settings.appNamespace}\Model\Table\*Table`
3. For each Table class:
   - Extract the table name (remove "Table" suffix)
   - Create `LookupElementBuilder` with the table name
4. Add all completions to result set

**No complex parsing needed** - just enumerate all available Table classes!

**Note**: Plugin table support deferred - would need plugin name configuration added to Settings

### Phase 3: Create ContainGotoDeclarationHandler.kt

**File**: `src/main/kotlin/com/daveme/chocolateCakePHP/model/ContainGotoDeclarationHandler.kt`

**Simplified Functionality**:
1. Accept both string and array patterns from `ContainMethodPatterns`
2. Extract association name from string literal (e.g., `'Authors'`)
3. Handle nested associations by taking first part:
   - Split on dot: `'Authors.Addresses'` → take "Authors"
4. Use `PhpIndex.getPossibleTableClasses(settings, tableName)` to find Table class
5. Return array of PsiElement pointing to Table class
6. Add empty string check (don't navigate on empty strings)

**Edge cases**:
- Empty string: Return empty array (nothing to navigate to)
- Nested associations: Navigate to first part only (e.g., `'Authors.Addresses'` → navigate to `AuthorsTable`)
- Unknown association: Return empty array
- Plugin tables: Not supported yet (would need plugin name configuration)

### Phase 4: Create Test Files for CakePHP 5

**File**: `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake5/ContainCompletionTest.kt`

**Test scenarios**:
1. ✅ Complete table names from simple `contain()` call
2. ✅ Complete from array parameter
3. ✅ Complete second element in array
4. ✅ Don't complete when disabled
5. ✅ Verify completions include all tables from app namespace

**File**: `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake5/ContainGotoDeclarationTest.kt`

**Test scenarios**:
1. ✅ Navigate to Table class from string parameter
2. ✅ Navigate from array parameter
3. ✅ Navigate from second element in array
4. ✅ Don't navigate on empty string
5. ✅ Navigate from nested association (first part only)

### Phase 5: Create Test Fixtures

**Files needed**:
- Update `src/test/fixtures/cake5/src5/Model/Table/ArticlesTable.php`
- Use existing `AuthorsTable.php` if available, or create simple one
- Use existing `CommentsTable.php` if available, or create simple one

**Note**: We don't need actual association definitions in `initialize()` since we're just enumerating all available Table classes!

### Phase 6: Register in plugin.xml

**File**: `src/main/resources/META-INF/plugin.xml`

Add registrations:
```xml
<completion.contributor language="PHP"
    implementationClass="com.daveme.chocolateCakePHP.model.ContainCompletionContributor"/>
<gotoDeclarationHandler
    implementation="com.daveme.chocolateCakePHP.model.ContainGotoDeclarationHandler"/>
```

## Technical Implementation Details

### Getting All Table Classes

Use PhpIndex to enumerate all classes matching the Table pattern:

```kotlin
// Get all tables from app namespace
val appTableFqnPattern = "${settings.appNamespace}\\Model\\Table\\*Table"
val appTables = phpIndex.getClassesByFQN(appTableFqnPattern)
```

### Extracting Table Name from Class Name

```kotlin
// "ArticlesTable" → "Articles"
val tableName = phpClass.name.removeSuffix("Table")
```

### Handling Nested Associations

For navigation, extract the first part before any dot:
```kotlin
val tableName = stringLiteral.contents.split(".", limit = 2)[0]
// "Authors.Addresses" → "Authors"
// "Authors" → "Authors"
```

## Advantages of Simplified Approach

1. ✅ **Much simpler to implement** - no PHP AST parsing needed
2. ✅ **More robust** - doesn't depend on parsing complex PHP syntax
3. ✅ **Comprehensive** - shows ALL available tables, even if not formally associated
4. ✅ **Follows existing patterns** - similar to how CustomFinderCompletionContributor works
5. ✅ **Still very useful** - developers can see all tables and autocomplete will help them
6. ✅ **Easy to test** - just check that known tables appear in completions

## Success Criteria

- ✅ Completion shows all available Table names when typing in `contain()` parameter
- ✅ Completion works for both string and array syntax
- ✅ Goto declaration navigates to Table class
- ✅ All tests pass for all CakePHP versions (34/34 tests passing)
- ✅ Empty strings don't navigate (but do show completions)
- ✅ Unknown table names handled gracefully
- ✅ Nested associations supported (navigate to first part)

## Implementation Summary

### Files Created

1. **`src/main/kotlin/com/daveme/chocolateCakePHP/model/ContainMethodPatterns.kt`**
   - Pattern definitions for `contain()` method
   - Supports both string and array parameters
   - Validates method name and CakePHP version

2. **`src/main/kotlin/com/daveme/chocolateCakePHP/model/ContainCompletionContributor.kt`**
   - Enumerates all Table classes from app namespace
   - Provides completions for both string and array syntax
   - Strips "Table" suffix from class names

3. **`src/main/kotlin/com/daveme/chocolateCakePHP/model/ContainGotoDeclarationHandler.kt`**
   - Navigates to Table class from association name
   - Handles nested associations (takes first part)
   - Validates parameter position and empty strings

4. **Test Files**
   - `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake5/ContainCompletionTest.kt` (5 tests)
   - `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake5/ContainGotoDeclarationTest.kt` (7 tests)
   - `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake4/ContainCompletionTest.kt` (5 tests)
   - `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake4/ContainGotoDeclarationTest.kt` (7 tests)
   - `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake3/ContainCompletionTest.kt` (4 tests)
   - `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake3/ContainGotoDeclarationTest.kt` (6 tests)

5. **Test Fixtures**
   - CakePHP 5:
     - `src/test/fixtures/cake5/src5/Controller/ArticleController.php`
     - `src/test/fixtures/cake5/src5/Model/Table/AuthorsTable.php`
     - `src/test/fixtures/cake5/src5/Model/Table/CommentsTable.php`
   - CakePHP 4:
     - `src/test/fixtures/cake4/src4/Controller/ArticleController.php`
     - `src/test/fixtures/cake4/src4/Model/Table/AuthorsTable.php`
     - `src/test/fixtures/cake4/src4/Model/Table/CommentsTable.php`
   - CakePHP 3:
     - `src/test/fixtures/cake3/src/Controller/ArticleController.php`
     - `src/test/fixtures/cake3/src/Model/Table/AuthorsTable.php`
     - `src/test/fixtures/cake3/src/Model/Table/CommentsTable.php`

### Configuration Changes

Updated `src/main/resources/META-INF/plugin.xml`:
- Registered `ContainCompletionContributor`
- Registered `ContainGotoDeclarationHandler`

## Implementation Notes

### CakePHP 3 Type Resolution
The initial CakePHP 3 tests failed because they used `$this->loadModel()` which is not supported by the TableLocatorTypeProvider. The plugin only supports:
- CakePHP 5: `$this->fetchTable()`
- CakePHP 4: `$this->fetchTable()`
- CakePHP 3: `$this->getTableLocator()->get()`

The tests were updated to use `getTableLocator()->get()` instead of `loadModel()`, and all tests now pass.

## Future Enhancements (Out of Scope)

- Parse `initialize()` method to find actual association definitions
- Only show associated tables (not all tables)
- Validate that association exists
- Support for CakePHP 2
- Nested association completion (complete after dot with associations from related table)
- Support for association configuration arrays
- Line markers to navigate from association definition to Table class
- **Plugin table support** - requires adding plugin name to Settings configuration
- **Support for `loadModel()`** - requires extending TableLocatorTypeProvider
