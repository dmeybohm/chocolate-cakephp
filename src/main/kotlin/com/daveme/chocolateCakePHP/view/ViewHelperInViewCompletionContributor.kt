package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.isCakeViewFile
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.intellij.codeInsight.completion.*
import com.intellij.openapi.project.DumbService
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
            if (DumbService.getInstance(psiElement.project).isDumb) {
                return
            }
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
            val loaded = ConfiguredMemberService.loadedMembers(
                psiElement.project,
                viewType(settings).types,
                setOf(MemberKind.HELPER)
            )
            completionResultSet.completeFromLoadedMembers(loaded.members)

            // The view lazy loads any helper by name, so the convention list stays:
            val phpIndex = PhpIndex.getInstance(psiElement.project)
            val viewHelperClasses = phpIndex.getViewHelperSubclasses(settings).filter {
                it.name.removeFromEnd("Helper", ignoreCase = true) !in loaded.members
            }
            completionResultSet.completeFromClasses(viewHelperClasses, "Helper")
        }
    }

}