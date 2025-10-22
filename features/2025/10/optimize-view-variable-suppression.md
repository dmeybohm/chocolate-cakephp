# Optimize View Variable Suppression - Implementation Plan

## Overview

Add a fast existence-check path for `UndefinedViewVariableInspectionSuppressor` that avoids expensive type resolution. The optimization uses a two-phase lookup strategy:
- **Phase 1 (Fast)**: Check static patterns without PSI
- **Phase 2 (Slow)**: Resolve dynamic patterns with PSI (but without type resolution)

## Current Performance Problem

The inspection suppressor currently does full type resolution for every undefined variable:

```kotlin
// Current flow:
ViewVariableIndexService.lookupVariableTypeFromViewPathInSmartReadAction()
  → Load index data (fast)
  → Load controller PSI file
  → Call rawVar.resolveType() for type resolution:
    - Find element at offset
    - Resolve variable assignments
    - Parse expressions
    - Follow property/method chains
    - Return PhpType
  → Check if resultType.types.size > 0
```

**Problem**: We only need to know **if the variable exists**, not **what type it is**.

Type resolution is expensive:
- PSI file loading
- AST traversal
- Expression parsing
- Type inference

## Pattern Categories

### Static Patterns (Variable names known at index time)

| Pattern | Example | Index Stores |
|---------|---------|--------------|
| PAIR | `$this->set('movie', $val)` | variableName: "movie" |
| ARRAY | `$this->set(['movie' => $val])` | variableName: "movie" |
| COMPACT | `$this->set(compact('movie'))` | variableName: "movie" |
| TUPLE | `$this->set(['movie', 'actors'], [...])` | variableName: "movie", "actors" |

**Lookup**: Direct map key check, no PSI needed ✅

### Dynamic Patterns (Variable names need resolution)

| Pattern | Example | Index Stores |
|---------|---------|--------------|
| VARIABLE_ARRAY | `$this->set($vars)` where `$vars = ['movie' => ...]` | variableName: "vars" (placeholder) |
| VARIABLE_COMPACT | `$this->set($vars)` where `$vars = compact('movie')` | variableName: "vars" (placeholder) |
| MIXED_TUPLE | `$this->set($keys, $vals)` | variableName: "keys_vals_mixed_tuple" |
| VARIABLE_PAIR | `$this->set($key, $val)` | variableName: "key" (placeholder) |

**Lookup**: Need PSI to find assignments and extract variable names ⚠️

## Two-Phase Lookup Architecture

### Phase 1: Fast Path (Static Patterns)

```kotlin
fun variableExistsInViewPath(
    project: Project,
    settings: Settings,
    filenameKey: String,
    variableName: String
): Boolean {
    val fileList = ViewFileIndexService.referencingElementsInSmartReadAction(project, filenameKey)
    val toProcess = fileList.toMutableList()
    val visited = mutableSetOf<String>()
    var maxLookups = 15

    while (toProcess.isNotEmpty()) {
        if (maxLookups == 0) break
        maxLookups -= 1

        val elementAndPath = toProcess.removeAt(0)
        visited.add(elementAndPath.path)

        if (elementAndPath.nameWithoutExtension.isAnyControllerClass()) {
            val controllerKey = controllerKeyFromElementAndPath(elementAndPath) ?: continue

            // Phase 1: Check static patterns (no PSI)
            // Phase 2: Resolve dynamic patterns (with PSI, no type resolution)
            if (variableExistsInController(project, controllerKey, variableName)) {
                return true
            }
            continue
        }

        // Handle view file references (traverse to find controllers)
        val containingFile = ReadAction.compute<PsiFile?, Nothing> {
            elementAndPath.psiElement?.containingFile
        } ?: continue

        val templatesDir = templatesDirectoryOfViewFile(project, settings, containingFile) ?: continue
        val newFilenameKey = ViewFileIndexService.canonicalizeFilenameToKey(
            templatesDir, settings, elementAndPath.path
        )
        val newFileList = ViewFileIndexService.referencingElementsInSmartReadAction(
            project, newFilenameKey
        )
        for (newPsiElementAndPath in newFileList) {
            if (visited.contains(newPsiElementAndPath.path)) continue
            toProcess.add(newPsiElementAndPath)
        }
    }

    return false
}
```

### Phase 2: Controller-Level Lookup

