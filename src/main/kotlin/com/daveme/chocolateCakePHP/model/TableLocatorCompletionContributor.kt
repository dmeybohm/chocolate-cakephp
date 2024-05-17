package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.resolve.types.PhpType

class TableLocatorCompletionContributor : CompletionContributor() {

    init {
        val completionProvider = FetchTableCompletionProvider()

        // When typing $this->fetchTable(' or $this->fetchTable(", with a quote
        val stringLiteralPattern = psiElement(LeafPsiElement::class.java)
            .withParent(
                psiElement(StringLiteralExpression::class.java)
                    .withParent(
                        psiElement(ParameterList::class.java)
                            .withParent(
                                psiElement(MethodReference::class.java)
                                    .with(TableLocatorMethodPattern)
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

            val project = methodReference.project
            val phpIndex = PhpIndex.getInstance(methodReference.project)
            val classType = methodReference.classReference?.type ?: return
            val type = classType.lookupCompleteType(project, phpIndex, null)
            if (!hasRequiredType(type)) {
                return
            }
            val modelSubclasses = phpIndex.getAllModelSubclasses(settings)
            completionResultSet.completeMethodCallWithParameterFromClasses(
                modelSubclasses,
                removeFromEnd = "Table",
                advanceBeyondClosingParen = true
            )
        }

        private fun hasRequiredType(type: PhpType): Boolean {
            return type.types.any {
                it.isTableLocatorInterface() ||
                        it.isAnyControllerClass() ||
                            it.hasGetTableLocatorMethodCall()
            }
        }
    }

}