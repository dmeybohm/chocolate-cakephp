package com.daveme.chocolateCakePHP.view.elementvariableindex

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.ControllerPath
import com.daveme.chocolateCakePHP.cake.templatesDirectoryFromViewFile
import com.daveme.chocolateCakePHP.view.viewfileindex.PsiElementAndPath
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.resolve.types.PhpType

// Maps MovieController:methodName
//   or {templates,src/Template,App/View}/Movie/view_file_without_extension
//   or {templates/element,src/Template/Element/Movie,app/View/Element}/element_file_without_extension
typealias ElementVariablesKey = String

// The name of the variable
typealias ElementVariableName = String

// The data stored at each key in the index.
// For elements, we offset of the lvalue in the first assignment statement that defines the var.
data class ElementVariableValue(
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

class ElementVariables : HashMap<ElementVariableName, ElementVariableValue>()

val ELEMENT_VARIABLE_INDEX_KEY: ID<ElementVariablesKey, ElementVariables> =
    ID.create("com.daveme.chocolateCakePHP.view.viewvariableindex.ElementVariableIndex")


object ElementVariableIndexService {

    fun controllerKeyFromElementAndPath(
        elementAndPath: PsiElementAndPath
    ): String? {
        val element = if (elementAndPath.psiElement is Method)
            elementAndPath.psiElement
        else
            PsiTreeUtil.getParentOfType(elementAndPath.psiElement, Method::class.java)
        if (element == null || !element.isValid) {
            return null
        }
        val controllerPath = elementAndPath.controllerPath ?: return null
        return controllerMethodKey(controllerPath, element.name)
    }

    fun lookupVariableTypeFromViewPath(
        project: Project,
        settings: Settings,
        filenameKey: String,
        variableName: String,
    ): PhpType {
        val fileList = ViewFileIndexService.referencingElements(project, filenameKey)
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
            val templatesDir = templatesDirectoryFromViewFile(project, settings, elementAndPath.psiElement.containingFile)
               ?: continue
            val newFilenameKey = ViewFileIndexService.canonicalizeFilenameToKey(
                templatesDir,
                settings,
                elementAndPath.path
            )
            val newFileList = ViewFileIndexService.referencingElements(
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

    fun lookupVariableTypeFromControllerKey(
        project: Project,
        controllerKey: String,
        variableName: String
    ): PhpType? {
        val fileIndex = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)

        val list = fileIndex.getValues(ELEMENT_VARIABLE_INDEX_KEY, controllerKey, searchScope)
        val vars = list.mapNotNull {
            it.getOrDefault(variableName, null)
        }
        if (vars.isEmpty()) {
            return null
        }
        val result = PhpType()
        vars.map {
            val types = it.phpType
            result.add(types)
        }
        return result
    }

    fun lookupVariablesFromControllerKey(
        project: Project,
        controllerKey: String,
    ): List<ElementVariables> {
        val fileIndex = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)

        return fileIndex.getValues(ELEMENT_VARIABLE_INDEX_KEY, controllerKey, searchScope)
    }

    fun lookupVariablesFromViewPath(
        project: Project,
        settings: Settings,
        filenameKey: String,
    ): ElementVariables {
        val fileList = ViewFileIndexService.referencingElements(project, filenameKey)
        val toProcess = fileList.toMutableList()
        val visited = mutableSetOf<String>() // paths
        val result = ElementVariables()
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
                variables.forEach { v -> result += v }
                continue
            }
            val templatesDir = templatesDirectoryFromViewFile(project, settings, elementAndPath.psiElement.containingFile)
                ?: continue
            val newFilenameKey = ViewFileIndexService.canonicalizeFilenameToKey(
                templatesDir,
                settings,
                elementAndPath.path
            )
            val newFileList = ViewFileIndexService.referencingElements(
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
): ElementVariablesKey {
    return "${controllerPath.prefix}:${controllerPath.name}:${methodName}"
}