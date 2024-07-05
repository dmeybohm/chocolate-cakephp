package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.view.viewfileindex.PsiElementAndPath
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
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

    fun controllerKeyFromRelativePath(
        relativePathWithViewFilename: String,
        cakeVersion: Int
    ): String? {
        val parts = relativePathWithViewFilename
            .replace('/', ':')
            .split(":")
            .toMutableList()
        if (parts.size < 2) {
            return null
        }
        parts[parts.size - 1] = parts[parts.size - 1].underscoreToCamelCaseViewFile(cakeVersion)
        return parts.joinToString(separator = ":")
    }

    fun viewKeyFromElementAndPath(
        elementAndPath: PsiElementAndPath
    ): String {
        return elementAndPath.path
    }

    fun lookupVariableTypeFromControllerKey(project: Project, controllerKey: String, variableName: String): PhpType? {
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

    fun variableIsSetByController(project: Project, filenameKey: String, variableName: String): Boolean {
        val fileIndex = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)

        val list = fileIndex.getValues(VIEW_VARIABLE_INDEX_KEY, filenameKey, searchScope)
        return list.any {
            it.contains(variableName)
        }
    }

    fun variableIsSetForView(project: Project, viewKey: String, name: @NlsSafe String): Boolean {
        // todo get the view paths for this view
        // todo find all the variables for each of the view paths, recursively and set some limit
        //      on the number of views to check
        return false
    }


}

fun controllerMethodKey(
    sourceFile: VirtualFile,
    methodCall: Method
): ViewVariablesKey {
    val controllerBaseName = sourceFile.nameWithoutExtension.controllerBaseName()
    return "${controllerBaseName}:${methodCall.name}"
}