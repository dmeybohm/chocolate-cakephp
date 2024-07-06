package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.templatesDirectoryFromViewFile
import com.daveme.chocolateCakePHP.view.viewfileindex.PsiElementAndPath
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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

// The data stored at each key in the index.
// For controllers, we store the startOffset at the offset inside the `$this->set()` call.
// For views, we offset of the lvalue in the first assignment statement that defines the var.
data class ViewVariableValue(
    val possiblyIncompleteType: String,
    val startOffset: Int
)

class ViewVariables : HashMap<ViewVariableName, ViewVariableValue>()

val VIEW_VARIABLE_INDEX_KEY: ID<ViewVariablesKey, ViewVariables> =
    ID.create("com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndex")


object ViewVariableIndexService {

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
        return "${elementAndPath.nameWithoutExtension.controllerBaseName()}:${element.name}"
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

        val list = fileIndex.getValues(VIEW_VARIABLE_INDEX_KEY, controllerKey, searchScope)
        val vars = list.mapNotNull {
            it.getOrDefault(variableName, null)
        }
        if (vars.isEmpty()) {
            return null
        }
        val result = PhpType()
        vars.map {
            val types = it.possiblyIncompleteType.split('|')
            for (type in types) {
                result.add(type)
            }
        }
        return result
    }

}

fun controllerMethodKey(
    sourceFile: VirtualFile,
    methodCall: Method
): ViewVariablesKey {
    val controllerBaseName = sourceFile.nameWithoutExtension.controllerBaseName()
    return "${controllerBaseName}:${methodCall.name}"
}