```kotlin
private fun variableExistsInController(
    project: Project,
    controllerKey: String,
    variableName: String
): Boolean {
    val fileIndex = FileBasedIndex.getInstance()
    val searchScope = GlobalSearchScope.allScope(project)
    val psiManager = PsiManager.getInstance(project)
    var found = false

    fileIndex.processValues(VIEW_VARIABLE_INDEX_KEY, controllerKey, null,
        { controllerVirtualFile, viewVariablesMap: ViewVariablesWithRawVars ->
            // Phase 1: Check static patterns (direct key lookup)
            if (viewVariablesMap.containsKey(variableName)) {
                found = true
                return@processValues false  // Stop processing
            }

            // Phase 2: Check dynamic patterns (need PSI)
            val dynamicEntries = viewVariablesMap.values.filter { rawVar ->
                rawVar.varKind in setOf(
                    VarKind.VARIABLE_ARRAY,
                    VarKind.VARIABLE_COMPACT,
                    VarKind.MIXED_TUPLE,
                    VarKind.VARIABLE_PAIR
                )
            }

            if (dynamicEntries.isEmpty()) {
                return@processValues true  // Continue to next controller
            }

            // Need PSI to resolve dynamic patterns
            val controllerPsiFile = psiManager.findFile(controllerVirtualFile)

            for (entry in dynamicEntries) {
                val variableNames = extractVariableNamesFromDynamicPattern(entry, controllerPsiFile)
                if (variableName in variableNames) {
                    found = true
                    return@processValues false  // Stop processing
                }
            }

            true  // Continue processing
        },
        searchScope
    )

    return found
}
```

## Dynamic Pattern Resolution

### Extract Variable Names (NOT Types!)

```kotlin
private fun extractVariableNamesFromDynamicPattern(
    rawVar: RawViewVar,
    controllerFile: PsiFile?
): Set<String> {
    if (controllerFile == null) return emptySet()

    return when (rawVar.varKind) {
        VarKind.VARIABLE_ARRAY -> extractVariableArrayNames(rawVar, controllerFile)
        VarKind.VARIABLE_COMPACT -> extractVariableCompactNames(rawVar, controllerFile)
        VarKind.MIXED_TUPLE -> extractMixedTupleNames(rawVar, controllerFile)
        VarKind.VARIABLE_PAIR -> extractVariablePairName(rawVar, controllerFile)
        else -> emptySet()
    }
}
```

### VARIABLE_ARRAY: `$this->set($variables)` where `$variables = ['movie' => ..., 'actors' => ...]`

```kotlin
private fun extractVariableArrayNames(
    rawVar: RawViewVar,
    controllerFile: PsiFile
): Set<String> {
    val psiElementAtOffset = controllerFile.findElementAt(rawVar.varHandle.offset) ?: return emptySet()
    val containingMethod = PsiTreeUtil.getParentOfType(psiElementAtOffset, Method::class.java) ?: return emptySet()

    // Find the last assignment to this variable before the $this->set() call
    val assignments = PsiTreeUtil.findChildrenOfType(containingMethod, AssignmentExpression::class.java)
    val relevantAssignment = assignments
        .filter { assignment ->
            val variable = assignment.variable
            variable is Variable &&
            variable.name == rawVar.varHandle.symbolName &&
            assignment.textRange.startOffset < rawVar.varHandle.offset
        }
        .maxByOrNull { it.textRange.startOffset }
        ?: return emptySet()

    // Extract keys from the array assignment
    // $variables = ['movie' => ..., 'actors' => ...]
    val value = relevantAssignment.value ?: return emptySet()

    if (value !is ArrayCreationExpression) return emptySet()

    val keys = mutableSetOf<String>()
    for (hashElement in value.hashElements) {
        val key = hashElement.key
        if (key is StringLiteralExpression) {
            keys.add(key.contents)
        }
    }

    return keys
}
```

### VARIABLE_COMPACT: `$this->set($vars)` where `$vars = compact('movie', 'actors')`

