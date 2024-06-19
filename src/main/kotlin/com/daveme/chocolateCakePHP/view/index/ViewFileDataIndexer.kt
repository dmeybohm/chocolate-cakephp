package com.daveme.chocolateCakePHP.view.index

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.*
import com.daveme.chocolateCakePHP.pathRelativeToProject
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

val VIEW_FILE_INDEX_KEY : ID<ViewFileLocation, Void?> =
    ID.create("com.daveme.chocolateCakePHP.view.index.ViewFileIndex")

object ViewFileDataIndexer : DataIndexer<ViewFileLocation, Void?, FileContent> {
    override fun map(inputData: FileContent): MutableMap<ViewFileLocation, Void?> {
        val result = mutableMapOf<ViewFileLocation, Void?>()
        val psiFile = inputData.psiFile

        val renderMethods = PsiTreeUtil.findChildrenOfType(psiFile, MethodReference::class.java)
            .filter {
                it.name.equals("render", ignoreCase = true)
            }

        if (renderMethods.isEmpty()) {
            return result
        }

        val project = inputData.psiFile.project
        val settings = Settings.getInstance(project)
        if (!isCakeViewFile(project, settings, psiFile) &&
            !isCakeControllerFile(psiFile)
        ) {
            return result
        }
        val topSourceDirectory = topSourceDirectoryFromSourceFile(settings, psiFile)
            ?: return result
        val templatesDir = templatesDirectoryFromTopSourceDirectory(settings, topSourceDirectory)
            ?: return result
        val templatePath = pathRelativeToProject(project, templatesDir.psiDirectory)

        if (renderMethods.isNotEmpty()) {
            val withThis = renderMethods.filter { method ->
                val variable = method.firstChild as? Variable ?: return@filter false
                variable.name == "this" &&
                        method.parameters.isNotEmpty() &&
                        method.parameters.first() is StringLiteralExpression
            }
            for (method in withThis) {
                val parameterName = method.parameters.first() as StringLiteralExpression
                val content = parameterName.text
                val argToAppend = if (content.startsWith("/"))
                    content
                else
                    "/${content}"
                val fullPath = "${templatePath}${argToAppend}"
                val viewFilePathInfo = viewFilePathInfoFromPath(fullPath)
                    ?: continue
                val viewFileLocation = ViewFileLocation(
                    filename = viewFilePathInfo.viewFilename,
                    prefixPath = viewFilePathInfo.templateDirPath,
                    viewType = ViewFileLocation.ViewType.VIEW
                )
                result[viewFileLocation] = null
            }
        }
        return result
    }
}
