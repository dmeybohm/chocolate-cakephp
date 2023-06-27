package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.completeFromClasses
import com.daveme.chocolateCakePHP.getAllViewHelperSubclasses
import com.daveme.chocolateCakePHP.isCakeViewFile
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference

class ViewHelperInViewHelperCompletionContributor : CompletionContributor() {

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

            val phpIndex = PhpIndex.getInstance(psiElement.project)
            val type = classReference.type.filterUnknown()
            val viewHelperSubclasses = phpIndex.getAllViewHelperSubclasses(settings)
            val filtered = viewHelperSubclasses.filter { !type.types.contains(it.fqn) }

            val isCurrentFileAViewHelper = filtered.size < viewHelperSubclasses.size
            if (!isCurrentFileAViewHelper) {
                return
            }

            completionResultSet.completeFromClasses(filtered, "Helper")
        }
    }

}