```kotlin
private fun extractVariableCompactNames(
    rawVar: RawViewVar,
    controllerFile: PsiFile
): Set<String> {
    val psiElementAtOffset = controllerFile.findElementAt(rawVar.varHandle.offset) ?: return emptySet()
    val containingMethod = PsiTreeUtil.getParentOfType(psiElementAtOffset, Method::class.java) ?: return emptySet()

    // Find assignment: $vars = compact('movie', 'actors')
    val assignments = PsiTreeUtil.findChildrenOfType(containingMethod, AssignmentExpression::class.java)
    val relevantAssignment = assignments
        .filter { assignment ->
            val variable = assignment.variable
            variable is Variable &&
            variable.name == rawVar.varHandle.symbolName &&
            assignment.textRange.startOffset < rawVar.varHandle.offset
        }
        .maxByOrNull { it.textRange.startOffset }
        ?: return emptySet()

    val value = relevantAssignment.value
    if (value !is FunctionReference || value.name != "compact") return emptySet()

    // Extract string parameters from compact()
    val keys = mutableSetOf<String>()
    for (param in value.parameters) {
        if (param is StringLiteralExpression) {
            keys.add(param.contents)
        }
    }

    return keys
}
```

### MIXED_TUPLE: `$this->set($keys, $vals)` where `$keys = ['movie', 'actors']`

```kotlin
private fun extractMixedTupleNames(
    rawVar: RawViewVar,
    controllerFile: PsiFile
): Set<String> {
    // Parse the symbolName which has format "keysVarName|valsVarName"
    val parts = rawVar.varHandle.symbolName.split("|")
    val keysVarName = parts.getOrNull(0)?.takeIf { it.isNotEmpty() }
    val valsVarName = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }

    if (keysVarName == null) return emptySet()

    val psiElementAtOffset = controllerFile.findElementAt(rawVar.varHandle.offset) ?: return emptySet()
    val containingMethod = PsiTreeUtil.getParentOfType(psiElementAtOffset, Method::class.java) ?: return emptySet()

    // Find assignment to keys variable: $keys = ['movie', 'actors']
    val assignments = PsiTreeUtil.findChildrenOfType(containingMethod, AssignmentExpression::class.java)
    val keysAssignment = assignments
        .filter { assignment ->
            val variable = assignment.variable
            variable is Variable &&
            variable.name == keysVarName &&
            assignment.textRange.startOffset < rawVar.varHandle.offset
        }
        .maxByOrNull { it.textRange.startOffset }
        ?: return emptySet()

    val value = keysAssignment.value
    if (value !is ArrayCreationExpression) return emptySet()

    // Extract string values from array (not keys, since it's a list)
    val keys = mutableSetOf<String>()
    for (element in value.children) {
        if (element is StringLiteralExpression) {
            keys.add(element.contents)
        } else if (element is PhpPsiElement) {
            // Handle array values (not hash elements)
            for (child in element.children) {
                if (child is StringLiteralExpression) {
                    keys.add(child.contents)
                }
            }
        }
    }

    return keys
}
```

### VARIABLE_PAIR: `$this->set($key, $val)` where `$key = 'movie'`

```kotlin
private fun extractVariablePairName(
    rawVar: RawViewVar,
    controllerFile: PsiFile
): Set<String> {
    val psiElementAtOffset = controllerFile.findElementAt(rawVar.varHandle.offset) ?: return emptySet()
    val containingMethod = PsiTreeUtil.getParentOfType(psiElementAtOffset, Method::class.java) ?: return emptySet()

    // Find assignment: $key = 'movie'
    val assignments = PsiTreeUtil.findChildrenOfType(containingMethod, AssignmentExpression::class.java)
    val relevantAssignment = assignments
        .filter { assignment ->
            val variable = assignment.variable
            variable is Variable &&
            variable.name == rawVar.varHandle.symbolName &&
            assignment.textRange.startOffset < rawVar.varHandle.offset
        }
        .maxByOrNull { it.textRange.startOffset }
        ?: return emptySet()

    // Check if value is a string literal
    val value = relevantAssignment.value
    if (value is StringLiteralExpression) {
        return setOf(value.contents)
    }

    // Could also be a parameter - check method params
    val parameters = containingMethod.parameters
    val matchingParam = parameters.firstOrNull { it.name == rawVar.varHandle.symbolName }

    // Can't determine the value from parameter at this point
    // Would need to analyze call sites (too expensive)
    return emptySet()
}
```

## Performance Characteristics

### Static Patterns (~70-80% of real-world usage?)

**Current:**
```
Index lookup → Load PSI → Resolve type → Check non-empty
~50-100ms
```

**Optimized:**
```
Index lookup → Check map key
~1-5ms
```

**Speedup: ~10-20x** ⚡

### Dynamic Patterns (~20-30% of real-world usage?)

**Current:**
```
Index lookup → Load PSI → Resolve type → Check non-empty
~50-100ms
```

