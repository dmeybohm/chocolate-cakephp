# Support Chained ViewBuilder Method Calls

## Overview
Add support for chained ViewBuilder method calls where `setTemplatePath()` and `setTemplate()` are combined in a single expression:

```php
$this->viewBuilder()->setTemplatePath('Somewhere')->setTemplate('somethingElse')
```

This pattern is common in CakePHP and should work alongside the existing support for separate calls.

## Current Behavior

Currently supported patterns:
```php
// Separate calls (with state tracking)
$this->viewBuilder()->setTemplatePath('Movie/Nested');
$this->viewBuilder()->setTemplate('custom');

// Simple setTemplate
$this->viewBuilder()->setTemplate('artist');
```

**Not yet supported:**
```php
// Chained calls
$this->viewBuilder()->setTemplatePath('Movie/Nested')->setTemplate('custom');
```

## AST Structure

For chained calls, the PSI tree structure is:
```
METHOD_REFERENCE (setTemplate)
  └─ receiver: METHOD_REFERENCE (setTemplatePath)
      └─ receiver: METHOD_REFERENCE (viewBuilder)
          └─ receiver: VARIABLE ($this)
      └─ PARAMETER_LIST
          └─ STRING ('Somewhere')
  └─ PARAMETER_LIST
      └─ STRING ('somethingElse')
```

Key difference: The receiver of `setTemplate` is `setTemplatePath` (not `viewBuilder`).

## Implementation Plan

### Key Principle: Return Separate Calls to Preserve State Tracking

**IMPORTANT**: Do NOT combine chained calls at parse time. Instead:
1. Detect BOTH patterns (normal and chained)
2. Return them as SEPARATE ViewBuilderCall objects
3. Let existing state tracking algorithms handle path application

**Why**: PSI/AST traversal naturally visits both `setTemplatePath` and `setTemplate` nodes separately in a chain. By returning them as separate calls, the existing state tracking in `actionNamesFromViewBuilderCalls()` and `indexViewBuilderCalls()` will correctly apply the path prefix to subsequent setTemplate calls in the same method.

**Example**:
```php
// Chained call
$this->viewBuilder()->setTemplatePath('A')->setTemplate('one');
// Separate call - should also use path 'A'!
$this->viewBuilder()->setTemplate('two');
```

If we combined at parse time, the second `setTemplate('two')` would lose the 'A' prefix because we didn't return the `setTemplatePath` separately.

### 1. Update findViewBuilderCalls() in CakeController.kt

**File**: `src/main/kotlin/com/daveme/chocolateCakePHP/cake/CakeController.kt` (lines 101-132)

**Changes**:
1. Detect chained calls by checking if `setTemplate`'s receiver is `setTemplatePath`
2. Also detect chained calls by checking if `setTemplatePath`'s parent is `setTemplate`
3. Return BOTH as separate ViewBuilderCall objects
4. Let `actionNamesFromViewBuilderCalls()` handle the path combination via state tracking

**Implementation**:
```kotlin
fun findViewBuilderCalls(element: PsiElement): List<ViewBuilderCall> {
    val methodRefs = PsiTreeUtil.findChildrenOfType(element, MethodReference::class.java)

    return methodRefs.mapNotNull { methodRef ->
        val methodName = methodRef.name
        if (methodName != "setTemplate" && methodName != "setTemplatePath") {
            return@mapNotNull null
        }

        val receiverMethodRef = methodRef.classReference as? MethodReference ?: return@mapNotNull null

        // Pattern 1: Normal calls - receiver is viewBuilder()
        if (receiverMethodRef.name == "viewBuilder") {
            val receiverVariable = receiverMethodRef.classReference as? Variable ?: return@mapNotNull null
            if (receiverVariable.name != "this") {
                return@mapNotNull null
            }

            val parameterList = methodRef.parameterList
            val firstParam = parameterList?.parameters?.getOrNull(0) as? StringLiteralExpression
                ?: return@mapNotNull null

            return@mapNotNull ViewBuilderCall(
                methodName = methodName,
                parameterValue = firstParam.contents,
                offset = methodRef.textRange.startOffset
            )
        }

        // Pattern 2: Chained calls - setTemplate's receiver is setTemplatePath
        if (methodName == "setTemplate" && receiverMethodRef.name == "setTemplatePath") {
            // Verify the chain goes back to viewBuilder()
            val viewBuilderRef = receiverMethodRef.classReference as? MethodReference ?: return@mapNotNull null
            if (viewBuilderRef.name != "viewBuilder") return@mapNotNull null
            val thisVar = viewBuilderRef.classReference as? Variable ?: return@mapNotNull null
            if (thisVar.name != "this") return@mapNotNull null

            // Extract the template parameter
            val templateParam = methodRef.parameterList?.parameters?.getOrNull(0) as? StringLiteralExpression
                ?: return@mapNotNull null

            // Return as normal setTemplate - state tracking will handle the path
            return@mapNotNull ViewBuilderCall(
                methodName = "setTemplate",
                parameterValue = templateParam.contents,  // Just the template name, not combined!
                offset = methodRef.textRange.startOffset
            )
        }

        // Pattern 3: Chained setTemplatePath - will be processed normally by Pattern 1
        // The PSI tree visits it separately, so no special handling needed

        return@mapNotNull null
    }.sortedBy { it.offset }
}
```

