package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable
import com.intellij.psi.util.PsiTreeUtil


class ControllerModelOrTableCompletionContributor : CompletionContributor() {

    init {
        val isControllerPattern = object : PatternCondition<FieldReference>("IsControllerPattern") {
            override fun accepts(fieldReference: FieldReference, context: ProcessingContext): Boolean {
                val settings = Settings.getInstance(fieldReference.project)
                if (!settings.enabled) {
                    return false
                }
                val classRefType = fieldReference.classReference?.type ?: return false
                val completedClassType = if (classRefType.isComplete)
                    classRefType
                else {
                    val phpIndex = PhpIndex.getInstance(fieldReference.project)
                    phpIndex.completeType(fieldReference.project, classRefType, null)
                }
                return completedClassType.types.any { it.isAnyControllerClass() }
            }
        }

        val isUppercaseFieldRef = object : PatternCondition<FieldReference>("IsUppercaseFieldRef") {
            override fun accepts(fieldReference: FieldReference, context: ProcessingContext): Boolean {
                val settings = Settings.getInstance(fieldReference.project)
                if (!settings.enabled) {
                    return false
                }
                return fieldReference.name?.startsWithUppercaseCharacter() ?: false
            }
        }

        val completionProvider = ControllerModelCompletionProvider()

        // When typing $this-><caret>
        val fieldInControllerPattern = PlatformPatterns.psiElement(LeafPsiElement::class.java)
                .withParent(
                        PlatformPatterns.psiElement(FieldReference::class.java)
                                .with(isControllerPattern)
                )

        // When typing $this->Movie-><caret>
        val nestedPattern = PlatformPatterns.psiElement(LeafPsiElement::class.java)
                .withParent(
                        PlatformPatterns.psiElement(FieldReference::class.java)
                                .withFirstChild(
                                        PlatformPatterns.psiElement(FieldReference::class.java)
                                                .with(isUppercaseFieldRef)
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

            val controllerClassNames = classReference.type.types.filter { it.isAnyControllerClass() }
            if (controllerClassNames.size > 0) {
                val phpIndex = PhpIndex.getInstance(fieldReference.project)
                val containingClasses = phpIndex.getAllAncestorTypesFromFQNs(controllerClassNames)
                val modelSubclasses = phpIndex.getAllModelSubclasses(settings)
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
            val modelClasses = phpIndex.getAllModelSubclasses(settings)
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
