# Add Line Markers for $this->view Assignments

## Overview
Add line markers to `$this->view = 'template_name'` assignments in CakePHP 2 controller methods. This is the CakePHP 2 way of specifying which view template to render.

## Current State
- Line markers exist for:
  - Controller method names (shows all associated views)
  - `$this->render()` calls
  - `$this->viewBuilder()->setTemplate()` calls (CakePHP 3+)
- Field assignments to `$this->view` are already tracked in `actionNamesFromControllerMethod()` (see CakeController.kt:246-259)
- But no line marker is shown next to the assignment itself

## Goal
Add a gutter icon next to `$this->view = 'template_name'` assignments that allows navigation to the specified view file, similar to the existing `render()` and `setTemplate()` markers.

## Implementation Plan

### 1. Add `actionNamesFromViewAssignment()` function to CakeController.kt

Location: After `actionNamesFromViewBuilderCall()` function (around line 241)

```kotlin
/**
 * Get ActionNames from a single $this->view field assignment.
 *
 * Used in CakePHP 2 for specifying view templates:
 *   $this->view = 'template_name';
 *
 * @param assignmentExpression The AssignmentExpression to check
 * @return ActionNames or null if not a valid $this->view assignment
 */
fun actionNamesFromViewAssignment(assignmentExpression: AssignmentExpression): ActionNames? {
    val fieldRef = assignmentExpression.variable as? FieldReference ?: return null
    val variable = fieldRef.classReference as? Variable ?: return null

    // Check it's $this->view
    if (variable.name != "this" || fieldRef.name != "view") {
        return null
    }

    // Get the assigned value
    val stringLiteral = assignmentExpression.value as? StringLiteralExpression ?: return null
    val viewName = stringLiteral.contents

    val actionName = actionNameFromPath(viewName)
    return ActionNames(
        defaultActionName = actionName,
        otherActionNames = listOf()
    )
}
```

### 2. Add `markerForViewAssignment()` to ControllerMethodLineMarker.kt

Location: After `markerForSingleViewBuilderCallInAction()` function (around line 134)

```kotlin
//
// Add a marker for a single $this->view = 'template' assignment.
//
private fun markerForViewAssignment(
    project: Project,
    relatedLookupInfo: RelatedLookupInfo,
    element: PsiElement,
): LineMarkerInfo<*>? {
    if (element.firstChild != null) {
        return null
    }

    // Check if this element is the "this" variable
    val variable = element.parent as? Variable ?: return null
    if (variable.name != "this") {
        return null
    }

    // Check if variable.parent is a FieldReference for "view"
    val fieldRef = variable.parent as? FieldReference ?: return null
    if (fieldRef.name != "view") {
        return null
    }

    // Check if this FieldReference is part of an assignment
    val assignment = fieldRef.parent as? AssignmentExpression ?: return null
    if (assignment.variable != fieldRef) {
        return null
    }

    // Get ActionNames for this assignment
    val actionNames = actionNamesFromViewAssignment(assignment)
        ?: return null

    return relatedItemLineMarkerInfo(
        project,
        actionNames,
        relatedLookupInfo,
        element,
        useAltLabel = true
    )
}
```

**Note**: Need to import `AssignmentExpression`:
```kotlin
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
```

### 3. Update `collectSlowLineMarkers()` in ControllerMethodLineMarker.kt

Location: In the main loop, after the viewBuilder marker registration (around line 243)

Add:
```kotlin
// Add line markers for $this->view assignments (CakePHP 2)
val viewAssignmentMarker = markerForViewAssignment(project, relatedLookupInfo, element)
addLineMarkerUnique(result, viewAssignmentMarker)
```

### 4. Add Test Cases

Location: Add to `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake3/ControllerLineMarkerTest.kt`

**Note**: These tests should actually go in a Cake2 test file, but we can add them to Cake3 for now since CakePHP 2 compatibility is still supported.

