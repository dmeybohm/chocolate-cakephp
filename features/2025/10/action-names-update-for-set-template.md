# Update actionNamesFromControllerMethod for ViewBuilder Support

## Overview
Update `actionNamesFromControllerMethod()` in `CakeController.kt` to handle ViewBuilder calls (`setTemplate()`/`setTemplatePath()`) in addition to the existing `render()` support. This will enable line markers and toggle-to-view functionality to work with ViewBuilder-based template assignments.

## Problem Statement

Currently, `actionNamesFromControllerMethod()` only handles:
- Default action name (method name)
- `$this->render('template')` calls

It needs to also handle:
- `$this->viewBuilder()->setTemplate('template')`
- `$this->viewBuilder()->setTemplatePath('path')` (which affects subsequent `setTemplate()` calls)

The key challenge is that `setTemplatePath()` establishes a path prefix that applies to all subsequent `setTemplate()` calls until another `setTemplatePath()` is encountered, similar to what we implemented in:
1. `TemplateGotoDeclarationHandler.getTemplatePathPreceeding()` - for goto-declaration
2. `ViewFileDataIndexer.indexViewBuilderCalls()` - for indexing

## Current Implementation

### CakeController.kt (lines 86-105)
```kotlin
fun actionNamesFromControllerMethod(method: Method): ActionNames {
    // Collect $this->render("some_file") calls:
    val renderCalls = PsiTreeUtil.findChildrenOfAnyType(method, false, MethodReference::class.java)
            as Collection<MethodReference>

    val defaultActionName = actionNameFromMethod(method)
    val otherActionNames: List<ActionName> = renderCalls.mapNotNull {
        if (it.name != "render") {
            return@mapNotNull null
        }
        val firstParameter = it.parameterList?.getParameter(0) as? StringLiteralExpression
            ?: return@mapNotNull null
        return@mapNotNull actionNameFromPath(firstParameter.contents)
    }

    return ActionNames(
        defaultActionName = defaultActionName,
        otherActionNames = otherActionNames
    )
}
```

### Usage Points
1. **ControllerMethodLineMarker.kt:43** - Generates line markers next to controller methods showing view files
2. **ToggleBetweenControllerAndViewAction.kt:93** - Enables Cmd/Ctrl+Shift+A toggle between controller and view

Both features currently don't work for ViewBuilder-based template assignments.

## Proposed Solution

### 1. Create Shared Data Structure

Create a new data class to represent ViewBuilder call information:

**Location**: `CakeController.kt` (after `ActionNames` definition)

```kotlin
/**
 * Represents a ViewBuilder method call (setTemplate or setTemplatePath).
 */
data class ViewBuilderCall(
    val methodName: String,        // "setTemplate" or "setTemplatePath"
    val parameterValue: String,     // The template name or path
    val offset: Int                 // Text offset for ordering
)
```

### 2. Create Shared ViewBuilder Call Extraction Function

Extract the common logic from `TemplateGotoDeclarationHandler.getTemplatePathPreceeding()` into a reusable function.

**Location**: `CakeController.kt` (new function)

```kotlin
/**
 * Find all ViewBuilder calls (setTemplate and setTemplatePath) in a PSI element.
 *
 * This function finds calls matching the pattern:
 *   $this->viewBuilder()->setTemplate('name')
 *   $this->viewBuilder()->setTemplatePath('path')
 *
 * @param element The PSI element to search (typically a Method)
 * @return List of ViewBuilderCall objects sorted by offset
 */
fun findViewBuilderCalls(element: PsiElement): List<ViewBuilderCall> {
    val methodRefs = PsiTreeUtil.findChildrenOfType(element, MethodReference::class.java)

    return methodRefs.mapNotNull { methodRef ->
        // Check if this is a setTemplate or setTemplatePath call
        val methodName = methodRef.name
        if (methodName != "setTemplate" && methodName != "setTemplatePath") {
            return@mapNotNull null
        }

        // Check if the receiver is $this->viewBuilder()
        val receiverMethodRef = methodRef.classReference as? MethodReference ?: return@mapNotNull null
        if (receiverMethodRef.name != "viewBuilder") {
            return@mapNotNull null
        }
        val receiverVariable = receiverMethodRef.classReference as? Variable ?: return@mapNotNull null
        if (receiverVariable.name != "this") {
            return@mapNotNull null
        }

        // Extract the string parameter
        val parameterList = methodRef.parameterList
        val firstParam = parameterList?.parameters?.getOrNull(0) as? StringLiteralExpression
            ?: return@mapNotNull null

        ViewBuilderCall(
            methodName = methodName,
            parameterValue = firstParam.contents,
            offset = methodRef.textRange.startOffset
        )
    }.sortedBy { it.offset }
}
```

