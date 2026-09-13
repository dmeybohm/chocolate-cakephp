package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.ControllerPath
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.view.viewfileindex.PsiElementAndPath
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.resolve.types.PhpType

// One of three key kinds, see controllerMethodKey(), elementDataKey() and viewSetKey():
//   Movie:index                                  vars set by a controller action
//   element-data:element/Director/filmography    data arrays passed to that element by $this->element() calls
//   view-set:Movie/film_director                 $this->set() calls made inside that view file
// The view path part is the ViewFileIndex canonical key (relative to the templates dir, no extension).
typealias ViewVariablesKey = String

// The name of the variable
typealias ViewVariableName = String

// The different kinds of $this->set() assignments we can detect syntactically
enum class VarKind {
    PAIR,           // $this->set('name', $value)
    ARRAY,          // $this->set(['name' => $value])  
    COMPACT,        // $this->set(compact('value'))
    TUPLE,          // $this->set(['name1', 'name2'], [$val1, $val2])
    VARIABLE_PAIR,  // $this->set($var, $value) 
    VARIABLE_ARRAY, // $this->set($arrayVar) where $arrayVar = ['key' => 'val']
    VARIABLE_COMPACT, // $this->set($compactVar) where $compactVar = compact('var')
    MIXED_TUPLE     // $this->set($keysVar, $valsVar) where either param could be variable or array
}

// The different sources where the value side of $this->set() comes from
enum class SourceKind {
    PARAM,      // comes from a method parameter
    LOCAL,      // comes from a local variable assigned earlier in the method
    PROPERTY,   // comes from an object property reference like $this->foo
    LITERAL,    // comes from a literal value like 'string' or 123
    EXPRESSION, // comes from any typed expression (method calls, property access, variables, etc.)
    MIXED_ASSIGNMENT, // comes from mixed tuple assignment requiring further resolution
    UNKNOWN     // couldn't determine syntactically
}

// Handle that describes both the syntax form and value source for type resolution
data class VarHandle(
    val sourceKind: SourceKind,
    val symbolName: String,      // The symbol to resolve (e.g. "foo" for $foo)
    val offset: Int              // Location for navigation
)