**Algorithm**:
1. Pattern 1 (Normal): Receiver is `viewBuilder` → return as-is
2. Pattern 2 (Chained setTemplate): Receiver is `setTemplatePath` → verify chain, return as normal setTemplate
3. Pattern 3 (Chained setTemplatePath): Receiver is `viewBuilder` → caught by Pattern 1
4. PSI traversal naturally visits both nodes in a chain
5. State tracking in `actionNamesFromViewBuilderCalls()` applies the path prefix

**Key Insight**: The `setTemplatePath` in a chain is ALSO visited as a separate node by PSI traversal, and it matches Pattern 1 (receiver is `viewBuilder`). We don't need special handling - just let it be returned as a normal `setTemplatePath` call.

### 2. Update handleViewBuilderCall() in TemplateGotoDeclarationHandler.kt

**File**: `src/main/kotlin/com/daveme/chocolateCakePHP/view/TemplateGotoDeclarationHandler.kt` (lines 151-221)

**Changes**:
Handle both clicking scenarios in chained calls:
1. Click on 'somethingElse' in `->setTemplate('somethingElse')` → navigate to view
2. Click on 'Somewhere' in `->setTemplatePath('Somewhere')` → navigate to view (when chained with setTemplate)

**Implementation**:
```kotlin
private fun handleViewBuilderCall(psiElement: PsiElement, settings: Settings): Array<PsiElement>? {
    val stringLiteral = psiElement.context as? StringLiteralExpression ?: return null
    val parameterList = stringLiteral.parent as? ParameterList ?: return null
    val methodRef = parameterList.parent as? MethodReference ?: return null

    val methodName = methodRef.name
    if (methodName != "setTemplate" && methodName != "setTemplatePath") {
        return null
    }

    val receiverMethodRef = methodRef.classReference as? MethodReference ?: return null

    // Handle chained calls: ->setTemplatePath('path')->setTemplate('name')
    if (methodName == "setTemplate" && receiverMethodRef.name == "setTemplatePath") {
        // User clicked on template in a chained call
        val pathParam = receiverMethodRef.parameterList?.parameters?.getOrNull(0) as? StringLiteralExpression
            ?: return null

        // Verify chain goes back to viewBuilder()
        val viewBuilderRef = receiverMethodRef.classReference as? MethodReference ?: return null
        if (viewBuilderRef.name != "viewBuilder") return null
        val thisVar = viewBuilderRef.classReference as? Variable ?: return null
        if (thisVar.name != "this") return null

        // Navigate with combined path
        val viewContents = stringLiteral.contents
        val viewName = "/${pathParam.contents}/${viewContents}"

        return navigateToView(psiElement, settings, viewName)
    }

    // Handle setTemplatePath in a chain (user clicked on the path)
    if (methodName == "setTemplatePath") {
        // Check if this setTemplatePath is chained with a setTemplate
        val parentMethod = PsiTreeUtil.getParentOfType(methodRef, com.jetbrains.php.lang.psi.elements.Method::class.java)
        val chainedSetTemplate = findChainedSetTemplate(methodRef, parentMethod)

        if (chainedSetTemplate != null) {
            // This is chained - navigate to the final view
            val templateParam = chainedSetTemplate.parameterList?.parameters?.getOrNull(0) as? StringLiteralExpression
                ?: return null
            val viewContents = stringLiteral.contents
            val viewName = "/${viewContents}/${templateParam.contents}"

            return navigateToView(psiElement, settings, viewName)
        }

        // Standalone setTemplatePath - not supported for goto-declaration
        return null
    }

    // Non-chained setTemplate
    if (receiverMethodRef.name != "viewBuilder") {
        return null
    }
    val receiverVariable = receiverMethodRef.classReference as? Variable ?: return null
    if (receiverVariable.name != "this") {
        return null
    }

    val containingFile = psiElement.containingFile
    val virtualFile = containingFile.virtualFile
    val controllerPath = controllerPathFromControllerFile(virtualFile)
        ?: return null

    // Get template name with any preceding setTemplatePath
    val viewContents = stringLiteral.contents
    val templatePathLiteral = getTemplatePathPreceeding(stringLiteral)
    val viewName = if (templatePathLiteral != null) {
        "/${templatePathLiteral.contents}/${viewContents}"
    } else {
        viewContents
    }

    return navigateToView(psiElement, settings, viewName)
}

/**
 * Find a setTemplate call that is chained with the given setTemplatePath call.
 *
 * Looks for: $this->viewBuilder()->setTemplatePath('path')->setTemplate('name')
 * where methodRef is the setTemplatePath call.
 */
private fun findChainedSetTemplate(
    setTemplatePathRef: MethodReference,
    containingMethod: com.jetbrains.php.lang.psi.elements.Method?
): MethodReference? {
    if (containingMethod == null) return null

    val allMethodRefs = PsiTreeUtil.findChildrenOfType(containingMethod, MethodReference::class.java)

    return allMethodRefs.find { methodRef ->
        if (methodRef.name != "setTemplate") return@find false

        // Check if its receiver is our setTemplatePath
        val receiver = methodRef.classReference as? MethodReference ?: return@find false
        receiver == setTemplatePathRef
    }
}

/**
 * Helper to navigate to a view file with the given view name.
 */
private fun navigateToView(
    psiElement: PsiElement,
    settings: Settings,
    viewName: String
): Array<PsiElement>? {
    val containingFile = psiElement.containingFile
    val virtualFile = containingFile.virtualFile
    val controllerPath = controllerPathFromControllerFile(virtualFile)
        ?: return null

    val actionName = actionNameFromPath(viewName)
    val actionNames = ActionNames(defaultActionName = actionName)

    val topSourceDirectory = topSourceDirectoryFromSourceFile(settings, containingFile)
        ?: return null
    val allTemplatesPaths = allTemplatePathsFromTopSourceDirectory(
        psiElement.project,
        settings,
        topSourceDirectory
    ) ?: return null

    val allViewPaths = allViewPathsFromController(
        controllerPath,
        allTemplatesPaths,
        settings,
        actionNames
    )
    val files = viewFilesFromAllViewPaths(
        project = psiElement.project,
        allTemplatesPaths = allTemplatesPaths,
        allViewPaths = allViewPaths
    )
    return files.toTypedArray()
}
```

