package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable
import com.intellij.psi.util.PsiTreeUtil


class ControllerModelOrTableCompletionContributor : CompletionContributor() {

    init {
        val completionProvider = ControllerModelCompletionProvider()

        // When typing $this-><caret>
        val fieldInControllerPattern = PlatformPatterns.psiElement(LeafPsiElement::class.java)
                .withParent(
                        PlatformPatterns.psiElement(FieldReference::class.java)
                                .with(IsControllerPattern)
                )

        // When typing $this->Movie-><caret>
        val nestedPattern = PlatformPatterns.psiElement(LeafPsiElement::class.java)
                .withParent(
                        PlatformPatterns.psiElement(FieldReference::class.java)
                                .withFirstChild(
                                        PlatformPatterns.psiElement(FieldReference::class.java)
                                                .with(IsUpperCaseFieldRefPattern)
                                )
                )

        extend(
            CompletionType.BASIC,
            fieldInControllerPattern,
            completionProvider
        )
        extend(
            CompletionType.SMART,
            fieldInControllerPattern,
            completionProvider
        )

        extend(
            CompletionType.BASIC,
            nestedPattern,
            completionProvider
        )
        extend(
            CompletionType.SMART,
            nestedPattern,
            completionProvider
        )
    }

    private class ControllerModelCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            completionParameters: CompletionParameters,
            processingContext: ProcessingContext,
            completionResultSet: CompletionResultSet
        ) {
            val fieldReference = PsiTreeUtil.getParentOfType(
                completionParameters.position,
                FieldReference::class.java
            ) ?: return

            val settings =
                Settings.getInstance(fieldReference.project)
            if (!settings.enabled) {
                return
            }

            val childElement = fieldReference.firstChild
            return if (childElement is FieldReference) {
                nestedLookup(settings, completionResultSet, childElement)
            } else {
                directLookup(settings, completionResultSet, fieldReference)
            }
        }

        private fun directLookup(
            settings: Settings,
            completionResultSet: CompletionResultSet,
            fieldReference: FieldReference,
        ) {
            val classReference = fieldReference.classReference ?: return
            if (classReference !is Variable) {
                return
            }
            val type = classReference.type.lookupCompleteType(classReference.project, null)

            val controllerClassNames = type.types.filter { it.isAnyControllerClass() }
            if (controllerClassNames.size > 0) {
                val phpIndex = PhpIndex.getInstance(fieldReference.project)
                val containingClasses = phpIndex.getAllAncestorTypesFromFQNs(controllerClassNames)
                val modelSubclasses = phpIndex.getModelSubclasses(settings)
                completionResultSet.completeFromClasses(
                        modelSubclasses,
                        removeFromEnd = "Table",
                        containingClasses = containingClasses,
                )
            }
        }

        private fun nestedLookup(
            settings: Settings,
            completionResultSet: CompletionResultSet,
            fieldReferenceChild: FieldReference,
        ) {
            val phpIndex = PhpIndex.getInstance(fieldReferenceChild.project)
            val fieldName = fieldReferenceChild.name

            // Check if "child" (preceding $this->FieldReference) is in the list of model subclasses
            val modelClasses = phpIndex.getModelSubclasses(settings)
            val fieldStr = "\\" + fieldName
            if (!modelClasses.any { modelClass -> modelClass.fqn.contains(fieldStr) }) {
                return
            }
            completionResultSet.completeFromClasses(
                    modelClasses,
                    removeFromEnd = "Table"
            )
        }
    }

}
