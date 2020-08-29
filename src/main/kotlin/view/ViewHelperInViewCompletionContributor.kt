package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.completeFromClasses
import com.daveme.chocolateCakePHP.getAllViewHelperSubclasses
import com.daveme.chocolateCakePHP.isCakeTemplate
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
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
            val settings =
                Settings.getInstance(psiElement.project)
            if (!settings.enabled) {
                return
            }

            val parent = (psiElement.parent ?: return) as? FieldReference ?: return
            val containingFile = psiElement.containingFile
            if (!containingFile.name.isCakeTemplate(settings)) {
                return
            }
            val classReference = parent.classReference ?: return
            if (classReference.text == "\$this") {
                val phpIndex = PhpIndex.getInstance(psiElement.project)
                val viewHelperClasses = phpIndex.getAllViewHelperSubclasses(settings)
                completionResultSet.completeFromClasses(viewHelperClasses, "Helper")
            }
        }
    }

}