**Optimized:**
```
Index lookup → Check map key (miss) → Load PSI → Extract keys → Check match
~20-50ms
```

**Speedup: ~2-3x** (still loads PSI, but simpler traversal than type resolution)

### Overall Expected Speedup

Assuming 75% static / 25% dynamic distribution:
- 75% of cases: 10-20x faster
- 25% of cases: 2-3x faster
- **Overall: ~8-12x faster on average**

## Integration Changes

### 1. Add to ViewVariableIndexService

```kotlin
// New method in ViewVariableIndexService
fun variableExistsInViewPath(
    project: Project,
    settings: Settings,
    filenameKey: String,
    variableName: String
): Boolean {
    // Implementation as specified above
}
```

### 2. Update ViewVariableCache

```kotlin
// In ViewVariableCache.isVariableDefined()
// Change the lookup function to use existence check instead of type check
return cache.isVariableDefined(psiFile, filenameKey, variable.name, phpTracker) {
    key, varName ->
    // Fast existence check - no type resolution!
    ViewVariableIndexService.variableExistsInViewPath(
        project,
        settings,
        key,
        varName
    )
}
```

### 3. UndefinedViewVariableInspectionSuppressor

No changes needed - it already uses the cache!

## Testing Strategy

### Unit Tests

Create tests for each dynamic pattern extraction:

```kotlin
class VariableExtractionTest : BasePlatformTestCase() {

    fun `test extract from VARIABLE_ARRAY`() {
        // $vars = ['movie' => $val, 'actors' => $val2]
        // $this->set($vars)
        // Should extract: ["movie", "actors"]
    }

    fun `test extract from VARIABLE_COMPACT`() {
        // $vars = compact('movie', 'actors')
        // $this->set($vars)
        // Should extract: ["movie", "actors"]
    }

    fun `test extract from MIXED_TUPLE`() {
        // $keys = ['movie', 'actors']
        // $this->set($keys, $vals)
        // Should extract: ["movie", "actors"]
    }

    fun `test extract from VARIABLE_PAIR`() {
        // $key = 'movie'
        // $this->set($key, $val)
        // Should extract: ["movie"]
    }
}
```

### Integration Tests

Update existing suppressor tests to verify optimization:

```kotlin
fun `test suppression works with static patterns`() {
    // Verify $this->set('movie', $val) works
}

fun `test suppression works with VARIABLE_ARRAY pattern`() {
    // Verify $this->set($vars) where $vars = [...] works
}

fun `test suppression works with VARIABLE_COMPACT pattern`() {
    // Verify $this->set($vars) where $vars = compact(...) works
}

fun `test suppression works with MIXED_TUPLE pattern`() {
    // Verify $this->set($keys, $vals) works
}
```

### Performance Tests

Add simple performance measurements:

```kotlin
fun `test suppression performance - static pattern`() {
    val start = System.currentTimeMillis()
    // Check 100 variables with static patterns
    val duration = System.currentTimeMillis() - start
    assertTrue("Should be fast", duration < 500) // <5ms per check
}

fun `test suppression performance - dynamic pattern`() {
    val start = System.currentTimeMillis()
    // Check 100 variables with dynamic patterns
    val duration = System.currentTimeMillis() - start
    assertTrue("Should be reasonably fast", duration < 3000) // <30ms per check
}
```

## Phased Implementation (Iterative)

Each phase implements support for specific VarKind patterns, allowing for:
- Independent testing and validation
- Incremental value delivery
- Easier debugging and rollback if needed
- Faster iteration cycles

---

## Phase 1: Infrastructure + Static Patterns

**Goal:** Build foundation and support static patterns (immediate ~10-20x speedup for 75% of cases)

**Patterns Supported:**
- ✅ `VarKind.PAIR` - `$this->set('movie', $val)`
- ✅ `VarKind.ARRAY` - `$this->set(['movie' => $val])`
- ✅ `VarKind.COMPACT` - `$this->set(compact('movie'))`
- ✅ `VarKind.TUPLE` - `$this->set(['movie', 'actors'], [...])`

**Implementation:**

1. Add `variableExistsInViewPath()` skeleton to ViewVariableIndexService:
```kotlin
fun variableExistsInViewPath(
    project: Project,
    settings: Settings,
    filenameKey: String,
    variableName: String
): Boolean {
    // Full implementation as in spec
    // Calls variableExistsInController()
}
```

