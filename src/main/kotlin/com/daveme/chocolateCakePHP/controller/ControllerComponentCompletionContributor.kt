package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
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
                directLookup(settings, completionResultSet, childElement)
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

                val componentSubclasses = phpIndex.getAllComponentSubclasses(settings)
                completionResultSet.completeFromClasses(
                    componentSubclasses,
                    chopFromEnd = "Component",
                    containingClasses = containingClasses
                )
            }
        }
    }

}