// Lightweight, resolve-free DTO for index storage with embedded type resolution capability
// Contains only syntax-level facts, no type resolution during indexing
data class RawViewVar(
    val variableName: String,
    val varKind: VarKind,
    val offset: Int,
    val varHandle: VarHandle // Describes where the value comes from for type resolution
) {
    // Type resolution happens ONLY when needed, with full PSI context
    fun resolveType(project: Project, sourceFile: PsiFile? = null): PhpType {
        return when (varKind) {
            VarKind.PAIR -> resolvePairType(project, sourceFile)
            VarKind.ARRAY -> resolveArrayType(project, sourceFile)
            VarKind.COMPACT -> resolveCompactType(project, sourceFile)
            VarKind.TUPLE -> resolveTupleType(project, sourceFile)
            VarKind.VARIABLE_PAIR -> resolveVariablePairType(project, sourceFile)
            VarKind.VARIABLE_ARRAY -> resolveVariableArrayType(project, sourceFile)
            VarKind.VARIABLE_COMPACT -> resolveVariableCompactType(project, sourceFile)
            VarKind.MIXED_TUPLE -> resolveMixedTupleType(project, sourceFile)
        }
    }
    
    private fun resolvePairType(project: Project, sourceFile: PsiFile?): PhpType {
        // For PAIR: $this->set('name', $value)
        // Use varHandle to find $value and resolve its type
        return resolveByHandle(project, sourceFile, VarKind.PAIR)
    }

    private fun resolveArrayType(project: Project, sourceFile: PsiFile?): PhpType {
        // For ARRAY: $this->set(['name' => $value])
        // Use varHandle to find $value and resolve its type
        return resolveByHandle(project, sourceFile, VarKind.ARRAY)
    }

    private fun resolveCompactType(project: Project, sourceFile: PsiFile?): PhpType {
        // For COMPACT: $this->set(compact('varName'))
        // Use varHandle to find $varName and resolve its type
        return resolveByHandle(project, sourceFile, VarKind.COMPACT)
    }
    
    private fun resolveTupleType(project: Project, sourceFile: PsiFile?): PhpType {
        // TODO: Use PSI to resolve tuple assignment types
        return createFallbackType()
    }
    
    private fun resolveVariablePairType(project: Project, sourceFile: PsiFile?): PhpType {
        // TODO: Use PSI to resolve indirect variable assignment
        return createFallbackType()
    }
    
    private fun resolveVariableArrayType(project: Project, sourceFile: PsiFile?): PhpType {
        // TODO: Use PSI to resolve indirect array assignment
        return createFallbackType()
    }
    
    private fun resolveVariableCompactType(project: Project, sourceFile: PsiFile?): PhpType {
        // TODO: Use PSI to resolve indirect compact assignment
        return createFallbackType()
    }
    
    private fun resolveMixedTupleType(project: Project, sourceFile: PsiFile?): PhpType {
        // TODO: Use PSI to resolve mixed tuple assignment like $this->set($keysVar, $valsVar)
        // This requires finding assignments to both variables and pairing them up
        return createFallbackType()
    }
    
    // Central method that resolves types based on VarHandle information
    private fun resolveByHandle(project: Project, sourceFile: PsiFile?, varKind: VarKind): PhpType {
        return when (varHandle.sourceKind) {
            SourceKind.PARAM -> {
                // Parameters are resolved the same way as locals
                // resolveLocalVariableType checks both assignments and parameters
                resolveLocalVariableType(project, sourceFile)
            }
            SourceKind.LOCAL -> {
                resolveLocalVariableType(project, sourceFile)
            }
            SourceKind.PROPERTY -> {
                // TODO: Look for property access like $this->symbolName and get its type
                createFallbackType()
            }
            SourceKind.LITERAL -> {
                resolveLiteralType(project, sourceFile)
            }
            SourceKind.EXPRESSION -> {
                // Dispatch based on VarKind to handle different expression contexts
                when (varKind) {
                    VarKind.PAIR -> resolveExpressionTypeFromPair(project, sourceFile)
                    VarKind.ARRAY -> resolveExpressionTypeFromArray(project, sourceFile)
                    else -> createFallbackType() // For COMPACT, TUPLE, or other unsupported types
                }
            }
            SourceKind.MIXED_ASSIGNMENT -> {
                // TODO: Resolve mixed tuple assignment by finding variable assignments
                createFallbackType()
            }
            SourceKind.UNKNOWN -> createFallbackType()
        }
    }

    /**
     * Type of the local `$symbolName` whose use sits at the handle offset. The source may be a
     * controller method or a view file, so scope is "nearest function, else the file".
     *
     * Strategies, in order:
     *   1. the last plain assignment to the variable before the use, in the same scope;
     *   2. a parameter of the enclosing function with that name;
     *   3. the variable itself, typed by PhpStorm's inference. In a view file this re-enters
     *      ViewVariableTypeProvider, so a template `$movie` that the controller provided (or a
     *      `@var` docblock declares) carries its type into an element it is passed to;
     *   4. for a view file whose scope never mentions the variable (a `compact('name')` of a
     *      view var), the view file's own variable lookup.
     */
    private fun resolveLocalVariableType(project: Project, sourceFile: PsiFile?): PhpType {
        if (sourceFile == null) {
            return createFallbackType()
        }
        val symbolName = varHandle.symbolName
        val offset = varHandle.offset

        val assignment = lastAssignmentBefore(sourceFile, symbolName, offset)
        if (assignment != null) {
            val variable = assignment.variable
            if (variable is PhpTypedElement) {
                return variable.type.global(project)
            }
        }

        val parameter = scopeParameter(sourceFile, symbolName, offset)
        if (parameter != null) {
            return stripPrimitiveNamespaces(parameter.type)
        }

        val variable = scopeVariable(sourceFile, symbolName, offset)
        if (variable != null) {
            val inferred = variable.type.global(project)
            if (hasConcreteType(inferred)) {
                return inferred
            }
        }

        val viewVarType = lookupAsViewVariableOfFile(project, sourceFile, symbolName)
        if (viewVarType != null && hasConcreteType(viewVarType)) {
            return viewVarType
        }

        return createFallbackType()
    }

    /** Parameter types can carry a namespace prefix on primitives; strip it. */
    private fun stripPrimitiveNamespaces(paramType: PhpType): PhpType {
        val cleanedType = PhpType()
        paramType.types.forEach { typeString ->
            val lastSegment = typeString.substringAfterLast('\\')
            val cleanedTypeString = if (lastSegment.lowercase() in PRIMITIVE_TYPES && typeString.contains('\\')) {
                lastSegment
            } else {
                typeString
            }
            cleanedType.add(cleanedTypeString)
        }
        return cleanedType
    }

    private fun hasConcreteType(type: PhpType): Boolean =
        type.types.any { it.removePrefix("\\").lowercase() != "mixed" }

    /** When [sourceFile] is a view file, the type its own view variable lookup gives [symbolName]. */
    private fun lookupAsViewVariableOfFile(project: Project, sourceFile: PsiFile, symbolName: String): PhpType? {
        val settings = Settings.getInstance(project)
        val templatesDir = templatesDirectoryOfViewFile(project, settings, sourceFile) ?: return null
        val path = sourceFile.originalFile.virtualFile?.path ?: return null
        val key = ViewFileIndexService.canonicalizeFilenameToKey(templatesDir, settings, path)
        return ViewVariableIndexService.lookupVariableTypeFromViewPathInSmartReadAction(
            project, settings, key, symbolName
        )
    }

    private fun resolveLiteralType(project: Project, sourceFile: PsiFile?): PhpType {
        if (sourceFile == null) {
            return createFallbackType()
        }

        // Find the PSI element at the offset (should be a literal value)
        val psiElementAtOffset = sourceFile.findElementAt(varHandle.offset)
        if (psiElementAtOffset == null) {
            return createFallbackType()
        }

        // Navigate up to find the actual literal expression
        // The offset might point to the content of a string literal, so we need to find the parent
        var current: PsiElement? = psiElementAtOffset
        while (current != null) {
            when (current) {
                is com.jetbrains.php.lang.psi.elements.StringLiteralExpression -> {
                    val result = PhpType()
                    result.add("string")
                    return result
                }
                is com.jetbrains.php.lang.psi.elements.PhpExpression -> {
                    // Check if it's a numeric literal by looking at the text
                    val text = current.text.trim()
                    when {
                        text == "true" || text == "false" -> {
                            val result = PhpType()
                            result.add("bool")
                            return result
                        }
                        text == "null" -> {
                            val result = PhpType()
                            result.add("null")
                            return result
                        }
                        text.toIntOrNull() != null -> {
                            val result = PhpType()
                            result.add("int")
                            return result
                        }
                        text.toDoubleOrNull() != null -> {
                            val result = PhpType()
                            result.add("float")
                            return result
                        }
                    }
                }
            }

            // Don't walk up too far - stop at statement level
            if (current is com.jetbrains.php.lang.psi.elements.Statement) {
                break
            }
            current = current.parent
        }

        // Couldn't determine literal type
        return createFallbackType()
    }

    private fun resolveExpressionTypeFromPair(project: Project, sourceFile: PsiFile?): PhpType {
        if (sourceFile == null) {
            return createFallbackType()
        }

        // Find the PSI element at the offset
        val psiElementAtOffset = sourceFile.findElementAt(varHandle.offset)
        if (psiElementAtOffset == null) {
            return createFallbackType()
        }

        // For PAIR syntax expressions (method calls, property access, variables, etc.),
        // we need to find the complete expression by walking up the tree until we hit a
        // ParameterList boundary.
        // Example: $this->set('name', $this->getTable()->get('Movies'))
        // This walks up from inner parts of chained calls to find the complete expression.

        var expressionElement: PsiElement? = psiElementAtOffset
        var current = psiElementAtOffset.parent

        while (current != null) {
            // Stop if we hit a parameter list - we've reached the boundary
            if (current is ParameterList) {
                break
            }
            expressionElement = current
            current = current.parent
        }

        // Get type from any PhpTypedElement (covers all expression types)
        if (expressionElement is PhpTypedElement) {
            return expressionElement.type.global(project)
        }

        // Couldn't resolve the expression type
        return createFallbackType()
    }

    private fun resolveExpressionTypeFromArray(project: Project, sourceFile: PsiFile?): PhpType {
        if (sourceFile == null) {
            return createFallbackType()
        }

        // Find the PSI element at the offset
        val psiElementAtOffset = sourceFile.findElementAt(varHandle.offset)
        if (psiElementAtOffset == null) {
            return createFallbackType()
        }

        // For ARRAY syntax, the offset points to an expression inside structural wrappers
        // Structure: ArrayCreationExpression → Hash array element → Array value → (wrapper?) → Expression
        // Example: $this->set(['name' => $this->property])
        // We need to walk up but stop at the great-grandchild of ArrayCreationExpression

        // Walk up the tree to find the expression, stopping when we're 3 levels below ArrayCreationExpression
        var expressionElement: PsiElement? = psiElementAtOffset
        var current = psiElementAtOffset.parent

        while (current != null) {
            // Check if current's grandparent is the ArrayCreationExpression boundary
            val grandparent = current.parent?.parent
            if (grandparent is com.jetbrains.php.lang.psi.elements.ArrayCreationExpression) {
                // Current is 2 levels down from ArrayCreationExpression
                // expressionElement is the great-grandchild - this is what we want!
                break
            }

            // Also stop if we hit the boundary directly (safety check)
            if (current is com.jetbrains.php.lang.psi.elements.ArrayCreationExpression) {
                break
            }

            expressionElement = current
            current = current.parent
        }

        // Get type from the expression element (should be the actual value, not a wrapper)
        if (expressionElement is PhpTypedElement) {
            return expressionElement.type.global(project)
        }

        return createFallbackType()
    }

    private fun createFallbackType(): PhpType {
        val fallbackType = PhpType()
        fallbackType.add("mixed")
        return fallbackType
    }
}

