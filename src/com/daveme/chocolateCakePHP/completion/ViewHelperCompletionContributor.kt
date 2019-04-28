package com.daveme.chocolateCakePHP.completion

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.completeFromClasses
import com.daveme.chocolateCakePHP.isCakeTemplate
import com.daveme.chocolateCakePHP.getAllViewHelperSubclasses
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference

class ViewHelperCompletionContributor : CompletionContributor() {
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
            val containingFile = psiElement.containingFile

            val settings = Settings.getInstance(psiElement.project)
            val parent = (psiElement.parent ?: return) as? FieldReference ?: return
            if (!isCakeTemplate(settings, containingFile.name)) {
                return
            }
            val classReference = parent.classReference ?: return
            if (classReference.text == "\$this") {
                val phpIndex = PhpIndex.getInstance(psiElement.project)
                val viewHelperClasses = getAllViewHelperSubclasses(phpIndex)
                completionResultSet.completeFromClasses(viewHelperClasses, "Helper")
            }
        }
    }

}