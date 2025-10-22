package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.ControllerPath
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.view.viewfileindex.PsiElementAndPath
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
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

// Maps MovieController:methodName
//   or {templates,src/Template,App/View}/Movie/view_file_without_extension
//   or {templates/element,src/Template/Element/Movie,app/View/Element}/element_file_without_extension
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
    fun resolveType(project: Project, controllerFile: PsiFile? = null): PhpType {
        return when (varKind) {
            VarKind.PAIR -> resolvePairType(project, controllerFile)
            VarKind.ARRAY -> resolveArrayType(project, controllerFile)
            VarKind.COMPACT -> resolveCompactType(project, controllerFile)
            VarKind.TUPLE -> resolveTupleType(project, controllerFile)
            VarKind.VARIABLE_PAIR -> resolveVariablePairType(project, controllerFile)
            VarKind.VARIABLE_ARRAY -> resolveVariableArrayType(project, controllerFile)
            VarKind.VARIABLE_COMPACT -> resolveVariableCompactType(project, controllerFile)
            VarKind.MIXED_TUPLE -> resolveMixedTupleType(project, controllerFile)
        }
    }
    
    private fun resolvePairType(project: Project, controllerFile: PsiFile?): PhpType {
        // For PAIR: $this->set('name', $value)
        // Use varHandle to find $value and resolve its type
        return resolveByHandle(project, controllerFile, VarKind.PAIR)
    }

    private fun resolveArrayType(project: Project, controllerFile: PsiFile?): PhpType {
        // For ARRAY: $this->set(['name' => $value])
        // Use varHandle to find $value and resolve its type
        return resolveByHandle(project, controllerFile, VarKind.ARRAY)
    }

    private fun resolveCompactType(project: Project, controllerFile: PsiFile?): PhpType {
        // For COMPACT: $this->set(compact('varName'))
        // Use varHandle to find $varName and resolve its type
        return resolveByHandle(project, controllerFile, VarKind.COMPACT)
    }
    
    private fun resolveTupleType(project: Project, controllerFile: PsiFile?): PhpType {
        // TODO: Use PSI to resolve tuple assignment types
        return createFallbackType()
    }
    
    private fun resolveVariablePairType(project: Project, controllerFile: PsiFile?): PhpType {
        // TODO: Use PSI to resolve indirect variable assignment
        return createFallbackType()
    }
    
    private fun resolveVariableArrayType(project: Project, controllerFile: PsiFile?): PhpType {
        // TODO: Use PSI to resolve indirect array assignment
        return createFallbackType()
    }
    
    private fun resolveVariableCompactType(project: Project, controllerFile: PsiFile?): PhpType {
        // TODO: Use PSI to resolve indirect compact assignment
        return createFallbackType()
    }
    
    private fun resolveMixedTupleType(project: Project, controllerFile: PsiFile?): PhpType {
        // TODO: Use PSI to resolve mixed tuple assignment like $this->set($keysVar, $valsVar)
        // This requires finding assignments to both variables and pairing them up
        return createFallbackType()
    }
    
    // Central method that resolves types based on VarHandle information
    private fun resolveByHandle(project: Project, controllerFile: PsiFile?, varKind: VarKind): PhpType {
        return when (varHandle.sourceKind) {
            SourceKind.PARAM -> {
                // Parameters are resolved the same way as locals
                // resolveLocalVariableType checks both assignments and parameters
                resolveLocalVariableType(project, controllerFile)
            }
            SourceKind.LOCAL -> {
                resolveLocalVariableType(project, controllerFile)
            }
            SourceKind.PROPERTY -> {
                // TODO: Look for property access like $this->symbolName and get its type
                createFallbackType()
            }
            SourceKind.LITERAL -> {
                resolveLiteralType(project, controllerFile)
            }
            SourceKind.EXPRESSION -> {
                // Dispatch based on VarKind to handle different expression contexts
                when (varKind) {
                    VarKind.PAIR -> resolveExpressionTypeFromPair(project, controllerFile)
                    VarKind.ARRAY -> resolveExpressionTypeFromArray(project, controllerFile)
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

    private fun resolveLocalVariableType(project: Project, controllerFile: PsiFile?): PhpType {
        if (controllerFile == null) {
            return createFallbackType()
        }

        // Find the PSI element at the offset specified in varHandle
        val psiElementAtOffset = controllerFile.findElementAt(varHandle.offset)
        if (psiElementAtOffset == null) {
            return createFallbackType()
        }

        // Find the containing method to limit our search scope
        val containingMethod = PsiTreeUtil.getParentOfType(psiElementAtOffset, Method::class.java)
        if (containingMethod == null) {
            return createFallbackType()
        }

        // Strategy 1: Look for assignments to this variable within the method
        val assignments = PsiTreeUtil.findChildrenOfType(containingMethod, com.jetbrains.php.lang.psi.elements.AssignmentExpression::class.java)
        val relevantAssignments = assignments.filter { assignment ->
            val variable = assignment.variable
            variable is Variable && variable.name == varHandle.symbolName &&
            // Only consider assignments that come before our offset
            assignment.textRange.startOffset < varHandle.offset
        }

        // Use the last assignment before our offset (closest one)
        val lastAssignment = relevantAssignments.maxByOrNull { it.textRange.startOffset }
        if (lastAssignment != null) {
            val variable = lastAssignment.variable
            if (variable is com.jetbrains.php.lang.psi.elements.PhpTypedElement) {
                return variable.type.global(project)
            }
        }

        // Strategy 2: Check if it's a method parameter
        val parameters = containingMethod.parameters
        val matchingParam = parameters.firstOrNull { it.name == varHandle.symbolName }
        if (matchingParam != null) {
            // Get the type and clean up any namespace pollution for primitive types
            val paramType = matchingParam.type

            // PHP primitive types that should never have namespace prefixes
            val primitiveTypes = PRIMITIVE_TYPES

            // Create a new PhpType with cleaned type strings
            val cleanedType = PhpType()
            paramType.types.forEach { typeString ->
                // Check if this looks like a primitive with an incorrect namespace prefix
                val lastSegment = typeString.substringAfterLast('\\')
                val cleanedTypeString = if (lastSegment.lowercase() in primitiveTypes && typeString.contains('\\')) {
                    // This is a primitive type with a namespace prefix - strip it
                    lastSegment
                } else {
                    // Keep the full type name for classes/interfaces
                    typeString
                }
                cleanedType.add(cleanedTypeString)
            }

            return cleanedType
        }

        // Fallback: couldn't resolve
        return createFallbackType()
    }

    private fun resolveLiteralType(project: Project, controllerFile: PsiFile?): PhpType {
        if (controllerFile == null) {
            return createFallbackType()
        }

        // Find the PSI element at the offset (should be a literal value)
        val psiElementAtOffset = controllerFile.findElementAt(varHandle.offset)
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

    private fun resolveExpressionTypeFromPair(project: Project, controllerFile: PsiFile?): PhpType {
        if (controllerFile == null) {
            return createFallbackType()
        }

        // Find the PSI element at the offset
        val psiElementAtOffset = controllerFile.findElementAt(varHandle.offset)
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

    private fun resolveExpressionTypeFromArray(project: Project, controllerFile: PsiFile?): PhpType {
        if (controllerFile == null) {
            return createFallbackType()
        }

        // Find the PSI element at the offset
        val psiElementAtOffset = controllerFile.findElementAt(varHandle.offset)
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
    ID.create("com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndex.v4")


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

    fun lookupVariableTypeFromViewPathInSmartReadAction(
        project: Project,
        settings: Settings,
        filenameKey: String,
        variableName: String,
    ): PhpType {
        val fileList = ViewFileIndexService.referencingElementsInSmartReadAction(project, filenameKey)
        val toProcess = fileList.toMutableList()
        val visited = mutableSetOf<String>() // paths
        val result = PhpType()
        var maxLookups = 15

        while (toProcess.isNotEmpty()) {
            if (maxLookups == 0) {
                break
            }
            maxLookups -= 1
            val elementAndPath = toProcess.removeAt(0)
            visited.add(elementAndPath.path)
            if (elementAndPath.nameWithoutExtension.isAnyControllerClass()) {
                val controllerKey = controllerKeyFromElementAndPath(elementAndPath)
                    ?: continue
                val variableType = lookupVariableTypeFromControllerKey(project, controllerKey, variableName)
                    ?: continue
                result.add(variableType)
                continue
            }
            val containingFile = ReadAction.compute<com.intellij.psi.PsiFile?, Nothing> {
                elementAndPath.psiElement?.containingFile
            } ?: continue
            val templatesDir = templatesDirectoryOfViewFile(project, settings, containingFile)
               ?: continue
            val newFilenameKey = ViewFileIndexService.canonicalizeFilenameToKey(
                templatesDir,
                settings,
                elementAndPath.path
            )
            val newFileList = ViewFileIndexService.referencingElementsInSmartReadAction(
                project,
                newFilenameKey
            )
            for (newPsiElementAndPath in newFileList) {
                if (visited.contains(newPsiElementAndPath.path)) {
                    continue
                }
                toProcess.add(newPsiElementAndPath)
            }
        }

        return result
    }

    private fun lookupVariableTypeFromControllerKey(
        project: Project,
        controllerKey: String,
        variableName: String
    ): PhpType? {
        val fileIndex = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)
        val psiManager = com.intellij.psi.PsiManager.getInstance(project)
        val result = PhpType()

        // Use processValues to get access to the VirtualFile (controller file)
        fileIndex.processValues(VIEW_VARIABLE_INDEX_KEY, controllerKey, null,
            { controllerVirtualFile, viewVariablesMap: ViewVariablesWithRawVars ->
                val controllerPsiFile = psiManager.findFile(controllerVirtualFile)
                val rawVar: RawViewVar? = (viewVariablesMap as HashMap<ViewVariableName, RawViewVar>).get(variableName)
                if (rawVar != null) {
                    val types: PhpType = rawVar.resolveType(project, controllerPsiFile)
                    result.add(types)
                }
                true // continue processing
            },
            searchScope
        )

        return if (result.types.isEmpty()) null else result
    }

    private fun lookupVariablesFromControllerKey(
        project: Project,
        controllerKey: String,
    ): List<Pair<PsiFile?, ViewVariablesWithRawVars>> {
        val fileIndex = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)
        val psiManager = com.intellij.psi.PsiManager.getInstance(project)
        val result = mutableListOf<Pair<PsiFile?, ViewVariablesWithRawVars>>()

        fileIndex.processValues(VIEW_VARIABLE_INDEX_KEY, controllerKey, null,
            { controllerVirtualFile, viewVariablesMap: ViewVariablesWithRawVars ->
                val controllerPsiFile = psiManager.findFile(controllerVirtualFile)
                result.add(Pair(controllerPsiFile, viewVariablesMap))
                true // continue processing
            },
            searchScope
        )

        return result
    }

    fun lookupVariablesFromViewPathInSmartReadAction(
        project: Project,
        settings: Settings,
        filenameKey: String,
    ): ViewVariables {
        val fileList = ViewFileIndexService.referencingElementsInSmartReadAction(project, filenameKey)
        val toProcess = fileList.toMutableList()
        val visited = mutableSetOf<String>() // paths
        val result = ViewVariables()
        var maxLookups = 15

        while (toProcess.isNotEmpty()) {
            if (maxLookups == 0) {
                break
            }
            maxLookups -= 1
            val elementAndPath = toProcess.removeAt(0)
            visited.add(elementAndPath.path)
            if (elementAndPath.nameWithoutExtension.isAnyControllerClass()) {
                val controllerKey = controllerKeyFromElementAndPath(elementAndPath)
                    ?: continue
                val variables = lookupVariablesFromControllerKey(project, controllerKey)
                variables.forEach { (controllerPsiFile, rawVarCollection) ->
                    // Convert RawViewVar to ViewVariableValue for backward compatibility
                    rawVarCollection.forEach { (name, rawVar) ->
                        val resolvedType = rawVar.resolveType(project, controllerPsiFile)
                        result[name] = ViewVariableValue(resolvedType.toString(), rawVar.offset)
                    }
                }
                continue
            }
            val containingFile2 = ReadAction.compute<com.intellij.psi.PsiFile?, Nothing> {
                elementAndPath.psiElement?.containingFile
            } ?: continue
            val templatesDir = templatesDirectoryOfViewFile(project, settings, containingFile2)
                ?: continue
            val newFilenameKey = ViewFileIndexService.canonicalizeFilenameToKey(
                templatesDir,
                settings,
                elementAndPath.path
            )
            val newFileList = ViewFileIndexService.referencingElementsInSmartReadAction(
                project,
                newFilenameKey
            )
            for (newPsiElementAndPath in newFileList) {
                if (visited.contains(newPsiElementAndPath.path)) {
                    continue
                }
                toProcess.add(newPsiElementAndPath)
            }
        }
        return result
    }

    /**
     * Check if a variable exists in the view path without resolving its type.
     * This is faster than lookupVariableTypeFromViewPathInSmartReadAction as it avoids type resolution.
     *
     * Phase 1: Supports static patterns (PAIR, ARRAY, COMPACT, TUPLE) via direct map lookup.
     * Future phases will add support for dynamic patterns (VARIABLE_ARRAY, etc.).
     */
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

    /**
     * Extract variable names from dynamic pattern entries.
     * Dispatches to appropriate extraction method based on VarKind.
     *
     * Phase 2: Supports VARIABLE_ARRAY
     * Phase 3: Supports VARIABLE_COMPACT
     * Phase 4: Supports VARIABLE_PAIR
     * Future phases will add MIXED_TUPLE
     */
    private fun extractVariableNamesFromDynamicPattern(
        rawVar: RawViewVar,
        controllerFile: PsiFile?
    ): Set<String> {
        if (controllerFile == null) return emptySet()

        return when (rawVar.varKind) {
            VarKind.VARIABLE_ARRAY -> extractVariableArrayNames(rawVar, controllerFile)
            VarKind.VARIABLE_COMPACT -> extractVariableCompactNames(rawVar, controllerFile)
            VarKind.VARIABLE_PAIR -> extractVariablePairName(rawVar, controllerFile)
            // Future phases will add other patterns here
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

        val value = relevantAssignment.value ?: return emptySet()

        // Check if this is actually a compact() call (indexer doesn't distinguish yet)
        if (value is FunctionReference && value.name == "compact") {
            return extractVariableCompactNames(rawVar, controllerFile)
        }

        // Check if this is actually a string literal (VARIABLE_PAIR pattern)
        if (value is StringLiteralExpression) {
            return extractVariablePairName(rawVar, controllerFile)
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
        // Note: We can't determine the value from parameter at this point
        // as it would need to analyze call sites (too expensive)
        return emptySet()
    }

    /**
     * Check if a variable exists in a specific controller without resolving its type.
     *
     * Phase 1: Checks static patterns via direct map key lookup (no PSI loading).
     * Phase 2: Checks VARIABLE_ARRAY dynamic pattern (with PSI loading).
     * Phase 3: Checks VARIABLE_COMPACT dynamic pattern (with PSI loading).
     * Phase 4: Checks VARIABLE_PAIR dynamic pattern (with PSI loading).
     * Future phases will add other dynamic patterns.
     */
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
                // Phase 1: Check static patterns (direct key lookup - no PSI needed)
                if (viewVariablesMap.containsKey(variableName)) {
                    found = true
                    return@processValues false  // Stop processing
                }

                // Phase 2-4: Check dynamic patterns (need PSI)
                val dynamicEntries = viewVariablesMap.values.filter { rawVar ->
                    rawVar.varKind in setOf(
                        VarKind.VARIABLE_ARRAY,
                        VarKind.VARIABLE_COMPACT,
                        VarKind.VARIABLE_PAIR
                    )
                }

                if (dynamicEntries.isNotEmpty()) {
                    val controllerPsiFile = psiManager.findFile(controllerVirtualFile)
                    for (entry in dynamicEntries) {
                        val variableNames = extractVariableNamesFromDynamicPattern(entry, controllerPsiFile)
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