// The data stored at each key in the index.
// For controllers, we store the startOffset at the offset inside the `$this->set()` call.
// For views, we offset of the lvalue in the first assignment statement that defines the var.
data class ViewVariableValue(
    val possiblyIncompleteType: String,
    val startOffset: Int
) {
    val phpType: PhpType
        get() {
            val result = PhpType()
            possiblyIncompleteType.split("|").forEach {
                result.add(it)
            }
            return result
        }
}

class ViewVariables : HashMap<ViewVariableName, ViewVariableValue>()

// New version using RawViewVar for direct mapping with embedded type resolution
class ViewVariablesWithRawVars : HashMap<ViewVariableName, RawViewVar>()

val VIEW_VARIABLE_INDEX_KEY: ID<ViewVariablesKey, ViewVariablesWithRawVars> =
    ID.create("com.daveme.chocolateCakePHP.viewvariableindex.v4")


/**
 * One index key that can contribute variables to a view file. See [ViewVariablesKey] for the
 * three key shapes.
 */
sealed class ViewVariableSource(val key: ViewVariablesKey) {
    /** Data arrays passed to the element being looked up by `$this->element()` calls. */
    class ElementCallData(key: ViewVariablesKey) : ViewVariableSource(key)

    /** `$this->set()` calls inside the file being looked up or one of the views that render it. */
    class ViewSet(key: ViewVariablesKey) : ViewVariableSource(key)

