package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference

class ControllerModelCompletionContributor : CompletionContributor() {

    init {
        val iscontrollerPatern = object : PatternCondition<FieldReference>("IsControllerPattern") {
            override fun accepts(fieldReference: FieldReference, context: ProcessingContext): Boolean {
                val settings = Settings.getInstance(fieldReference.project)
                if (!settings.cake3Enabled) {
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

        val completionProvider = FetchTableCompletionProvider()

        // When typing $this->fetchTable(' or $this->fetchTable(", with a quote
        val fieldInControllerPattern = PlatformPatterns.psiElement(LeafPsiElement::class.java)
                .withParent(
                        PlatformPatterns.psiElement(FieldReference::class.java)
                                .with(iscontrollerPatern)
                )

        extend(
                CompletionType.BASIC,
                fieldInControllerPattern,
                completionProvider,
        )
        extend(
                CompletionType.SMART,
                fieldInControllerPattern,
                completionProvider,
        )
    }

    class FetchTableCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
                completionParameters: CompletionParameters,
                context: ProcessingContext,
                completionResultSet: CompletionResultSet
        ) {
            val fieldReference = PsiTreeUtil.getParentOfType(
                    completionParameters.position,
                    FieldReference::class.java
            ) ?: return

            val settings =
                    Settings.getInstance(fieldReference.project)
            if (!settings.cake3Enabled) {
                return
            }

            val phpIndex = PhpIndex.getInstance(fieldReference.project)
            val modelSubclasses = phpIndex.getAllModelSubclasses(settings)
            completionResultSet.completeMethodCallWithParameterFromClasses(
                    modelSubclasses,
                    removeFromEnd = "Table",
                    advanceBeyondClosingParen = true
            )
        }
    }

}

