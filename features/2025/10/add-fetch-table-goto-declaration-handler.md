# Implementation Plan: Add fetchTable() GotoDeclarationHandler

## Overview
Add a new GotoDeclarationHandler that allows navigating to the Table class when clicking on the string argument in a `fetchTable()` call.

## Current State Analysis

### Existing Components
1. **TableLocatorMethodPattern**: Already recognizes `fetchTable()` calls (line 17 in TableLocatorMethodPattern.kt)
2. **TableLocatorTypeProvider**: Provides type resolution for `fetchTable()` calls (lines 38-51 in TableLocatorTypeProvider.kt)
3. **TableLocatorCompletionContributor**: Provides code completion for table names
4. **CustomFinderGotoDeclarationHandler**: Example handler for the `find()` method

### Key Patterns Identified
- GotoDeclarationHandlers extend `GotoDeclarationHandler` interface
- They check for StringLiteralExpression in the correct context
- They use PhpIndex to look up the target classes
- They return an array of PsiElements (the target files/classes)

## Implementation Details

### 1. New Class: TableLocatorGotoDeclarationHandler
**Location**: `src/main/kotlin/com/daveme/chocolateCakePHP/model/TableLocatorGotoDeclarationHandler.kt`

**Responsibilities**:
- Handle goto declaration for `fetchTable("TableName")` calls
- Handle goto declaration for `$this->getTableLocator()->get("TableName")` calls
- Navigate to the corresponding Table class (e.g., "Movies" → MoviesTable.php)

**Key Logic**:
1. Check if the element is a StringLiteralExpression
2. Verify it's the first parameter of a `fetchTable()` or `get()` method call
3. Check the calling context (Controller, TableLocator, or TableRegistry)
4. Resolve the table name to the full class name (e.g., "Movies" → "App\Model\Table\MoviesTable")
5. Use PhpIndex to find the class and return it

### 2. Update plugin.xml
Add the following line in the extensions section:
```xml
<gotoDeclarationHandler implementation="com.daveme.chocolateCakePHP.model.TableLocatorGotoDeclarationHandler" />
```

### 3. Test Class: TableLocatorGotoDeclarationTest
**Location**: `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake5/TableLocatorGotoDeclarationTest.kt`

**Test Cases**:
1. `test goto declaration from fetchTable in controller` - Navigate from `$this->fetchTable("Movies")` to MoviesTable
2. `test goto declaration from getTableLocator get method` - Navigate from `$this->getTableLocator()->get("Articles")` to ArticlesTable
3. `test goto declaration does not work on other methods` - Ensure it doesn't trigger on other method calls
4. `test goto declaration handles plugin notation` - Handle plugin-prefixed tables if needed

## Implementation Steps

1. Create the TableLocatorGotoDeclarationHandler class
   - Implement getGotoDeclarationTargets() method
   - Add logic to identify fetchTable() and get() calls
   - Use Settings to get the app namespace
   - Use PhpIndex to locate the Table class

2. Register the handler in plugin.xml

3. Create test fixtures if needed
   - Ensure MoviesTable.php and ArticlesTable.php exist in test fixtures
   - Add controller with fetchTable calls if not already present

4. Write comprehensive tests
   - Test navigation from controllers
   - Test different table name formats
   - Test edge cases (non-existent tables, etc.)

## Notes
- Focus on Cake 5 implementation only as requested
- Reuse existing utility methods from the codebase where possible
- Follow the existing code style and patterns

## Implementation Complete

✅ Created `TableLocatorGotoDeclarationHandler` class that handles:
- `$this->fetchTable("TableName")` calls in controllers
- `$this->getTableLocator()->get("TableName")` calls
- `TableRegistry::get("TableName")` calls
- `TableRegistry::getTableLocator()->get("TableName")` calls

✅ Registered the handler in `plugin.xml`

✅ Created comprehensive test suite for **Cake 3, 4, and 5**:

**Cake 5 Tests** (7 test cases):
- Navigation from fetchTable in controller
- Navigation from getTableLocator()->get() method
- Navigation from static TableRegistry::get() method
- Navigation from TableRegistry::getTableLocator()->get() method
- Validation that handler doesn't trigger on non-Controller objects
- Validation that handler doesn't trigger on non-get methods on TableLocator
- Graceful handling of non-existent tables

**Cake 4 Tests** (7 test cases):
- Same test coverage as Cake 5 but using Cake 4 specific fixtures and paths

**Cake 3 Tests** (7 test cases):
- Navigation from getTableLocator()->get() method (fetchTable() not available in Cake 3)
- Navigation from static TableRegistry::get() method
- Navigation from TableRegistry::getTableLocator()->get() method
- Validation that handler doesn't trigger on non-Table objects
- Validation that handler doesn't trigger on non-get methods on TableLocator
- Graceful handling of non-existent tables
- **Plugin table navigation with "TestPlugin.Directors" notation** ✅

## Enhanced Plugin Support

✅ **Added plugin table navigation support:**
- Created `TestPlugin\Model\Table\DirectorsTable.php` fixture
- Enhanced handler to parse plugin notation like `"TestPlugin.Directors"`
- Handler correctly resolves plugin tables by filtering namespace matches
- Works with all CakePHP versions (3, 4, 5)

All tests passing successfully across all CakePHP versions!