### 3. Create ActionName Resolution Function

Process ViewBuilder calls to generate ActionName objects with proper path prefixes:

**Location**: `CakeController.kt` (new function)

```kotlin
/**
 * Convert a list of ViewBuilder calls into ActionName objects.
 *
 * This function processes setTemplate and setTemplatePath calls in order,
 * tracking the "current" template path and applying it to subsequent setTemplate calls.
 *
 * Algorithm:
 * - Iterate through calls in order (by offset)
 * - When we see setTemplatePath('path'), store 'path' as the current prefix
 * - When we see setTemplate('name'):
 *   - If we have a current prefix: create ActionName with prefix "/path/" + name
 *   - If no prefix: create ActionName with just name
 *
 * The "/" prefix makes the path absolute (see ActionName.isAbsolute).
 *
 * @param viewBuilderCalls List of ViewBuilder calls sorted by offset
 * @return List of ActionName objects
 */
fun actionNamesFromViewBuilderCalls(viewBuilderCalls: List<ViewBuilderCall>): List<ActionName> {
    val result = mutableListOf<ActionName>()
    var currentTemplatePath: String? = null

    for (call in viewBuilderCalls) {
        when (call.methodName) {
            "setTemplatePath" -> {
                // Update the current template path for subsequent setTemplate calls
                currentTemplatePath = call.parameterValue
            }
            "setTemplate" -> {
                // Build the final path combining setTemplatePath (if any) with setTemplate
                val viewName = if (currentTemplatePath != null) {
                    // Prepend "/" to make it absolute so the controller path is not prepended
                    // This matches the behavior in TemplateGotoDeclarationHandler and ViewFileDataIndexer
                    "/$currentTemplatePath/${call.parameterValue}"
                } else {
                    call.parameterValue
                }

                val actionName = actionNameFromPath(viewName)
                result.add(actionName)
            }
        }
    }

    return result
}
```

### 4. Update actionNamesFromControllerMethod

Extend the existing function to also collect ViewBuilder calls:

**Location**: `CakeController.kt:86-105`

```kotlin
/**
 * Get all the action names from a PHP method.
 *
 * Collects action names from:
 * - The method name itself (default action)
 * - $this->render('template') calls
 * - $this->viewBuilder()->setTemplate('template') calls (with setTemplatePath() support)
 * - $this->view = 'template' field assignments
 *
 * Only literal string parameters are included (dynamic values like $this->render($var) are filtered out).
 *
 * @param method The method element to search for action names.
 */
fun actionNamesFromControllerMethod(method: Method): ActionNames {
    // Collect $this->render("some_file") calls:
    val renderCalls = PsiTreeUtil.findChildrenOfAnyType(method, false, MethodReference::class.java)
            as Collection<MethodReference>

    val defaultActionName = actionNameFromMethod(method)

    // Collect render() action names
    val renderActionNames: List<ActionName> = renderCalls.mapNotNull {
        if (it.name != "render") {
            return@mapNotNull null
        }
        val firstParameter = it.parameterList?.getParameter(0) as? StringLiteralExpression
            ?: return@mapNotNull null
        return@mapNotNull actionNameFromPath(firstParameter.contents)
    }

    // Collect ViewBuilder action names (setTemplate/setTemplatePath)
    val viewBuilderCalls = findViewBuilderCalls(method)
    val viewBuilderActionNames = actionNamesFromViewBuilderCalls(viewBuilderCalls)

    // Collect $this->view = 'template' field assignments (CakePHP 2)
    val fieldAssignments = PsiTreeUtil.findChildrenOfType(method, AssignmentExpression::class.java)
    val fieldAssignmentActionNames: List<ActionName> = fieldAssignments.mapNotNull { assignment ->
        val fieldRef = assignment.variable as? FieldReference ?: return@mapNotNull null
        val variable = fieldRef.classReference as? Variable ?: return@mapNotNull null

        // Check it's $this->view
        if (variable.name != "this" || fieldRef.name != "view") {
            return@mapNotNull null
        }

        // Get the assigned value
        val stringLiteral = assignment.value as? StringLiteralExpression ?: return@mapNotNull null
        return@mapNotNull actionNameFromPath(stringLiteral.contents)
    }

    // Combine all action names
    val allOtherActionNames = renderActionNames + viewBuilderActionNames + fieldAssignmentActionNames

    return ActionNames(
        defaultActionName = defaultActionName,
        otherActionNames = allOtherActionNames
    )
}
```

