package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import com.jetbrains.php.lang.psi.elements.Method

// Maps src/Controller/MovieController:methodName
//   or templates/Movie/view_file_without_extension
//   or templates/element/Movie/element_file_without_extension
typealias ViewVariablesKey = String

// The name of the variable
typealias ViewVariableName = String

// The data stored at each key in the index.
// For controllers, we store the startOffset at the offset inside the `$this->set()` call.
// For views, we offset of the lvalue in the first assignment statement that defines the var.
data class ViewVariableValue (
    val possiblyIncompleteType: String,
    val startOffset: Int
)

class ViewVariables : HashMap<ViewVariableName, ViewVariableValue>()

val VIEW_VARIABLE_INDEX_KEY : ID<ViewVariablesKey, ViewVariables> =
    ID.create("com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndex")


object ViewVariableIndexService {
    fun canonicalizeFilenameToKey(filename: String, settings: Settings): String {
        return filename
            .removeFromEnd(settings.cakeTemplateExtension, ignoreCase = true)
            .removeFromEnd(".php", ignoreCase = true)
    }

    fun referencingVariables(project: Project, filenameKey: String): ViewVariables {
        val result = ViewVariables()
        val fileIndex = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)

        val list = fileIndex.getValues(VIEW_VARIABLE_INDEX_KEY, filenameKey, searchScope)
        list.forEach { variables ->
            result += variables
        }
        return result
    }
}

fun isControllerFile(file: VirtualFile): Boolean {
    return file.nameWithoutExtension.endsWith("Controller")
}

fun controllerMethodKey(
    sourceFile: VirtualFile,
    methodCall: Method
): ViewVariablesKey {
    val controllerBaseName = sourceFile.nameWithoutExtension.controllerBaseName()
    return "${controllerBaseName}:${methodCall.name}"
}