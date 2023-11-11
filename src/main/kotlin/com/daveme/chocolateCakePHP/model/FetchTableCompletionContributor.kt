package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable
import com.intellij.patterns.PlatformPatterns.psiElement
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class FetchTableCompletionContributor : CompletionContributor() {

    init {
//        val pattern = psiElement(PhpTokenTypes.STRING_LITERAL).withParent(
//            psiElement(StringLiteralExpression::class.java).withParent(
//                psiElement(ParameterList::class.java).withParent(
//                    psiElement(MethodReference::class.java)
//                )
//            )
//        )

        val pattern = psiElement().withParent(ParameterList::class.java)

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
            val fieldReference = PsiTreeUtil.getParentOfType(
                completionParameters.position,
                MethodReference::class.java
            ) ?: return

            val settings =
                Settings.getInstance(fieldReference.project)
            if (!settings.cake2Enabled) {
                return
            }

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
                    containingClasses = containingClasses
                )
            }
        }
    }

}