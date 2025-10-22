# Fix Array Syntax Type Resolution - Refactor Expression Resolution

## Problem Statement

When using `$this->set(['key' => $value])` array syntax, expression values get incorrect types with `[]` suffix:
- `$this->set(['singleVar' => $this->statusMessage])` → `$singleVar` has type `string[]` instead of `string`
- `$this->set(['total' => $count])` → `$total` has type `int[]` instead of `int`

Literal values work correctly:
- `$this->set(['title' => 'Test'])` → `$title` has type `string` ✅
- `$this->set(['count' => 42])` → `$count` has type `int` ✅

## Root Cause

The `resolveExpressionType()` method walks up the PSI tree to find the complete expression, which works for PAIR syntax but fails for ARRAY syntax:

### Tree Structure for Array Syntax:
```
ParameterList
  └─ ArrayCreationExpression ['singleVar' => $this->statusMessage]
      └─ HashArrayElement
          └─ Array value
              └─ FieldReference ($this->statusMessage)  ← offset points here
```

### Current Behavior:
1. Start at FieldReference (`$this->statusMessage`)
2. Walk up parent chain until hitting `ParameterList`
3. Stop at `ArrayCreationExpression` (last element before ParameterList)
4. Resolve type of **ArrayCreationExpression** → `string[]` ❌

### Expected Behavior:
1. Start at FieldReference (`$this->statusMessage`)
2. Get type directly from this element → `string` ✅

## Analysis: PAIR vs ARRAY Context

### PAIR Syntax: `$this->set('name', $expression)`
**Needs walking** to handle chained calls:
```php
$this->set('table', $this->getTableLocator()->get('Movies'))
```
- Offset points to inner part of chain
- Must walk up to find complete outer expression
- Stops at `ParameterList` boundary

### ARRAY Syntax: `$this->set(['name' => $expression])`
**No walking needed** - offset already points to exact expression:
```php
$this->set(['var' => $this->property])
```
- Offset points directly to `$this->property` (FieldReference)
- Can get type immediately from this element
- Walking up would incorrectly reach the containing array

## Solution: Separate Methods for Different Contexts

Instead of using a boolean parameter to change behavior, create separate methods with clear purposes:

### Refactored Structure:

```kotlin
// For PAIR context - walks up to find complete expression
private fun resolveExpressionTypeFromPair(project: Project, controllerFile: PsiFile?): PhpType {
    // Walk up to ParameterList to handle chained calls
}

// For ARRAY context - uses element directly at offset
private fun resolveExpressionTypeFromArray(project: Project, controllerFile: PsiFile?): PhpType {
    // No walking - offset already points to exact value expression
}

// Dispatch based on VarKind
private fun resolveByHandle(...): PhpType {
    when (varHandle.sourceKind) {
        SourceKind.EXPRESSION -> {
            when (varKind) {
                VarKind.ARRAY -> resolveExpressionTypeFromArray(project, controllerFile)
                else -> resolveExpressionTypeFromPair(project, controllerFile)
            }
        }
        // ... other cases
    }
}
```

## Implementation Plan

### 1. Rename existing method
**File**: `ViewVariableIndexService.kt:284`

Rename `resolveExpressionType()` → `resolveExpressionTypeFromPair()`
- Keep all existing logic (walks up to ParameterList)
- Remove the ArrayCreationExpression check added earlier (not needed)

### 2. Create new method for array context
**File**: `ViewVariableIndexService.kt` (new method)

```kotlin
private fun resolveExpressionTypeFromArray(project: Project, controllerFile: PsiFile?): PhpType {
    if (controllerFile == null) {
        return createFallbackType()
    }

    // Find the PSI element at the offset
    val psiElementAtOffset = controllerFile.findElementAt(varHandle.offset)
    if (psiElementAtOffset == null) {
        return createFallbackType()
    }

    // For array values, offset points directly to the value expression
    // No need to walk up - just get type from this element or its immediate parent
    val element = psiElementAtOffset as? PhpTypedElement
        ?: psiElementAtOffset.parent as? PhpTypedElement
        ?: return createFallbackType()

    return element.type.global(project)
}
```

### 3. Update dispatch logic
**File**: `ViewVariableIndexService.kt:144-146`

Change:
```kotlin
SourceKind.EXPRESSION -> {
    resolveExpressionType(project, controllerFile)
}
```

To:
```kotlin
SourceKind.EXPRESSION -> {
    when (varKind) {
        VarKind.ARRAY -> resolveExpressionTypeFromArray(project, controllerFile)
        else -> resolveExpressionTypeFromPair(project, controllerFile)
    }
}
```

### 4. Update tests
**File**: `cake5/ViewVariableTest.kt`

Update expectations from `string[]`/`int[]` → `string`/`int`:
- Line 359: `assertEquals("string", presentation.typeText)` ✅ (already correct)
- Line 386-390: Update comment to reflect fix
- Line 411: `assertEquals("int", presentation.typeText)` (change from `int[]`)

### 5. Revert earlier change
**File**: `ViewVariableIndexService.kt:310-314`

Remove the ArrayCreationExpression boundary check that was added:
```kotlin
// Remove these lines:
if (current is com.jetbrains.php.lang.psi.elements.ArrayCreationExpression) {
    break
}
```

## Benefits

1. ✅ **Clear separation of concerns**: PAIR walks up, ARRAY doesn't
2. ✅ **No boolean parameters**: Method names indicate behavior
3. ✅ **Easier to understand**: Each method has single purpose
4. ✅ **Easier to maintain**: Context-specific logic is isolated
5. ✅ **Correct types**: Array values get base types without `[]` suffix

## Testing

Run cake5 ViewVariableTests - should see 21 tests passing with:
- `$singleVar` → `string` (was `string[]`)
- `$total` → `int` (was `int[]`)
- `$title` → `string` (unchanged)
- `$count` → `int` (unchanged)

## Future Considerations

If we find other contexts that need different walking behavior, this pattern makes it easy to add new methods like:
- `resolveExpressionTypeFromTuple()`
- `resolveExpressionTypeFromCompact()`

Each with its own appropriate boundary logic.
