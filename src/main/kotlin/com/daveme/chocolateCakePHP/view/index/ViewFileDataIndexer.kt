package com.daveme.chocolateCakePHP.view.index

import com.daveme.chocolateCakePHP.Settings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

object ViewFileDataIndexer : DataIndexer<ViewFileLocation, Void?, FileContent> {
    override fun map(inputData: FileContent): MutableMap<ViewFileLocation, Void?> {
        val psiFile = inputData.psiFile
        val result = mutableMapOf<ViewFileLocation, Void?>()

        val renderMethods = PsiTreeUtil.findChildrenOfType(psiFile, MethodReference::class.java)
            .filter {
                it.name.equals("render", ignoreCase = true)
            }

        if (renderMethods.isNotEmpty()) {
            val withThis = renderMethods.filter { method ->
                val variable = method.firstChild as? Variable ?: return@filter false
                variable.name == "this" &&
                        method.parameters.size == 1 &&
                        method.parameters.first() is StringLiteralExpression
            }
            for (method in withThis) {
                val parameterName = method.parameters.first() as StringLiteralExpression
                val viewFileLocation = ViewFileLocation(
                    filename = parameterName.text,
                    prefixPath = "",
                    viewType = ViewFileLocation.ViewType.VIEW
                )
                result[viewFileLocation] = null
            }
        }
        return result
    }
}

val KEY : ID<ViewFileLocation, Void?> =
    ID.create("com.daveme.chocolateCakePHP.view.index.ViewFileIndex")