2. Add `variableExistsInController()` with Phase 1 only:
```kotlin
private fun variableExistsInController(
    project: Project,
    controllerKey: String,
    variableName: String
): Boolean {
    val fileIndex = FileBasedIndex.getInstance()
    val searchScope = GlobalSearchScope.allScope(project)
    var found = false

    fileIndex.processValues(VIEW_VARIABLE_INDEX_KEY, controllerKey, null,
        { _, viewVariablesMap: ViewVariablesWithRawVars ->
            // Phase 1: Check static patterns (direct key lookup)
            if (viewVariablesMap.containsKey(variableName)) {
                found = true
                false  // Stop processing
            } else {
                true  // Continue
            }
            // NOTE: Dynamic patterns will return false for now
        },
        searchScope
    )

    return found
}
```

3. Update ViewVariableCache to use new method:
```kotlin
return cache.isVariableDefined(psiFile, filenameKey, variable.name, phpTracker) {
    key, varName ->
    ViewVariableIndexService.variableExistsInViewPath(project, settings, key, varName)
}
```

4. **Testing:**
   - Run existing tests - should all pass for static patterns
   - Add performance test to measure speedup
   - Verify cache works correctly

5. **Success Criteria:**
   - All existing tests pass
   - Static patterns 10-20x faster
   - No regressions in suppression behavior

**Deliverable:** Working optimization for 75% of use cases

---

## Phase 2: VARIABLE_ARRAY Support

**Goal:** Support `$this->set($vars)` where `$vars = ['movie' => ..., 'actors' => ...]`

**Pattern:** `VarKind.VARIABLE_ARRAY`

**Implementation:**

1. Add `extractVariableArrayNames()` helper:
```kotlin
private fun extractVariableArrayNames(
    rawVar: RawViewVar,
    controllerFile: PsiFile
): Set<String> {
    // Full implementation from spec
    // Find assignment: $vars = ['movie' => ..., 'actors' => ...]
    // Extract keys: ["movie", "actors"]
}
```

2. Add `extractVariableNamesFromDynamicPattern()` dispatcher:
```kotlin
private fun extractVariableNamesFromDynamicPattern(
    rawVar: RawViewVar,
    controllerFile: PsiFile?
): Set<String> {
    if (controllerFile == null) return emptySet()

    return when (rawVar.varKind) {
        VarKind.VARIABLE_ARRAY -> extractVariableArrayNames(rawVar, controllerFile)
        // Others return emptySet() for now
        else -> emptySet()
    }
}
```

3. Update `variableExistsInController()` to add Phase 2:
```kotlin
// After Phase 1 check:
if (!found) {
    // Phase 2: Check dynamic patterns
    val dynamicEntries = viewVariablesMap.values.filter { rawVar ->
        rawVar.varKind == VarKind.VARIABLE_ARRAY  // Only this one for now
    }

    if (dynamicEntries.isNotEmpty()) {
        val controllerPsiFile = psiManager.findFile(controllerVirtualFile)
        for (entry in dynamicEntries) {
            val variableNames = extractVariableNamesFromDynamicPattern(entry, controllerPsiFile)
            if (variableName in variableNames) {
                found = true
                break
            }
        }
    }
}
```

4. **Testing:**
   - Add test fixture with `$vars = ['movie' => ..., 'actors' => ...]` pattern
   - Test `extractVariableArrayNames()` directly
   - Test end-to-end suppression works
   - Verify performance (should be 2-3x faster than type resolution)

5. **Success Criteria:**
   - VARIABLE_ARRAY pattern detected and suppressed correctly
   - No regressions in Phase 1 patterns
   - Performance acceptable for this pattern

**Deliverable:** Support for most common dynamic pattern

---

## Phase 3: VARIABLE_COMPACT Support

**Goal:** Support `$this->set($vars)` where `$vars = compact('movie', 'actors')`

**Pattern:** `VarKind.VARIABLE_COMPACT`

**Implementation:**

1. Add `extractVariableCompactNames()` helper:
```kotlin
private fun extractVariableCompactNames(
    rawVar: RawViewVar,
    controllerFile: PsiFile
): Set<String> {
    // Full implementation from spec
    // Find assignment: $vars = compact('movie', 'actors')
    // Extract parameters: ["movie", "actors"]
}
```

