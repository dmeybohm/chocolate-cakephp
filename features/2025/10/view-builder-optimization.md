# Performance Analysis of setTemplatePath/setTemplate Parsing

## Current Implementation

The viewBuilder parsing in `ViewFileDataIndexer.kt` uses recursive AST traversal to find `setTemplate()` and `setTemplatePath()` calls.

### Recursion Structure

**Main traversal:**
1. `findViewBuilderCallsRecursive()` (lines 293-308) - traverses entire AST tree recursively
2. For each METHOD_REFERENCE node, calls `parseViewBuilderCall()` (line 296)
3. `parseViewBuilderCall()` calls helper methods that iterate through child nodes:
   - `getMethodName()` (lines 411-420) - single level iteration
   - `getReceiverMethodRef()` (lines 425-434) - single level iteration
   - `isViewBuilderMethodCall()` (lines 436-455) - single level iteration
   - `findContainingMethod()` (lines 457-466) - walks up parent chain

### Performance Characteristics

**Positive aspects:**
- The recursion pattern is **identical** to other AST traversals (render, element, field assignments)
- Early filtering: only processes METHOD_REFERENCE nodes (line 295)
- Efficient child traversal using `firstChildNode`/`treeNext` pattern (avoids `toList()` allocation)
- Helper methods use iteration, not additional recursion

**Potential concerns:**
1. **Traverses entire AST** for every PHP controller file, even if no viewBuilder calls exist
2. **parseViewBuilderCall() is called for EVERY METHOD_REFERENCE** in the file, not just viewBuilder-related ones
3. **Multiple helper function calls** per METHOD_REFERENCE node (4-5 function calls in worst case)
4. **findContainingMethod() walks up parent chain** every time a match is found (lines 457-466)
5. **No early bailout** - unlike some other patterns, there's no quick text search before AST traversal

### Comparison to Other Parsing Methods

The viewBuilder parsing follows the same pattern as:
- `findMethodCallsRecursive()` - for render/element calls
- `findMethodDeclarationsRecursive()` - for method declarations
- `findFieldAssignmentsRecursive()` - for $this->view assignments

So it's **not significantly more recursive** than other parsing operations.

## Performance Optimization Recommendations

### 1. Add Early Bailout (High Impact)

Before traversing the AST, check if "viewBuilder" text exists in the file:

```kotlin
override fun map(inputData: FileContent): MutableMap<String, List<ViewReferenceData>> {
    // ... existing checks ...

    // Quick text scan before AST traversal
    val fileText = inputData.contentAsText.toString()
    if (!fileText.contains("viewBuilder")) {
        // Skip viewBuilder parsing entirely
        val astViewBuilderCalls = emptyList<ViewBuilderCallInfo>()
    } else {
        val astViewBuilderCalls = findViewBuilderCalls(rootNode)
            .filter { it.parameterValue != null }
    }
}
```

**Impact:** Eliminates AST traversal for files without viewBuilder calls (likely majority of files)

### 2. Optimize parseViewBuilderCall() - Quick Rejection (Medium Impact)

Add early rejection in `parseViewBuilderCall()` before parsing the full structure:

```kotlin
private fun parseViewBuilderCall(node: ASTNode): ViewBuilderCallInfo? {
    // Quick check: get method name first
    val methodName = getMethodName(node)
    if (methodName != "setTemplate" && methodName != "setTemplatePath") {
        return null  // Early exit for non-target methods
    }

    // Now parse the rest of the structure
    var parameterValue: String? = null
    var receiverMethodRef: ASTNode? = null

    var child = node.firstChildNode
    while (child != null) {
        when (child.elementType) {
            PhpElementTypes.METHOD_REFERENCE -> receiverMethodRef = child
            PhpElementTypes.PARAMETER_LIST -> {
                // Extract parameter...
            }
        }
        child = child.treeNext
    }

    // ... rest of validation ...
}
```

**Impact:** Reduces processing for non-viewBuilder METHOD_REFERENCE nodes (99% of method calls)

### 3. Cache Containing Method (Medium Impact)

Instead of walking up the parent chain for every match, track the containing method during traversal:

```kotlin
private fun findViewBuilderCallsRecursive(
    node: ASTNode,
    result: MutableList<ViewBuilderCallInfo>,
    containingMethodOffset: Int = -1
) {
    // Track when we enter a CLASS_METHOD
    val currentMethodOffset = if (node.elementType == PhpElementTypes.CLASS_METHOD) {
        node.startOffset
    } else {
        containingMethodOffset
    }

    if (node.elementType == PhpElementTypes.METHOD_REFERENCE) {
        val viewBuilderCall = parseViewBuilderCall(node, currentMethodOffset)
        if (viewBuilderCall != null) {
            result.add(viewBuilderCall)
        }
    }

    var child = node.firstChildNode
    while (child != null) {
        findViewBuilderCallsRecursive(child, result, currentMethodOffset)
        child = child.treeNext
    }
}

private fun parseViewBuilderCall(
    node: ASTNode,
    containingMethodOffset: Int
): ViewBuilderCallInfo? {
    // ... existing parsing ...

    // Use passed-in offset instead of walking up tree
    return ViewBuilderCallInfo(
        methodName = methodName,
        parameterValue = parameterValue,
        offset = node.startOffset,
        containingMethodStartOffset = containingMethodOffset
    )
}
```

**Impact:** Eliminates repeated parent chain walks (O(depth) per match -> O(1))

### 4. Combine Helper Calls (Low Impact)

Instead of multiple passes over children, parse everything in one pass:

```kotlin
private fun parseViewBuilderCall(node: ASTNode): ViewBuilderCallInfo? {
    var methodName: String? = null
    var parameterValue: String? = null
    var receiverMethodRef: ASTNode? = null

    // Single pass through children
    var child = node.firstChildNode
    while (child != null) {
        when (child.elementType) {
            PhpTokenTypes.IDENTIFIER -> methodName = child.text
            PhpElementTypes.METHOD_REFERENCE -> receiverMethodRef = child
            PhpElementTypes.PARAMETER_LIST -> {
                // Extract parameter inline
            }
        }
        child = child.treeNext
    }

    // Early exit after single pass
    if (methodName != "setTemplate" && methodName != "setTemplatePath") {
        return null
    }

    // Now check receiver (only if method name matched)
    val receiverMethodName = receiverMethodRef?.let { getMethodName(it) }
    // ... rest of logic ...
}
```

**Impact:** Reduces function call overhead slightly

## Recommendation

The recursion itself is **necessary and appropriate** for AST traversal. However, the performance could be improved with:

1. **Priority 1:** Add early bailout with text search (biggest impact)
2. **Priority 2:** Optimize parseViewBuilderCall() with quick rejection
3. **Priority 3:** Cache containing method offset during traversal

The current implementation is not "unnecessarily recursive" - the recursion is required to traverse the AST. The optimization opportunities are in reducing the work done per node, not eliminating recursion.

## Testing Impact

Any optimization should be verified against the existing test suite:
- `ViewVariableTest` - tests for view variable tracking
- `ControllerLineMarkerTest` - tests for controller-to-view navigation
- Run full test suite to ensure no regressions