### 3. Update ViewFileDataIndexer

**File**: `src/main/kotlin/com/daveme/chocolateCakePHP/view/viewfileindex/ViewFileDataIndexer.kt` (lines 310-369)

**Current Problem**: The `parseViewBuilderCall()` function only recognizes when the receiver is `viewBuilder()`. In chained calls, the receiver of `setTemplate` is `setTemplatePath`, so it's completely missed.

**Analysis**: The indexer uses AST-based parsing, but the same principle applies - AST traversal naturally visits both nodes in a chain separately.

**Changes**:
1. Make `parseViewBuilderCall()` recognize BOTH patterns (normal and chained)
2. Return them as SEPARATE ViewBuilderCallInfo objects
3. Let existing `indexViewBuilderCalls()` state tracking handle the path combination

**Implementation**:
```kotlin
private fun parseViewBuilderCall(node: ASTNode): ViewBuilderCallInfo? {
    if (node.elementType != PhpElementTypes.METHOD_REFERENCE) {
        return null
    }

    val methodName = getMethodName(node) ?: return null
    if (methodName != "setTemplate" && methodName != "setTemplatePath") {
        return null
    }

    val receiverNode = getClassReference(node) ?: return null
    val receiverMethodRef = if (receiverNode.elementType == PhpElementTypes.METHOD_REFERENCE) {
        receiverNode
    } else {
        return null
    }

    val receiverMethodName = getMethodName(receiverMethodRef) ?: return null

    // Pattern 1: Normal calls - receiver is viewBuilder()
    if (receiverMethodName == "viewBuilder" && isViewBuilderMethodCall(receiverMethodRef)) {
        val parameterValue = extractStringParameter(node) ?: return null
        val methodStartOffset = findContainingMethodStartOffset(node) ?: return null

        return ViewBuilderCallInfo(
            methodName = methodName,
            parameterValue = parameterValue,
            offset = node.startOffset,
            containingMethodStartOffset = methodStartOffset
        )
    }

    // Pattern 2: Chained calls - setTemplate's receiver is setTemplatePath
    if (methodName == "setTemplate" && receiverMethodName == "setTemplatePath") {
        // Verify the chain goes back to viewBuilder()
        val viewBuilderNode = getClassReference(receiverMethodRef) ?: return null
        if (!isViewBuilderMethodCall(viewBuilderNode)) {
            return null
        }

        val parameterValue = extractStringParameter(node) ?: return null
        val methodStartOffset = findContainingMethodStartOffset(node) ?: return null

        // Return as normal setTemplate - state tracking will handle the path
        return ViewBuilderCallInfo(
            methodName = "setTemplate",
            parameterValue = parameterValue,  // Just the template name, not combined!
            offset = node.startOffset,
            containingMethodStartOffset = methodStartOffset
        )
    }

    // Pattern 3: Chained setTemplatePath - will be processed normally by Pattern 1
    // AST traversal visits it separately, so no special handling needed

    return null
}

/**
 * Helper to check if an AST node represents a setTemplatePath call
 */
private fun isSetTemplatePathCall(node: ASTNode?): Boolean {
    if (node == null || node.elementType != PhpElementTypes.METHOD_REFERENCE) {
        return false
    }
    val methodName = getMethodName(node)
    return methodName == "setTemplatePath"
}
```