    /** A controller action reached by walking render / element references backwards. */
    class ControllerAction(key: ViewVariablesKey) : ViewVariableSource(key)
}

object ViewVariableIndexService {

    private fun controllerKeyFromElementAndPath(
        elementAndPath: PsiElementAndPath
    ): String? {
        val psiElement = elementAndPath.psiElement ?: return null
        val element = if (psiElement is Method)
            psiElement
        else
            PsiTreeUtil.getParentOfType(psiElement, Method::class.java)
        if (element == null || !element.isValid) {
            return null
        }
        val controllerPath = elementAndPath.controllerPath ?: return null
        return controllerMethodKey(controllerPath, element.name)
    }

    /**
     * Visit every index key that can contribute variables to the view file [filenameKey], most
     * specific first:
     *
     *   1. ElementCallData(filenameKey) — data passed directly to this element. Only for the
     *      original key: CakePHP hands `$data` to the named element alone (it renders with
     *      `array_merge($this->viewVars, $data)`), so it does not flow into nested elements.
     *   2. ViewSet(filenameKey) — `$this->set()` inside the file itself.
     *   3. Walking the ViewFileIndex backwards (bounded breadth-first search): ViewSet(ancestor)
     *      for every template or element that renders this one, because `viewVars` is shared
     *      downwards, and ControllerAction(key) for every controller action reached.
     *
     * [process] returns false to stop early.
     */
    private fun forEachContributingSource(
        project: Project,
        settings: Settings,
        filenameKey: String,
        process: (ViewVariableSource) -> Boolean
    ) {
        if (!process(ViewVariableSource.ElementCallData(elementDataKey(filenameKey)))) return
        if (!process(ViewVariableSource.ViewSet(viewSetKey(filenameKey)))) return

        val toProcess = ViewFileIndexService.referencingElementsInSmartReadAction(project, filenameKey)
            .toMutableList()
        val visited = mutableSetOf<String>() // file paths
        val emittedViewKeys = mutableSetOf(filenameKey)
        var maxLookups = 15

        while (toProcess.isNotEmpty()) {
            if (maxLookups == 0) {
                break
            }
            maxLookups -= 1
            val elementAndPath = toProcess.removeAt(0)
            visited.add(elementAndPath.path)

            if (elementAndPath.nameWithoutExtension.isAnyControllerClass()) {
                val controllerKey = controllerKeyFromElementAndPath(elementAndPath) ?: continue
                if (!process(ViewVariableSource.ControllerAction(controllerKey))) return
                continue
            }

            // A template or element that renders the current file: it contributes its own
            // set() calls, and whatever renders it contributes in turn
            val containingFile = ReadAction.compute<PsiFile?, Nothing> {
                elementAndPath.psiElement?.containingFile
            } ?: continue
            val templatesDir = templatesDirectoryOfViewFile(project, settings, containingFile) ?: continue
            val ancestorKey = ViewFileIndexService.canonicalizeFilenameToKey(
                templatesDir,
                settings,
                elementAndPath.path
            )
            if (emittedViewKeys.add(ancestorKey)) {
                if (!process(ViewVariableSource.ViewSet(viewSetKey(ancestorKey)))) return
            }
            for (next in ViewFileIndexService.referencingElementsInSmartReadAction(project, ancestorKey)) {
                if (!visited.contains(next.path)) {
                    toProcess.add(next)
                }
            }
        }
    }

