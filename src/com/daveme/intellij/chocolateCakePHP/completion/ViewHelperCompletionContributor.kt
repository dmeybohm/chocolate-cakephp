package com.daveme.intellij.chocolateCakePHP.completion

import com.daveme.intellij.chocolateCakePHP.cake.isCakeTemplate
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.FieldReference

class ViewHelperCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withParent(FieldReference::class.java), ViewHelperCompletionProvider())
        extend(CompletionType.SMART,
                PlatformPatterns.psiElement().withParent(FieldReference::class.java), ViewHelperCompletionProvider())
    }

    private class ViewHelperCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
                completionParameters: CompletionParameters,
                processingContext: ProcessingContext,
                completionResultSet: CompletionResultSet
        ) {
            val psiElement = completionParameters.position
            val containingFile = psiElement.containingFile

            val parent = (psiElement.parent ?: return) as? FieldReference ?: return
            if (!isCakeTemplate(containingFile.name)) {
                return
            }
            val classReference = parent.classReference ?: return
            if (classReference.text == "\$this") {
                completeFromSubclasses(completionResultSet, psiElement.project, "\\AppHelper", "Helper")
            }
        }
    }

}