**How State Tracking Works**:

The existing `indexViewBuilderCalls()` function (lines 237-297) processes calls in order:

```kotlin
for (call in sortedCalls) {
    when (call.methodName) {
        "setTemplatePath" -> {
            currentTemplatePath = parameterValue  // Updates state
        }
        "setTemplate" -> {
            val finalPath = if (currentTemplatePath != null) {
                "/$currentTemplatePath/$parameterValue"  // Uses state
            } else {
                parameterValue
            }
            // ... index it
        }
    }
}
```

**Example**:
```php
$this->viewBuilder()->setTemplatePath('A')->setTemplate('one');
$this->viewBuilder()->setTemplate('two');
```

AST visits these nodes in order:
1. `setTemplatePath('A')` → returns ViewBuilderCallInfo("setTemplatePath", "A")
2. `setTemplate('one')` → returns ViewBuilderCallInfo("setTemplate", "one")
3. `setTemplate('two')` → returns ViewBuilderCallInfo("setTemplate", "two")

State tracking processes them:
1. Process `setTemplatePath("A")` → sets `currentTemplatePath = "A"`
2. Process `setTemplate("one")` → indexes as `/A/one` (uses state)
3. Process `setTemplate("two")` → indexes as `/A/two` (state preserved!)

**Key Insight**: By returning separate calls, state tracking "just works" for both chained and separate patterns.

### 4. Add Test Cases

**Files**:
- `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake3/TemplateGotoDeclarationTest.kt`
- `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake4/TemplateGotoDeclarationTest.kt`
- `src/test/kotlin/com/daveme/chocolateCakePHP/test/cake5/TemplateGotoDeclarationTest.kt`

