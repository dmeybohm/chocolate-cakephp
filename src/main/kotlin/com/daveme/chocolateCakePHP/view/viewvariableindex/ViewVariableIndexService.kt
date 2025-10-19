package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.ControllerPath
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.view.viewfileindex.PsiElementAndPath
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import com.jetbrains.php.lang.psi.elements.Method
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
    CALL,       // comes from a function/method call result
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
        return resolveByHandle(project, controllerFile)
    }
    
    private fun resolveArrayType(project: Project, controllerFile: PsiFile?): PhpType {
        // For ARRAY: $this->set(['name' => $value])  
        // Use varHandle to find $value and resolve its type
        return resolveByHandle(project, controllerFile)
    }
    
    private fun resolveCompactType(project: Project, controllerFile: PsiFile?): PhpType {
        // For COMPACT: $this->set(compact('varName'))
        // Use varHandle to find $varName and resolve its type
        return resolveByHandle(project, controllerFile)
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
    private fun resolveByHandle(project: Project, controllerFile: PsiFile?): PhpType {
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
                // TODO: Parse literal value and return appropriate type (string, int, etc.)
                createFallbackType()
            }
            SourceKind.CALL -> {
                // TODO: Analyze function/method call and get return type
                createFallbackType()
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
            val primitiveTypes = setOf("int", "float", "string", "bool", "array", "object",
                                       "callable", "iterable", "void", "mixed", "null",
                                       "integer", "boolean", "double")

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