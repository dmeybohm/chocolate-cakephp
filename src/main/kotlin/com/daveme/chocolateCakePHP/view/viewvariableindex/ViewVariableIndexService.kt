package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.findElementAt
import com.daveme.chocolateCakePHP.removeFromEnd
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.lang.psi.elements.MethodReference

data class PsiElementAndPath(
    val path: String,
    val psiElement: PsiElement
)

object ViewVariableIndexService {
    fun canonicalizeFilenameToKey(filename: String, settings: Settings): String {
        return filename
            .removeFromEnd(settings.cakeTemplateExtension, ignoreCase = true)
            .removeFromEnd(".php", ignoreCase = true)
    }

    fun referencingElements(project: Project, filenameKey: String): List<PsiElementAndPath> {
        val result = mutableListOf<PsiElementAndPath>()
        val fileIndex = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)

        fileIndex.processValues(VIEW_FILE_INDEX_KEY, filenameKey, null,
            { indexedFile, offsets: List<Int>  ->
                offsets.forEach { offset ->
                    val element = indexedFile.findElementAt(project, offset)
                    val method = element?.parent?.parent as? MethodReference ?: return@forEach
                    result.add(PsiElementAndPath(indexedFile.path, method))
                }
                true
            }, searchScope)
        return result
    }

}