2. Update `extractVariableNamesFromDynamicPattern()`:
```kotlin
return when (rawVar.varKind) {
    VarKind.VARIABLE_ARRAY -> extractVariableArrayNames(rawVar, controllerFile)
    VarKind.VARIABLE_COMPACT -> extractVariableCompactNames(rawVar, controllerFile)
    else -> emptySet()
}
```

3. Update `variableExistsInController()` filter:
```kotlin
val dynamicEntries = viewVariablesMap.values.filter { rawVar ->
    rawVar.varKind in setOf(VarKind.VARIABLE_ARRAY, VarKind.VARIABLE_COMPACT)
}
```

4. **Testing:**
   - Add test fixture with `$vars = compact('movie', 'actors')` pattern
   - Test `extractVariableCompactNames()` directly
   - Test end-to-end suppression works

5. **Success Criteria:**
   - VARIABLE_COMPACT pattern works correctly
   - No regressions in previous phases

**Deliverable:** Support for compact() pattern

---

## Phase 4: VARIABLE_PAIR Support

**Goal:** Support `$this->set($key, $val)` where `$key = 'movie'`

**Pattern:** `VarKind.VARIABLE_PAIR`

**Implementation:**

1. Add `extractVariablePairName()` helper:
```kotlin
private fun extractVariablePairName(
    rawVar: RawViewVar,
    controllerFile: PsiFile
): Set<String> {
    // Full implementation from spec
    // Find assignment: $key = 'movie'
    // Extract value: ["movie"]
}
```

2. Update `extractVariableNamesFromDynamicPattern()`:
```kotlin
return when (rawVar.varKind) {
    VarKind.VARIABLE_ARRAY -> extractVariableArrayNames(rawVar, controllerFile)
    VarKind.VARIABLE_COMPACT -> extractVariableCompactNames(rawVar, controllerFile)
    VarKind.VARIABLE_PAIR -> extractVariablePairName(rawVar, controllerFile)
    else -> emptySet()
}
```

3. Update `variableExistsInController()` filter:
```kotlin
val dynamicEntries = viewVariablesMap.values.filter { rawVar ->
    rawVar.varKind in setOf(
        VarKind.VARIABLE_ARRAY,
        VarKind.VARIABLE_COMPACT,
        VarKind.VARIABLE_PAIR
    )
}
```

4. **Testing:**
   - Add test fixture with `$key = 'movie'; $this->set($key, $val)` pattern
   - Test `extractVariablePairName()` directly
   - Test end-to-end suppression works

5. **Success Criteria:**
   - VARIABLE_PAIR pattern works correctly
   - No regressions

**Deliverable:** Support for variable key pattern

---

## Phase 5: MIXED_TUPLE Support

**Goal:** Support `$this->set($keys, $vals)` where `$keys = ['movie', 'actors']`

**Pattern:** `VarKind.MIXED_TUPLE`

**Implementation:**

1. Add `extractMixedTupleNames()` helper:
```kotlin
private fun extractMixedTupleNames(
    rawVar: RawViewVar,
    controllerFile: PsiFile
): Set<String> {
    // Full implementation from spec
    // Parse symbolName: "keysVarName|valsVarName"
    // Find assignment: $keys = ['movie', 'actors']
    // Extract values: ["movie", "actors"]
}
```

2. Update `extractVariableNamesFromDynamicPattern()`:
```kotlin
return when (rawVar.varKind) {
    VarKind.VARIABLE_ARRAY -> extractVariableArrayNames(rawVar, controllerFile)
    VarKind.VARIABLE_COMPACT -> extractVariableCompactNames(rawVar, controllerFile)
    VarKind.VARIABLE_PAIR -> extractVariablePairName(rawVar, controllerFile)
    VarKind.MIXED_TUPLE -> extractMixedTupleNames(rawVar, controllerFile)
    else -> emptySet()
}
```

3. Update `variableExistsInController()` filter:
```kotlin
val dynamicEntries = viewVariablesMap.values.filter { rawVar ->
    rawVar.varKind in setOf(
        VarKind.VARIABLE_ARRAY,
        VarKind.VARIABLE_COMPACT,
        VarKind.VARIABLE_PAIR,
        VarKind.MIXED_TUPLE
    )
}
```

4. **Testing:**
   - Add test fixture with `$keys = ['movie', 'actors']; $this->set($keys, $vals)` pattern
   - Test `extractMixedTupleNames()` directly
   - Test end-to-end suppression works

