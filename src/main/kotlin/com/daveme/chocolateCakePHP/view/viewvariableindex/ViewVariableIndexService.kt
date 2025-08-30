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
    VARIABLE_COMPACT // $this->set($compactVar) where $compactVar = compact('var')
}

// Lightweight, resolve-free DTO for index storage with embedded type resolution capability
// Contains only syntax-level facts, no type resolution during indexing
data class RawViewVar(
    val variableName: String,
    val varKind: VarKind,
    val offset: Int,
    val rawTokenText: String? = null // For later resolution if needed
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
        }
    }
    
    private fun resolvePairType(project: Project, controllerFile: PsiFile?): PhpType {
        // TODO: Use PSI to find the variable and resolve its type
        return createFallbackType()
    }
    
    private fun resolveArrayType(project: Project, controllerFile: PsiFile?): PhpType {
        // TODO: Use PSI to parse the array value and resolve its type
        return createFallbackType()
    }
    
    private fun resolveCompactType(project: Project, controllerFile: PsiFile?): PhpType {
        // TODO: Use PSI to find the compacted variable and resolve its type
        return createFallbackType()
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
    ID.create("com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndex.v3")


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

        val list = fileIndex.getValues(VIEW_VARIABLE_INDEX_KEY, controllerKey, searchScope)
        val vars = list.mapNotNull {
            it.getOrDefault(variableName, null)
        }
        if (vars.isEmpty()) {
            return null
        }
        val result = PhpType()
        vars.forEach { rawVar ->
            val types = rawVar.resolveType(project)
            result.add(types)
        }
        return result
    }

    private fun lookupVariablesFromControllerKey(
        project: Project,
        controllerKey: String,
    ): List<ViewVariablesWithRawVars> {
        val fileIndex = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)

        return fileIndex.getValues(VIEW_VARIABLE_INDEX_KEY, controllerKey, searchScope)
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
                variables.forEach { rawVarCollection ->
                    // Convert RawViewVar to ViewVariableValue for backward compatibility
                    rawVarCollection.forEach { (name, rawVar) ->
                        val resolvedType = rawVar.resolveType(project)
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
    return "${controllerPath.prefix}:${controllerPath.name}:${methodName}"
}