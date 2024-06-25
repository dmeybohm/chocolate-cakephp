package com.daveme.chocolateCakePHP.view.index

import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
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
        val project = psiFile.project
        val projectDir = project.guessProjectDir() ?: return result

        val virtualFile = psiFile.virtualFile
        if (virtualFile.nameWithoutExtension.endsWith("Test")) {
            return result
        }

        val methods = PsiTreeUtil.findChildrenOfType(psiFile, MethodReference::class.java)
        val renderCalls = methods
            .filter {
                it.name.equals("render", ignoreCase = true)
            }
        val elementCalls = methods
            .filter {
                it.name.equals("element", ignoreCase = true)
            }

        if (elementCalls.isEmpty() && renderCalls.isEmpty()) {
            return result
        }

        indexRenderCalls(result, projectDir, renderCalls, virtualFile)
        indexElementCalls(result, projectDir, elementCalls, virtualFile)
        return result
    }

    private fun indexRenderCalls(
        result: MutableMap<String, List<Int>>,
        projectDir: VirtualFile,
        renderCalls: List<MethodReference>,
        virtualFile: VirtualFile
    ) {
        val withThis = renderCalls.filter { method ->
            val variable = method.firstChild as? Variable ?: return@filter false
            variable.name == "this" &&
                    method.parameters.isNotEmpty() &&
                    method.parameters.first() is StringLiteralExpression
        }
        if (withThis.isEmpty()) {
            return
        }

        val viewPathPrefix = viewPathPrefixFromSourceFile(projectDir, virtualFile)
            ?: return

        for (method in withThis) {
            val parameterName = method.parameters.first() as StringLiteralExpression
            val content = RenderPath(parameterName.text)

            if (content.quotesRemoved.isEmpty()) {
                continue
            }
            val fullViewPath = fullViewPathFromPrefixAndRenderPath(
                viewPathPrefix,
                content
            )
            if (result.containsKey(fullViewPath)) {
                val oldList = result[fullViewPath]!!.toMutableList()
                val newList = oldList + listOf(method.textOffset)
                result[fullViewPath] = newList
            } else {
                result[fullViewPath] = listOf(method.textOffset)
            }
        }
    }

    private fun indexElementCalls(
        result: MutableMap<String, List<Int>>,
        projectDir: VirtualFile,
        elementCalls: List<MethodReference>,
        virtualFile: VirtualFile
    ) {
        val withThis = elementCalls.filter { method ->
            val variable = method.firstChild as? Variable ?: return@filter false
            variable.name == "this" &&
                    method.parameters.isNotEmpty() &&
                    method.parameters.first() is StringLiteralExpression
        }
        if (withThis.isEmpty()) {
            return
        }

        val viewPathPrefix = elementPathPrefixFromSourceFile(projectDir, virtualFile)
            ?: return

        for (method in withThis) {
            val parameterName = method.parameters.first() as StringLiteralExpression
            val content = RenderPath(parameterName.text)

            if (content.quotesRemoved.isEmpty()) {
                continue
            }
            val fullViewPath = fullViewPathFromPrefixAndRenderPath(
                viewPathPrefix,
                content
            )
            if (result.containsKey(fullViewPath)) {
                val oldList = result[fullViewPath]!!.toMutableList()
                val newList = oldList + listOf(method.textOffset)
                result[fullViewPath] = newList
            } else {
                result[fullViewPath] = listOf(method.textOffset)
            }
        }
    }

}
