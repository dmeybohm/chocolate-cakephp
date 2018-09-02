package com.daveme.intellij.chocolateCakePHP.completion

import com.daveme.intellij.chocolateCakePHP.cake.appDirectoryFromFile
import com.daveme.intellij.chocolateCakePHP.psi.AddValueToPropertyInsertHandler
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
            val appDir = appDirectoryFromFile(containingFile) ?: return
            val controllerDir = appDir.findSubdirectory("Controller")
            var parent: PsiElement? = psiElement.parent
            if (parent !is FieldReference) {
                parent = findSiblingFieldReference(psiElement)
                if (parent == null) {
                    return
                }
            }
            val fieldReference = parent as FieldReference
            val classReference = fieldReference.classReference ?: return
            var hasController = false
            for (type in classReference.type.types) {
                if (type.contains("Controller")) {
                    hasController = true
                }
            }
            if (hasController) {
                completeFromFilesInDir(completionResultSet, appDir, "Model", usesHandler)
                if (controllerDir != null) {
                    completeFromFilesInDir(
                        completionResultSet,
                        controllerDir,
                        "Component",
                        componentsHandler,
                        "Component"
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
            for (child in prevSibling.children) {
                if (child is FieldReference) {
                    return child
                }
            }
            return null
        }
    }

}