### 5. Refactor TemplateGotoDeclarationHandler

Update `getTemplatePathPreceeding()` to use the new shared `findViewBuilderCalls()` function:

**Location**: `TemplateGotoDeclarationHandler.kt:223-278`

```kotlin
private fun getTemplatePathPreceeding(
    stringLiteral: StringLiteralExpression
): StringLiteralExpression? {
    // Find the containing method to limit our search scope
    val containingMethod = PsiTreeUtil.getParentOfType(
        stringLiteral,
        com.jetbrains.php.lang.psi.elements.Method::class.java
    ) ?: return null

    // Get the text offset of the current setTemplate call
    val currentOffset = stringLiteral.textRange.startOffset

    // Find all ViewBuilder calls using shared function
    val viewBuilderCalls = findViewBuilderCalls(containingMethod)

    // Find the closest preceding setTemplatePath call
    val closestSetTemplatePath = viewBuilderCalls
        .filter { it.methodName == "setTemplatePath" && it.offset < currentOffset }
        .maxByOrNull { it.offset }

    if (closestSetTemplatePath != null) {
        // Need to find the actual StringLiteralExpression for this call
        // Since we have the offset, we can search for it
        val methodRefs = PsiTreeUtil.findChildrenOfType(containingMethod, MethodReference::class.java)
        for (methodRef in methodRefs) {
            if (methodRef.textRange.startOffset == closestSetTemplatePath.offset) {
                val parameterList = methodRef.parameterList
                val firstParam = parameterList?.parameters?.getOrNull(0)
                return firstParam as? StringLiteralExpression
            }
        }
    }

    return null
}
```

### 6. Add Required Imports

**Location**: `CakeController.kt` (top of file)

Add imports for field assignment support:
```kotlin
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable
```

## Benefits of This Approach

1. **Code Reuse**: Shared `findViewBuilderCalls()` function eliminates duplication
2. **Consistent Behavior**: All three components use the same algorithm:
   - `TemplateGotoDeclarationHandler` (goto-declaration)
   - `ViewFileDataIndexer` (indexing)
   - `actionNamesFromControllerMethod()` (line markers & toggle)
3. **Proper Path Handling**: Uses "/" prefix for absolute paths (consistent with existing implementation)
4. **Comprehensive Coverage**: Handles all template assignment methods:
   - `$this->render('template')`
   - `$this->view = 'template'` (CakePHP 2)
   - `$this->viewBuilder()->setTemplate('template')` (CakePHP 3+)
   - `$this->viewBuilder()->setTemplatePath('path')` (CakePHP 3+)

## Algorithm Example

Given this controller method:
```php
public function myAction() {
    $this->viewBuilder()->setTemplatePath('Movie/Nested');
    $this->viewBuilder()->setTemplate('first');
    $this->viewBuilder()->setTemplate('second');
    $this->viewBuilder()->setTemplatePath('Movie/Other');
    $this->viewBuilder()->setTemplate('third');
}
```

The algorithm produces:
1. `setTemplatePath('Movie/Nested')` → store path = "Movie/Nested"
2. `setTemplate('first')` → ActionName(name="first", pathPrefix="/Movie/Nested/")
3. `setTemplate('second')` → ActionName(name="second", pathPrefix="/Movie/Nested/")
4. `setTemplatePath('Movie/Other')` → store path = "Movie/Other"
5. `setTemplate('third')` → ActionName(name="third", pathPrefix="/Movie/Other/")

Result:
- `otherActionNames = [first, second, third]`
- With proper path prefixes for nested views

## Testing Strategy

### Manual Testing
1. Open a controller with ViewBuilder calls
2. Verify line markers appear next to methods with `setTemplate()`
3. Click line marker and verify correct view files are shown
4. Test Cmd/Ctrl+Shift+A toggle from controller to view
5. Test with multiple `setTemplate()` calls in same method
6. Test with `setTemplatePath()` followed by multiple `setTemplate()` calls