5. **Success Criteria:**
   - MIXED_TUPLE pattern works correctly
   - All dynamic patterns now supported
   - No regressions

**Deliverable:** Complete support for all dynamic patterns

---

## Phase 6: Performance Tuning & Optimization

**Goal:** Measure and optimize performance

**Activities:**

1. **Performance Testing:**
   - Create benchmark suite
   - Measure before/after for each pattern type
   - Profile hot paths

2. **Optimization:**
   - Cache extracted variable names per method (if beneficial)
   - Tune search depth limits
   - Add time limits for extraction

3. **Validation:**
   - Run full test suite
   - Test on real CakePHP projects
   - Gather user feedback

4. **Documentation:**
   - Document performance characteristics
   - Add comments explaining optimization
   - Update user-facing documentation if needed

**Success Criteria:**
- Meet performance targets (8-12x faster overall)
- All tests pass
- No user-reported regressions

---

## Testing Strategy Per Phase

Each phase follows the same testing pattern:

### Unit Tests
```kotlin
// Test the extraction helper directly
fun `test extract from [PATTERN_NAME]`() {
    // Setup: Create controller file with pattern
    // Execute: Call extraction helper
    // Verify: Check extracted variable names
}
```

### Integration Tests
```kotlin
// Test end-to-end suppression
fun `test suppression works with [PATTERN_NAME]`() {
    // Setup: Controller with pattern + view file with variable
    // Execute: Trigger inspection
    // Verify: Variable is suppressed (no warning)
}
```

### Regression Tests
```kotlin
// Run existing tests
fun `test no regressions from previous phases`() {
    // All previous pattern tests still pass
}
```

### Performance Tests
```kotlin
// Optional: measure performance
fun `test [PATTERN_NAME] performance`() {
    // Measure time for 100 lookups
    // Verify within acceptable range
}
```

---

## Phase Acceptance Criteria

Each phase must meet these criteria before moving to next:

✅ All new tests pass
✅ All existing tests still pass
✅ Code review completed
✅ Performance measured and acceptable
✅ Documentation updated
✅ Can be deployed independently

## Open Questions

1. **What's the real-world distribution of static vs dynamic patterns?**
   - Could profile this in actual CakePHP projects
   - Informs the value of this optimization

2. **Should we cache the extracted variable names from dynamic patterns?**
   - Once we extract names from `$vars = ['movie' => ...]`, cache it
   - Avoids re-extracting on subsequent lookups
   - Add another level of caching?

3. **How do we handle edge cases?**
   - Variable assigned multiple times in method
   - Variable from parent class
   - Variable from trait
   - Conditional assignments

4. **Should we add configuration for this optimization?**
   - Allow users to disable if it causes issues
   - Toggle between fast path (existence) and slow path (type resolution)

## Risks and Mitigation

### Risk: False negatives for complex dynamic patterns

**Example:**
```php
$vars = $this->getViewVars(); // Returns array from method
$this->set($vars);
```

**Mitigation:**
- Document limitations
- Fall back to returning `false` when extraction fails
- Better to have false positives (show warning) than false negatives (hide valid variables)

### Risk: PSI traversal complexity for dynamic patterns

**Example:** Deeply nested method calls, complex control flow

**Mitigation:**
- Set reasonable depth limits for assignment searching
- Time-box the extraction (max 50ms per variable?)
- Fall back to `false` if extraction times out

### Risk: Regression in suppression coverage

**Mitigation:**
- Comprehensive test suite
- Run existing tests to verify no regressions
- Add tests for all new dynamic pattern handling

## Success Metrics

- **Performance**: 5-10x faster for common case (static patterns)
- **Correctness**: No regressions in existing tests
- **Coverage**: Handle all documented dynamic patterns
- **User Experience**: Faster inspection feedback during code navigation

## Future Enhancements

1. **Cache extracted variable names from dynamic patterns**
   - Add per-method cache of resolved variable names
   - Invalidate when method changes

2. **Support more complex dynamic patterns**
   - Variables from method calls: `$vars = $this->getViewVars()`
   - Variables from parent methods
   - Variables merged from multiple sources

3. **Add telemetry**
   - Track static vs dynamic pattern usage
   - Measure actual performance improvements
   - Identify problematic patterns

4. **Incremental indexing for dynamic patterns**
   - Resolve some dynamic patterns at index time
   - Store resolved variable names in index
   - Reduce PSI loading at lookup time
