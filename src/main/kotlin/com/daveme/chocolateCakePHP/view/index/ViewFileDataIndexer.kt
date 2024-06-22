package com.daveme.chocolateCakePHP.view.index

import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

object ViewFileDataIndexer : DataIndexer<String, List<Int>, FileContent> {

    override fun map(inputData: FileContent): MutableMap<String, List<Int>> {
        val result = mutableMapOf<String, List<Int>>()
        val psiFile = inputData.psiFile

        val renderMethods = PsiTreeUtil.findChildrenOfType(psiFile, MethodReference::class.java)
            .filter {
                it.name.equals("render", ignoreCase = true)
            }

        if (renderMethods.isEmpty()) {
            return result
        }

        val withThis = renderMethods.filter { method ->
            val variable = method.firstChild as? Variable ?: return@filter false
            variable.name == "this" &&
                    method.parameters.isNotEmpty() &&
                    method.parameters.first() is StringLiteralExpression
        }
        if (withThis.isEmpty()) {
            return result
        }

        val project = inputData.psiFile.project
        val virtualFile = psiFile.virtualFile
        if (virtualFile.nameWithoutExtension.endsWith("Test")) {
            return result
        }

        val projectDir = project.guessProjectDir()
            ?: return result
        val viewPathPrefix = viewPathPrefixFromSourceFile(projectDir, virtualFile)

        for (method in withThis) {
            val parameterName = method.parameters.first() as StringLiteralExpression
            val content = parameterName.text

            val fullViewPath = fullViewPathFromPrefixAndRenderPath(
                viewPathPrefix,
                RenderPath(content)
            )
            if (result.containsKey(fullViewPath)) {
                val oldList = result[fullViewPath]!!.toMutableList()
                val newList = oldList + listOf(method.textOffset)
                result[fullViewPath] = newList
            } else {
                result[fullViewPath] = listOf(method.textOffset)
            }
        }
        return result
    }

}