### Edge Cases to Verify
1. Method with no ViewBuilder calls → should still work (render/default only)
2. Method with only `setTemplatePath()` → should not create action names
3. Method with only `setTemplate()` → should work with controller path
4. Mix of `render()` and `setTemplate()` calls → both should work
5. CakePHP 2 field assignments should continue working

### Regression Testing
Run existing tests to ensure no breakage:
```bash
./gradlew test --tests "*TemplateGotoDeclarationTest*"
./gradlew test --tests "*ViewVariableTest*"
```

## Implementation Order

1. Add `ViewBuilderCall` data class
2. Implement `findViewBuilderCalls()` function
3. Implement `actionNamesFromViewBuilderCalls()` function
4. Update `actionNamesFromControllerMethod()` to use new functions
5. Refactor `TemplateGotoDeclarationHandler.getTemplatePathPreceeding()` (optional optimization)
6. Add required imports
7. Manual testing of line markers and toggle functionality
8. Regression testing

## Files to Modify

1. **src/main/kotlin/com/daveme/chocolateCakePHP/cake/CakeController.kt**
   - Add `ViewBuilderCall` data class
   - Add `findViewBuilderCalls()` function
   - Add `actionNamesFromViewBuilderCalls()` function
   - Update `actionNamesFromControllerMethod()` function
   - Add imports

2. **src/main/kotlin/com/daveme/chocolateCakePHP/view/TemplateGotoDeclarationHandler.kt** (optional)
   - Refactor `getTemplatePathPreceeding()` to use shared function

## Compatibility Notes

- **CakePHP 2**: Continues to support `$this->view = 'template'` and `$this->render('template')`
- **CakePHP 3+**: Now supports ViewBuilder pattern in addition to `$this->render()`
- **Backward Compatible**: Existing functionality for `render()` calls is preserved
- **Performance**: Minimal impact - same PSI traversal pattern as existing code

## Chained Method Call Support

**See detailed plan**: [support-chained-viewbuilder-calls.md](support-chained-viewbuilder-calls.md)

The implementation also supports chained ViewBuilder method calls:

```php
$this->viewBuilder()->setTemplatePath('Movie/Nested')->setTemplate('custom');
```

### Key Differences for Chained Calls

1. **AST Structure**: The receiver of `setTemplate` is `setTemplatePath` (not `viewBuilder`)
2. **Detection**: Check if `setTemplate`'s receiver is `setTemplatePath` instead of `viewBuilder`
3. **Processing**: Extract both path and template values from the chain
4. **Result**: Create single combined ActionName with path `"/Movie/Nested/custom"`
5. **Deduplication**: Track which `setTemplatePath` calls are used in chains to avoid duplicates

### Chained Call Algorithm

When `findViewBuilderCalls()` encounters a chained call:
1. Detect: `setTemplate`'s receiver is `setTemplatePath`
2. Extract path from `setTemplatePath` parameter
3. Extract template from `setTemplate` parameter
4. Create combined ActionName: `"/{path}/{template}"`
5. Mark `setTemplatePath` as "used in chain" to skip it during separate processing

### Goto-Declaration with Chained Calls

Users can click on either string:
- Click on `'custom'` → Navigate to `Movie/Nested/custom.php`
- Click on `'Movie/Nested'` → Also navigate to `Movie/Nested/custom.php`

### Examples

```php
// Chained call - creates one ActionName
$this->viewBuilder()->setTemplatePath('Movie/Nested')->setTemplate('custom');

// Multiple chained calls - each independent
$this->viewBuilder()->setTemplatePath('A')->setTemplate('one');
$this->viewBuilder()->setTemplatePath('B')->setTemplate('two');

// Mixed: chained + separate
$this->viewBuilder()->setTemplatePath('C')->setTemplate('three');
$this->viewBuilder()->setTemplate('four'); // Uses path 'C' from state tracking
```

## Success Criteria

✅ Line markers appear next to controller methods with ViewBuilder calls
✅ Clicking line markers shows correct view files (including nested paths)
✅ Toggle action (Cmd/Ctrl+Shift+A) works from controller to view with ViewBuilder
✅ Multiple `setTemplate()` calls in same method all get line markers
✅ `setTemplatePath()` correctly affects subsequent `setTemplate()` calls
✅ **Chained calls** (`setTemplatePath()->setTemplate()`) work correctly
✅ **Goto-declaration from chained calls** works for both strings
✅ **No duplicate ActionNames** for chained setTemplatePath calls
✅ Existing `render()` functionality continues working
✅ CakePHP 2 field assignments continue working
✅ All existing tests pass
