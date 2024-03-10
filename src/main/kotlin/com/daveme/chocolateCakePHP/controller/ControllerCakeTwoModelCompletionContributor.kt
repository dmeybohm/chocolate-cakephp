package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable
import com.intellij.psi.util.PsiTreeUtil


class ControllerCakeTwoModelCompletionContributor : CompletionContributor() {

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
            val fieldReference = PsiTreeUtil.getParentOfType(
                completionParameters.position,
                FieldReference::class.java
            ) ?: return

            val settings =
                Settings.getInstance(fieldReference.project)
            if (!settings.cake2Enabled) {
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

            val controllerClassNames = classReference.type.types.filter { it.isControllerClass() }
            if (controllerClassNames.isNotEmpty()) {
                val phpIndex = PhpIndex.getInstance(fieldReference.project)
                val containingClasses = phpIndex.getAllAncestorTypesFromFQNs(controllerClassNames)

                val modelSubclasses = phpIndex.getAllModelSubclasses(settings)
                completionResultSet.completeFromClasses(
                    modelSubclasses,
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
            val isUppercase = fieldName?.startsWithUppercaseCharacter() ?: false
            if (!isUppercase) {
                return
            }

            // Check if "child" (preceding $this->FieldReference) is in the list of model subclasses
            val modelClasses = phpIndex.getAllModelSubclasses(settings)
            val fqn = "\\" + fieldName
            if (!modelClasses.any { modelClass -> modelClass.fqn == fqn }) {
                return
            }
            completionResultSet.completeFromClasses(modelClasses)
        }
    }

}