**Test Cases**:
```kotlin
fun `test TemplateGotoDeclarationHandler with chained viewBuilder calls`() {
    myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
    <?php

    namespace App\Controller;

    class MovieController extends AppController {
        public function chainedTest() {
            ${'$'}this->viewBuilder()->setTemplatePath('Movie/Nested')->setTemplate('<caret>custom');
        }
    }
    """.trimIndent())

    val handler = TemplateGotoDeclarationHandler()
    assertGotoDeclarationHandlerGoesToFilename(handler, "custom.php")
}

fun `test TemplateGotoDeclarationHandler with chained viewBuilder calls clicking on path`() {
    myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
    <?php

    namespace App\Controller;

    class MovieController extends AppController {
        public function chainedTest() {
            ${'$'}this->viewBuilder()->setTemplatePath('<caret>Movie/Nested')->setTemplate('custom');
        }
    }
    """.trimIndent())

    val handler = TemplateGotoDeclarationHandler()
    assertGotoDeclarationHandlerGoesToFilename(handler, "custom.php")
}
```

**Update controller fixtures**:
```php
// Add to cake3/src/Controller/MovieController.php
public function chainedViewBuilderTest() {
    $this->viewBuilder()->setTemplatePath('Movie/Nested')->setTemplate('custom');
    $metadata = $this->MovieMetadata->generateMetadata();
    $moviesTable = $this->fetchTable('Movies');
    $this->set(compact('metadata', 'moviesTable'));
}
```

**View variable tests**:
```kotlin
fun `test variable list is communicated with chained viewBuilder calls`() {
    myFixture.configureByFilePathAndText("cake5/templates/Movie/Nested/custom.php", """
    <?php
    echo ${'$'}<caret>
    """.trimIndent())
    myFixture.completeBasic()

    val result = myFixture.lookupElementStrings
    assertTrue(result!!.contains("${'$'}moviesTable"))
}

fun `test variable type is communicated with chained viewBuilder calls`() {
    myFixture.configureByFilePathAndText("cake5/templates/Movie/Nested/custom.php", """
    <?php
    echo ${'$'}moviesTable-><caret>
    """.trimIndent())
    myFixture.completeBasic()

    val result = myFixture.lookupElementStrings
    assertTrue(result!!.contains("findOwnedBy"))
}
```

### 5. Update Documentation

**File**: `action-names-update-for-set-template.md`

The section "Chained Method Call Support" has already been added (lines 391-437) with the key information:

**Key Points Documented**:
1. Return separate ViewBuilderCall objects for chained calls
2. Parser detects both patterns (normal and chained)
3. State tracking applies path prefixes automatically
4. No duplicate ActionNames created
5. Goto-declaration works from both strings in the chain

**Examples Documented**:
```php
// Chained call - both nodes returned separately
$this->viewBuilder()->setTemplatePath('A')->setTemplate('one');

// Separate call - state tracking applies 'A' prefix
$this->viewBuilder()->setTemplate('two');  // Results in /A/two

// Multiple chains - each processed independently
$this->viewBuilder()->setTemplatePath('B')->setTemplate('three');
```

**Algorithm Documented**:
1. PSI/AST traversal visits both `setTemplatePath` and `setTemplate` nodes
2. Parser returns them as separate ViewBuilderCall objects
3. State tracking in `actionNamesFromViewBuilderCalls()` processes in order:
   - Sees `setTemplatePath('A')` → stores "A" as current path
   - Sees `setTemplate('one')` → creates ActionName with path "/A/one"
   - Sees `setTemplate('two')` → creates ActionName with path "/A/two" (preserved!)
4. Result: No special chaining logic needed, state tracking handles everything

## Implementation Order

1. ✅ Update `findViewBuilderCalls()` in CakeController.kt
2. ✅ Update `handleViewBuilderCall()` in TemplateGotoDeclarationHandler.kt
3. ✅ Add helper methods (`findChainedSetTemplate()`, `navigateToView()`)
4. ⚠️ Check if ViewFileDataIndexer needs updates (test first)
5. ✅ Add test fixtures for chained calls
6. ✅ Add goto-declaration tests
7. ✅ Add view variable tests
8. ✅ Run all regression tests
9. ✅ Update documentation

## Edge Cases to Handle

