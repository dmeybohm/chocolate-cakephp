package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class FetchTableCompletionContributor : CompletionContributor() {

    init {
        // When typing $this->fetchTable(, without a quote:
        val constantPattern = psiElement(LeafPsiElement::class.java)
            .withParent(
                psiElement(ConstantReference::class.java)
                    .withParent(
                       psiElement(ParameterList::class.java)
                           .withParent(
                               MethodReference::class.java
                           )
                    )
            )
        val completionProvider = FetchTableCompletionProvider()

        extend(
            CompletionType.BASIC,
            constantPattern,
            completionProvider,
        )
        extend(
            CompletionType.SMART,
            constantPattern,
            completionProvider,
        )

        // When typing $this->fetchTable(' or $this->fetchTable(", with a quote
        val stringLiteralPattern = psiElement(LeafPsiElement::class.java)
            .withParent(
                psiElement(StringLiteralExpression::class.java)
                    .withParent(
                        psiElement(ParameterList::class.java)
                            .withParent(
                                MethodReference::class.java
                            )
                    )
            )
        extend(
            CompletionType.BASIC,
            stringLiteralPattern,
            completionProvider,
        )
        extend(
            CompletionType.SMART,
            stringLiteralPattern,
            completionProvider,
        )

    }

    class FetchTableCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            completionParameters: CompletionParameters,
            context: ProcessingContext,
            completionResultSet: CompletionResultSet
        ) {
            val methodReference = PsiTreeUtil.getParentOfType(
                completionParameters.position,
                MethodReference::class.java
            ) ?: return

            val settings =
                Settings.getInstance(methodReference.project)
            if (!settings.cake3Enabled) {
                return
            }

            val methodName = methodReference.name ?: return
            if (methodName != "fetchTable") {
                return
            }

            // If the current element is not quote, we need to quote:
            val completeInsideString = completionParameters.position.parent is ConstantReference

            val phpIndex = PhpIndex.getInstance(methodReference.project)
            val modelSubclasses = phpIndex.getAllModelSubclasses(settings)
            completionResultSet.completeMethodCallWithParameterFromClasses(
                modelSubclasses,
                chopFromEnd = "Table",
                completeInsideString = completeInsideString
            )
        }
    }

}