    fun lookupVariableTypeFromViewPathInSmartReadAction(
        project: Project,
        settings: Settings,
        filenameKey: String,
        variableName: String,
    ): PhpType {
        // Resolving a passed value may re-enter here for the calling template (see
        // RawViewVar.resolveLocalVariableType). Two elements passing each other's variables
        // would otherwise recurse without end; the inner frame yields no type instead.
        return RecursionManager.doPreventingRecursion(Pair(filenameKey, variableName), false) {
            val result = PhpType()
            forEachContributingSource(project, settings, filenameKey) { source ->
                lookupVariableTypeByKey(project, source.key, variableName)?.let { result.add(it) }
                true
            }
            result
        } ?: PhpType()
    }

    private fun lookupVariableTypeByKey(
        project: Project,
        key: ViewVariablesKey,
        variableName: String
    ): PhpType? {
        val fileIndex = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)
        val psiManager = PsiManager.getInstance(project)
        val result = PhpType()

        // processValues hands back the indexed file: the controller, or for element data and
        // view set() entries the template that made the call. That file is where the value
        // expression lives, so it is the source for type resolution.
        fileIndex.processValues(VIEW_VARIABLE_INDEX_KEY, key, null,
            { indexedFile, viewVariablesMap: ViewVariablesWithRawVars ->
                val rawVar: RawViewVar? = viewVariablesMap[variableName]
                if (rawVar != null) {
                    val sourcePsiFile = psiManager.findFile(indexedFile)
                    result.add(rawVar.resolveType(project, sourcePsiFile))
                }
                true // continue processing
            },
            searchScope
        )

