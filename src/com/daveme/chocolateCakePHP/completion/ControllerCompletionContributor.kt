package com.daveme.chocolateCakePHP.completion

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.psi.AddValueToPropertyInsertHandler
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
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
            val psiElement = completionParameters.position

            var parent  = psiElement.parent ?: return
            if (parent !is FieldReference) {
                parent = findSiblingFieldReference(psiElement) ?: return
            }

            val fieldReference = parent as FieldReference

            // Don't add completion for nested classes: (e.g. $this->FooBar->FooBar):
            if (fieldReference.firstChild is FieldReference) {
                return
            }

            val classReference = fieldReference.classReference ?: return

            val controllerClassNames = classReference.type.types.filter { it.isControllerClass() }
            if (controllerClassNames.isNotEmpty()) {
                val phpIndex = PhpIndex.getInstance(psiElement.project)
                val modelClasses = getAllModelSubclasses(phpIndex)
                val containingClasses = controllerClassesFromFQNs(phpIndex, controllerClassNames)
                completionResultSet.completeFromClasses(
                    modelClasses,
                    insertHandler = usesHandler,
                    containingClasses = containingClasses
                )

                val componentClasses = getAllComponentSubclasses(phpIndex)
                completionResultSet.completeFromClasses(
                    componentClasses,
                    replaceName = "Component",
                    insertHandler = componentsHandler,
                    containingClasses = containingClasses
                )
            }
        }
    }

    companion object {
        private val usesHandler = AddValueToPropertyInsertHandler("uses")
        private val componentsHandler = AddValueToPropertyInsertHandler("components")

        private fun findSiblingFieldReference(element: PsiElement): PsiElement? {
            val prevSibling = element.prevSibling ?: return null
            return prevSibling.children.find { it is FieldReference }
        }
    }

}
