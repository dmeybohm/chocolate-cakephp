package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable

class ControllerComponentCompletionContributor : CompletionContributor() {
    init {
        // When typing $this-><caret>
        val fieldInControllerPattern = PlatformPatterns.psiElement(LeafPsiElement::class.java)
            .withParent(
                PlatformPatterns.psiElement(FieldReference::class.java)
                    .with(IsControllerPattern)
            )

        extend(
            CompletionType.BASIC,
            fieldInControllerPattern,
            ControllerCompletionProvider()
        )
        extend(
            CompletionType.SMART,
            fieldInControllerPattern,
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
            if (!settings.enabled) {
                return
            }

            directLookup(settings, completionResultSet, fieldReference)
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
            if (controllerClassNames.isNotEmpty()) {
                val phpIndex = PhpIndex.getInstance(fieldReference.project)
                val containingClasses = phpIndex.getAllAncestorTypesFromFQNs(controllerClassNames)

                val componentSubclasses = phpIndex.getComponentSubclasses(settings)
                completionResultSet.completeFromClasses(
                    componentSubclasses,
                    removeFromEnd = "Component",
                    containingClasses = containingClasses
                )
            }
        }
    }

}