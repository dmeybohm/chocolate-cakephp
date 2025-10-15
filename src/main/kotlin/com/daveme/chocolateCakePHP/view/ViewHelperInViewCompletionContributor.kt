package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.isCakeViewFile
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference

class ViewHelperInViewCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(FieldReference::class.java),
            ViewHelperCompletionProvider()
        )
        extend(
            CompletionType.SMART,
            PlatformPatterns.psiElement().withParent(FieldReference::class.java),
            ViewHelperCompletionProvider()
        )
    }

    private class ViewHelperCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            completionParameters: CompletionParameters,
            processingContext: ProcessingContext,
            completionResultSet: CompletionResultSet
        ) {
            val psiElement = completionParameters.position
            val settings = Settings.getInstance(psiElement.project)
            if (!settings.enabled) {
                return
            }

            val parent = PsiTreeUtil.getParentOfType(
                completionParameters.position,
                FieldReference::class.java
            ) ?: return
            val classReference = parent.classReference ?: return
            if (!classReference.textMatches("\$this")) {
                return
            }
            val containingFile = psiElement.containingFile
            if (!isCakeViewFile(psiElement.project, settings, containingFile)) {
                return
            }
            val phpIndex = PhpIndex.getInstance(psiElement.project)
            val viewHelperClasses = phpIndex.getViewHelperSubclasses(settings)
            completionResultSet.completeFromClasses(viewHelperClasses, "Helper")
        }
    }

}