1. **Multiple chains in same method**:
```php
$this->viewBuilder()->setTemplatePath('A')->setTemplate('one');
$this->viewBuilder()->setTemplatePath('B')->setTemplate('two');
```
**Expected behavior**:
- Parser returns: `setTemplatePath('A')`, `setTemplate('one')`, `setTemplatePath('B')`, `setTemplate('two')`
- State tracking: 'A' applies to 'one', 'B' applies to 'two'
- Result: Two ActionNames: `/A/one` and `/B/two`

2. **Chain followed by separate call** (Critical test case!):
```php
$this->viewBuilder()->setTemplatePath('A')->setTemplate('one');
$this->viewBuilder()->setTemplate('two'); // Should still use 'A' path!
```
**Expected behavior**:
- Parser returns: `setTemplatePath('A')`, `setTemplate('one')`, `setTemplate('two')`
- State tracking: 'A' applies to both 'one' and 'two'
- Result: Two ActionNames: `/A/one` and `/A/two`

**This is WHY we return separate calls!** If we combined the chained call, the `setTemplatePath('A')` wouldn't be in the list, so `setTemplate('two')` would lose the 'A' prefix.

3. **Clicking on setTemplatePath in a chain**:
```php
$this->viewBuilder()->setTemplatePath('<caret>Movie/Nested')->setTemplate('custom');
```
**Expected behavior**: Should navigate to the final view file `Movie/Nested/custom.php` (not just directory)
**Implementation**: `handleViewBuilderCall()` needs special logic to find the chained `setTemplate` and combine paths for navigation.

4. **Standalone setTemplatePath (not chained)**:
```php
$this->viewBuilder()->setTemplatePath('Movie/Nested');
// later...
$this->viewBuilder()->setTemplate('custom');
```
**Expected behavior**: Already fully supported by existing state tracking
**Result**: One ActionName: `/Movie/Nested/custom`

5. **Path reset in mixed scenario**:
```php
$this->viewBuilder()->setTemplatePath('A')->setTemplate('one');
$this->viewBuilder()->setTemplate('two');  // Uses 'A'
$this->viewBuilder()->setTemplatePath('B')->setTemplate('three');
$this->viewBuilder()->setTemplate('four');  // Uses 'B'
```
**Expected behavior**:
- Result: `/A/one`, `/A/two`, `/B/three`, `/B/four`
- State tracking correctly resets path when new `setTemplatePath` is encountered

## Testing Strategy

### Unit Tests
1. Test findViewBuilderCalls() with chained calls
2. Test findViewBuilderCalls() with mixed patterns
3. Test findViewBuilderCalls() doesn't create duplicates

### Integration Tests
1. Test goto-declaration from chained setTemplate
2. Test goto-declaration from chained setTemplatePath
3. Test line markers appear for chained calls
4. Test toggle action works with chained calls
5. Test view variable completion works with chained calls

### Regression Tests
Ensure all existing tests still pass:
```bash
./gradlew test --tests "*TemplateGotoDeclarationTest*"
./gradlew test --tests "*ViewVariableTest*"
```

## Success Criteria

✅ **Parser returns separate calls**: Both `setTemplatePath` and `setTemplate` in a chain are returned as separate ViewBuilderCall objects
✅ **State tracking preserved**: A chained `setTemplatePath('A')->setTemplate('one')` followed by separate `setTemplate('two')` correctly results in `/A/one` and `/A/two`
✅ **Goto-declaration from template**: Clicking on template in `->setTemplate('custom')` navigates to correct view
✅ **Goto-declaration from path**: Clicking on path in `->setTemplatePath('Movie/Nested')` (when chained) navigates to the final view file
✅ **Line markers**: Line markers appear next to controller methods with chained calls, showing correct view files
✅ **Toggle action**: Toggle action works from controller to view with chained calls
✅ **View variable completion**: View variables are available in views referenced by chained calls
✅ **No duplicates**: No duplicate ActionNames created for chained setTemplatePath calls
✅ **Mixed patterns**: Combined scenarios with both chained and separate calls work correctly
✅ **Path reset**: Multiple chained calls correctly reset the path prefix
✅ **All existing tests pass**: No regression in existing functionality
