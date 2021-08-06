package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable

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
            val psiElement = completionParameters.position
            var parent  = psiElement.parent ?: return
            if (parent !is FieldReference) {
                parent = findSiblingFieldReference(
                    psiElement
                ) ?: return
            }

            val fieldReference = parent as FieldReference
            val settings =
                Settings.getInstance(psiElement.project)
            if (!settings.enabled) {
                return
            }

            // Don't add completion for nested classes: (e.g. $this->FooBar->FooBar):
            if (fieldReference.firstChild is FieldReference) {
                return
            }

            val classReference = fieldReference.classReference ?: return
            if (!classReference.type.isComplete || classReference !is Variable) {
                return
            }

            val controllerClassNames = classReference.type.types.filter { it.isControllerClass() }
            if (controllerClassNames.isNotEmpty()) {
                val phpIndex = PhpIndex.getInstance(psiElement.project)
                val containingClasses = phpIndex.getAllAncestorTypesFromFQNs(controllerClassNames)

                completionResultSet.completeFromClasses(
                    phpIndex.getAllModelSubclasses(settings),
                    containingClasses = containingClasses
                )

                completionResultSet.completeFromClasses(
                    phpIndex.getAllComponentSubclasses(settings),
                    replaceName = "Component",
                    containingClasses = containingClasses
                )
            }
        }

    }

    companion object {

        private fun findSiblingFieldReference(element: PsiElement): PsiElement? {
            val prevSibling = element.prevSibling ?: return null
            return prevSibling.children.find { it is FieldReference }
        }
    }

}
