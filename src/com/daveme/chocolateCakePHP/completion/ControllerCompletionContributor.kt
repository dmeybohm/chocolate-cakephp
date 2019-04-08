package com.daveme.chocolateCakePHP.completion

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.appDirectoryFromFile
import com.daveme.chocolateCakePHP.psi.AddValueToPropertyInsertHandler
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.FieldReference

class ControllerCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(FieldReference::class.java),
            ControllerCompletionProvider()
        )
        extend(
            CompletionType.SMART,
            PlatformPatterns.psiElement().withParent(FieldReference::class.java),
            ControllerCompletionProvider()
        )
    }

    private class ControllerCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            completionParameters: CompletionParameters,
            processingContext: ProcessingContext,
            completionResultSet: CompletionResultSet
        ) {
            val originalPosition = completionParameters.originalPosition ?: return
            val psiElement = originalPosition.originalElement ?: return

            val containingFile = psiElement.containingFile
            val settings = Settings.getInstance(psiElement.project)
            val appDir = appDirectoryFromFile(settings, containingFile) ?: return
            val controllerDir = appDir.findSubdirectory("Controller")

            var parent  = psiElement.parent ?: return
            if (parent !is FieldReference) {
                parent = findSiblingFieldReference(psiElement) ?: return
            }

            val fieldReference = parent as FieldReference
            val classReference = fieldReference.classReference ?: return

            val hasController = classReference.type.types.any { it.contains("Controller") }
            if (hasController) {
                completeFromFilesInDir(completionResultSet, appDir, subDir = "Model", insertHandler = usesHandler)

                if (controllerDir != null) {
                    completeFromFilesInDir(
                        completionResultSet,
                        controllerDir,
                        subDir = "Component",
                        replaceName = "Component",
                        insertHandler = componentsHandler
                    )
                }
            }
        }

    }

    companion object {
        private val usesHandler = AddValueToPropertyInsertHandler("uses")
        private val componentsHandler = AddValueToPropertyInsertHandler("components")

        private fun findSiblingFieldReference(element: PsiElement): PsiElement? {
            val prevSibling = element.prevSibling ?: return null
            return prevSibling.children.find { it is FieldReference }
        }
    }

}
