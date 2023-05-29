package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable

class ControllerComponentCompletionContributor : CompletionContributor() {
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
            var parent = psiElement.parent ?: return
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

            val childElement = fieldReference.firstChild
            if (childElement is FieldReference) {
                return directLookup(settings, psiElement, completionResultSet, childElement)
            } else {
                return directLookup(settings, psiElement, completionResultSet, fieldReference)
            }
        }

        private fun directLookup(
            settings: Settings,
            psiElement: PsiElement,
            completionResultSet: CompletionResultSet,
            fieldReference: FieldReference,
        ) {
            val classReference = fieldReference.classReference ?: return
            if (!classReference.type.isComplete || classReference !is Variable) {
                return
            }

            val controllerClassNames = classReference.type.types.filter { it.isControllerClass() }
            if (controllerClassNames.isNotEmpty()) {
                val phpIndex = PhpIndex.getInstance(psiElement.project)
                val containingClasses = phpIndex.getAllAncestorTypesFromFQNs(controllerClassNames)

                val componentSubclasses = phpIndex.getAllComponentSubclasses(settings)
                completionResultSet.completeFromClasses(
                    componentSubclasses,
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