```kotlin
fun `test that line marker is added to view field assignments`() {
    val files = myFixture.configureByFiles(
        "cake3/src/Controller/AppController.php",
        "cake3/vendor/cakephp.php",
        "cake3/src/Template/Movie/artist.ctp",
        "cake3/src/Controller/MovieController.php",
    )

    val lastFile = files.last()
    myFixture.saveText(lastFile.virtualFile, """
    <?php

    namespace App\Controller;

    use Cake\Controller\Controller;

    class MovieController extends Controller
    {
        public function movie() {
            ${'$'}this->view = "artist";
        }
    }
    """.trimIndent())
    myFixture.openFileInEditor(lastFile.virtualFile)

    // Find all AssignmentExpressions and locate the one with $this->view
    val allAssignments = PsiTreeUtil.findChildrenOfType(myFixture.file, AssignmentExpression::class.java)
    val viewAssignment = allAssignments.find {
        val fieldRef = it.variable as? FieldReference
        fieldRef?.name == "view"
    }
    assertNotNull(viewAssignment)

    // The marker should be on the $this variable element
    val fieldRef = viewAssignment!!.variable as FieldReference
    val thisVariable = fieldRef.classReference as Variable
    val markers = calculateLineMarkers(thisVariable.firstChild!!,
        ControllerMethodLineMarker::class)
    assertEquals(1, markers.size)

    val items = gotoRelatedItems(markers.first())
    assertEquals(1, items.size)

    val infos = getRelatedItemInfos(items)
    val expected = setOf(
        RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"),
    )
    assertEquals(expected, infos)
}

fun `test that line marker is added to view field assignments with nested path`() {
    val files = myFixture.configureByFiles(
        "cake3/src/Controller/AppController.php",
        "cake3/vendor/cakephp.php",
        "cake3/src/Template/Movie/Nested/custom.ctp",
        "cake3/src/Controller/MovieController.php",
    )

    val lastFile = files.last()
    myFixture.saveText(lastFile.virtualFile, """
    <?php

    namespace App\Controller;

    use Cake\Controller\Controller;

    class MovieController extends Controller
    {
        public function movie() {
            ${'$'}this->view = "Nested/custom";
        }
    }
    """.trimIndent())
    myFixture.openFileInEditor(lastFile.virtualFile)

    val allAssignments = PsiTreeUtil.findChildrenOfType(myFixture.file, AssignmentExpression::class.java)
    val viewAssignment = allAssignments.find {
        val fieldRef = it.variable as? FieldReference
        fieldRef?.name == "view"
    }
    assertNotNull(viewAssignment)

    val fieldRef = viewAssignment!!.variable as FieldReference
    val thisVariable = fieldRef.classReference as Variable
    val markers = calculateLineMarkers(thisVariable.firstChild!!,
        ControllerMethodLineMarker::class)
    assertEquals(1, markers.size)

    val items = gotoRelatedItems(markers.first())
    assertEquals(1, items.size)

    val infos = getRelatedItemInfos(items)
    val expected = setOf(
        RelatedItemInfo(filename = "custom.ctp", containingDir = "Nested"),
    )
    assertEquals(expected, infos)
}


**Note**: Need to import `AssignmentExpression` and `FieldReference` in the test file:
```kotlin
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable
```

## Testing Strategy

1. Create the helper function in CakeController.kt
2. Create the marker function in ControllerMethodLineMarker.kt
3. Register it in collectSlowLineMarkers()
4. Add the three test cases
5. Run tests to verify:
   - Simple assignments work
   - Nested path assignments work
   - All existing tests still pass

## Edge Cases to Consider

1. **Dynamic assignments**: `$this->view = $someVariable` - should NOT get a marker (already handled by checking for StringLiteralExpression)
2. **Concatenated strings**: `$this->view = 'prefix/' . $name` - should NOT get a marker (not a StringLiteralExpression)
3. **Multiple assignments**: Multiple `$this->view` assignments in the same method - each should get its own marker
4. **Assignment in conditions**: `if ($condition) { $this->view = 'foo'; }` - should still get a marker

## Pattern Matching

The marker should be placed on the `$this` variable element, similar to:
- `$this->render()` markers
- `$this->viewBuilder()->setTemplate()` markers

This provides consistency in the UI.

## CakePHP Version Compatibility

- **CakePHP 2**: Primary use case - `$this->view` is the standard way to set templates
- **CakePHP 3+**: Less common but still supported as legacy code might use it
- The existing code in `actionNamesFromControllerMethod()` already handles this for all versions

## Files to Modify

1. `src/main/kotlin/com/daveme/chocolateCakePHP/cake/CakeController.kt`
   - Add `actionNamesFromViewAssignment()` function

2. `src/main/kotlin/com/daveme/chocolateCakePHP/controller/ControllerMethodLineMarker.kt`
   - Add import for `AssignmentExpression`
   - Add `markerForViewAssignment()` function
   - Update `collectSlowLineMarkers()` to call the new function

3. `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake3/ControllerLineMarkerTest.kt`
   - Add imports for `AssignmentExpression`, `FieldReference`, `Variable`
   - Add 3 test cases

## Expected Result

After implementation, users will see a Cake icon in the gutter next to `$this->view = 'template'` assignments that allows them to:
- Click to navigate to the view file
- See all available view file variants (json/, xml/, etc.)
- Ctrl+Click to create the view file if it doesn't exist (via existing NavigateToViewPopupHandler)
