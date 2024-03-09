package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PatternCondition
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.parser.PhpElementTypes
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class FetchTableCompletionContributor : CompletionContributor() {

    init {
        val pattern = psiElement()
            .withParent(
                psiElement(ConstantReference::class.java)
                    .withParent(
                       psiElement(ParameterList::class.java)
                           .withParent(
                               MethodReference::class.java
                           )
                    )
            )

        extend(
            CompletionType.BASIC,
            pattern,
            FetchTableCompletionProvider()
        )
        extend(
            CompletionType.SMART,
            pattern,
            FetchTableCompletionProvider()
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
            if (!settings.cake2Enabled) {
                return
            }

            val methodName = methodReference.name ?: return
            if (methodName != "fetchTable") {
                return
            }
            val phpIndex = PhpIndex.getInstance(methodReference.project)
            val modelSubclasses = phpIndex.getAllModelSubclasses(settings)
            completionResultSet.completeFromClasses(modelSubclasses)
        }
    }

}