        return if (result.types.isEmpty()) null else result
    }

    private fun lookupRawVarsByKey(
        project: Project,
        key: ViewVariablesKey,
    ): List<Pair<PsiFile?, ViewVariablesWithRawVars>> {
        val fileIndex = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)
        val psiManager = PsiManager.getInstance(project)
        val result = mutableListOf<Pair<PsiFile?, ViewVariablesWithRawVars>>()

        fileIndex.processValues(VIEW_VARIABLE_INDEX_KEY, key, null,
            { indexedFile, viewVariablesMap: ViewVariablesWithRawVars ->
                result.add(Pair(psiManager.findFile(indexedFile), viewVariablesMap))
                true // continue processing
            },
            searchScope
        )

        return result
    }

    /**
     * Every variable available in the view file [filenameKey], with its resolved type.
     *
     * Layered the way CakePHP merges them: controller vars first, then `set()` calls in the
     * views that render this file (farthest first), then `set()` in the file itself, then data
     * passed in the element call, so a later layer overrides an earlier one of the same name.
     */
    fun lookupVariablesFromViewPathInSmartReadAction(
        project: Project,
        settings: Settings,
        filenameKey: String,
    ): ViewVariables {
        val fromControllers = ViewVariables()
        val fromViewSets = mutableListOf<ViewVariables>() // in visiting order: this file first
        val fromElementData = ViewVariables()

        forEachContributingSource(project, settings, filenameKey) { source ->
            val target = when (source) {
                is ViewVariableSource.ControllerAction -> fromControllers
                is ViewVariableSource.ViewSet -> ViewVariables().also { fromViewSets.add(it) }
                is ViewVariableSource.ElementCallData -> fromElementData
            }
            lookupRawVarsByKey(project, source.key).forEach { (sourcePsiFile, rawVarCollection) ->
                rawVarCollection.forEach { (name, rawVar) ->
                    val resolvedType = rawVar.resolveType(project, sourcePsiFile)
                    target[name] = ViewVariableValue(resolvedType.toString(), rawVar.offset)
                }
            }
            true
        }

        val result = ViewVariables()
        result.putAll(fromControllers)
        fromViewSets.asReversed().forEach { result.putAll(it) }
        result.putAll(fromElementData)
        return result
    }

    /**
     * Check if a variable exists in the view path without resolving its type.
     * This is faster than lookupVariableTypeFromViewPathInSmartReadAction as it avoids type resolution.
     */
    fun variableExistsInViewPath(
        project: Project,
        settings: Settings,
        filenameKey: String,
        variableName: String
    ): Boolean {
        var found = false
        forEachContributingSource(project, settings, filenameKey) { source ->
            if (variableExistsByKey(project, source.key, variableName)) {
                found = true
            }
            !found
        }
        return found
    }

    /**
     * Extract variable names from dynamic pattern entries.
     * Dispatches to appropriate extraction method based on VarKind.
     *
     * Phase 2: Supports VARIABLE_ARRAY
     * Phase 3: Supports VARIABLE_COMPACT
     * Phase 4: Supports VARIABLE_PAIR
     * Phase 5: Supports MIXED_TUPLE
     */
    private fun extractVariableNamesFromDynamicPattern(
        rawVar: RawViewVar,
        sourceFile: PsiFile?
    ): Set<String> {
        if (sourceFile == null) return emptySet()

        return when (rawVar.varKind) {
            VarKind.VARIABLE_ARRAY -> extractVariableArrayNames(rawVar, sourceFile)
            VarKind.VARIABLE_COMPACT -> extractVariableCompactNames(rawVar, sourceFile)
            VarKind.VARIABLE_PAIR -> extractVariablePairName(rawVar, sourceFile)
            VarKind.MIXED_TUPLE -> extractMixedTupleName(rawVar, sourceFile)
            else -> emptySet()
        }
    }

    /**
     * Extract variable names from VARIABLE_ARRAY pattern.
     * Example: $vars = ['movie' => ..., 'actors' => ...]; $this->set($vars);
     * Returns: ["movie", "actors"]
     *
     * Note: Currently the indexer marks VARIABLE_ARRAY, VARIABLE_COMPACT, and VARIABLE_PAIR as VARIABLE_ARRAY,
     * so this function checks the assignment value type and delegates appropriately.
     */
    private fun extractVariableArrayNames(
        rawVar: RawViewVar,
        sourceFile: PsiFile
    ): Set<String> {
        // Find the last assignment to this variable before the $this->set() call
        val relevantAssignment = lastAssignmentBefore(sourceFile, rawVar.varHandle.symbolName, rawVar.varHandle.offset)
            ?: return emptySet()

        val value = relevantAssignment.value ?: return emptySet()

        // Check if this is actually a compact() call (indexer doesn't distinguish yet)
        if (value is FunctionReference && value.name == "compact") {
            return extractVariableCompactNames(rawVar, sourceFile)
        }

        // Check if this is actually a string literal (VARIABLE_PAIR pattern)
        if (value is StringLiteralExpression) {
            return extractVariablePairName(rawVar, sourceFile)
        }

        // Extract keys from the array assignment: $vars = ['movie' => ..., 'actors' => ...]
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

    /**
     * Extract variable names from VARIABLE_COMPACT pattern.
     * Example: $vars = compact('movie', 'actors'); $this->set($vars);
     * Returns: ["movie", "actors"]
     */
    private fun extractVariableCompactNames(
        rawVar: RawViewVar,
        sourceFile: PsiFile
    ): Set<String> {
        // Find assignment: $vars = compact('movie', 'actors')
        val relevantAssignment = lastAssignmentBefore(sourceFile, rawVar.varHandle.symbolName, rawVar.varHandle.offset)
            ?: return emptySet()

        val value = relevantAssignment.value
        if (value !is FunctionReference || value.name != "compact") return emptySet()

        // Extract string parameters from compact()
        val keys = mutableSetOf<String>()
        val parameterList = value.parameterList ?: return emptySet()
        for (param in parameterList.parameters) {
            if (param is StringLiteralExpression) {
                keys.add(param.contents)
            }
        }

        return keys
    }

    /**
     * Extract variable names from VARIABLE_PAIR pattern.
     * Example: $key = 'movie'; $this->set($key, $val);
     * Returns: ["movie"]
     */
    private fun extractVariablePairName(
        rawVar: RawViewVar,
        sourceFile: PsiFile
    ): Set<String> {
        // Find assignment: $key = 'movie'
        val relevantAssignment = lastAssignmentBefore(sourceFile, rawVar.varHandle.symbolName, rawVar.varHandle.offset)
            ?: return emptySet()

        // Check if value is a string literal
        val value = relevantAssignment.value
        if (value is StringLiteralExpression) {
            return setOf(value.contents)
        }

        // Could also be a parameter - check method params
        // Note: We can't determine the value from parameter at this point
        // as it would need to analyze call sites (too expensive)
        return emptySet()
    }

    /**
     * Extract variable names from MIXED_TUPLE pattern.
     * Example: $key = 'studio'; $val = 'Warner Bros'; $this->set($key, $val);
     * Returns: ["studio"]
     *
     * The symbolName format is "keyVarName|valVarName" (e.g., "key|val")
     * We need to find the assignment to the key variable to get the actual variable name.
     */
    private fun extractMixedTupleName(
        rawVar: RawViewVar,
        sourceFile: PsiFile
    ): Set<String> {
        // Parse the symbolName to get both variable names
        val parts = rawVar.varHandle.symbolName.split("|")
        if (parts.size != 2) return emptySet()

        val keyVariableName = parts[0]
        if (keyVariableName.isEmpty()) return emptySet()

        // Find assignment: $key = 'studio'
        val relevantAssignment = lastAssignmentBefore(sourceFile, keyVariableName, rawVar.varHandle.offset)
            ?: return emptySet()

        // Check if value is a string literal
        val value = relevantAssignment.value
        if (value is StringLiteralExpression) {
            return setOf(value.contents)
        }

        return emptySet()
    }

    /**
     * Check if a variable exists under one index key without resolving its type.
     *
     * Static patterns (PAIR, ARRAY, COMPACT, TUPLE) are a direct map lookup with no PSI
     * loading; dynamic patterns (VARIABLE_ARRAY, VARIABLE_COMPACT, VARIABLE_PAIR, MIXED_TUPLE)
     * need the source file's PSI to find the assignment that names the variables.
     */
    private fun variableExistsByKey(
        project: Project,
        key: ViewVariablesKey,
        variableName: String
    ): Boolean {
        val fileIndex = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)
        val psiManager = PsiManager.getInstance(project)
        var found = false

        fileIndex.processValues(VIEW_VARIABLE_INDEX_KEY, key, null,
            { indexedFile, viewVariablesMap: ViewVariablesWithRawVars ->
                // Phase 1: Check static patterns (direct key lookup - no PSI needed)
                if (viewVariablesMap.containsKey(variableName)) {
                    found = true
                    return@processValues false  // Stop processing
                }

                // Phase 2-5: Check dynamic patterns (need PSI)
                val dynamicEntries = viewVariablesMap.values.filter { rawVar ->
                    rawVar.varKind in setOf(
                        VarKind.VARIABLE_ARRAY,
                        VarKind.VARIABLE_COMPACT,
                        VarKind.VARIABLE_PAIR,
                        VarKind.MIXED_TUPLE
                    )
                }

                if (dynamicEntries.isNotEmpty()) {
                    val sourcePsiFile = psiManager.findFile(indexedFile)
                    for (entry in dynamicEntries) {
                        val variableNames = extractVariableNamesFromDynamicPattern(entry, sourcePsiFile)
                        if (variableName in variableNames) {
                            found = true
                            return@processValues false  // Stop processing
                        }
                    }
                }

                true  // Continue processing
            },
            searchScope
        )

        return found
    }

}

fun controllerMethodKey(
    controllerPath: ControllerPath,
    methodName: String
): ViewVariablesKey {
    return if (controllerPath.prefix.isEmpty()) {
        "${controllerPath.name}:${methodName}"
    } else {
        "${controllerPath.prefix}:${controllerPath.name}:${methodName}"
    }
}

private const val ELEMENT_DATA_KEY_PREFIX = "element-data:"
private const val VIEW_SET_KEY_PREFIX = "view-set:"

/** Key under which the data arrays passed to an element (its ViewFileIndex key) are indexed. */
fun elementDataKey(viewKey: String): ViewVariablesKey = ELEMENT_DATA_KEY_PREFIX + viewKey

/** Key under which the `$this->set()` calls made inside a view file (its ViewFileIndex key) are indexed. */
fun viewSetKey(viewKey: String): ViewVariablesKey = VIEW_SET_KEY_PREFIX + viewKey
