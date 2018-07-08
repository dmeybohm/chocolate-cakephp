package com.daveme.intellij.chocolateCakePHP.completion

import com.daveme.intellij.chocolateCakePHP.util.CakeUtil
import com.daveme.intellij.chocolateCakePHP.util.PsiUtil
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpExpression

class ControllerCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withParent(FieldReference::class.java), ControllerCompletionProvider())
        extend(CompletionType.SMART,
                PlatformPatterns.psiElement().withParent(FieldReference::class.java), ControllerCompletionProvider())
    }

    private class ControllerCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(completionParameters: CompletionParameters, processingContext: ProcessingContext, completionResultSet: CompletionResultSet) {
            val originalPosition = completionParameters.originalPosition ?: return
            val psiElement = originalPosition.originalElement
            if (psiElement == null) {
                println("null original element")
                return
            }
            val containingFile = psiElement.containingFile
            val appDir = PsiUtil.getAppDirectoryFromFile(containingFile) ?: return
            val controllerDir = appDir.findSubdirectory("Controller")
            var parent: PsiElement? = psiElement.parent
            if (parent !is FieldReference) {
                parent = findSiblingFieldReference(psiElement)
                if (parent == null) {
                    println("Couldn't find childFieldReference")
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
                CakeUtil.completeFromFilesInDir(completionResultSet, appDir, "Model")
                if (controllerDir != null) {
                    CakeUtil.completeFromFilesInDir(completionResultSet, controllerDir, "Component", "Component")
                }
            }
        }
    }

    companion object {

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
