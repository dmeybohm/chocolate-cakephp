package com.daveme.chocolateCakePHP.view

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

/**
 * Patterns for matching asset methods ($this->Html->css, script, image) and their parameters.
 */
object AssetMethodPatterns {

    /**
     * Pattern condition that matches asset method references: css, script, image
     */
    private object AssetMethodCondition :
        PatternCondition<MethodReference>("AssetMethodPattern") {

        private val assetMethods = listOf("css", "script", "image")

        override fun accepts(
            methodReference: MethodReference,
            context: ProcessingContext
        ): Boolean {
            val fieldReference = methodReference.firstChild as? FieldReference ?: return false
            val variable = fieldReference.firstChild as? Variable ?: return false
            if (variable.name != "this") {
                return false
            }
            return assetMethods.any { assetMethod ->
                assetMethod.equals(methodReference.name, ignoreCase = true)
            }
        }
    }

    /**
     * Pattern for string literal directly in parameter list (for completion).
     *
     * Matches: $this->Html->css('<caret>movie')
     */
    val stringForCompletion: ElementPattern<LeafPsiElement> = psiElement(LeafPsiElement::class.java)
        .withParent(
            psiElement(StringLiteralExpression::class.java)
                .withParent(
                    psiElement(ParameterList::class.java)
                        .withParent(
                            psiElement(MethodReference::class.java)
                                .with(AssetMethodCondition)
                        )
                )
        )

    /**
     * Pattern for string literal directly in parameter list (for goto declaration).
     *
     * Matches: $this->Html->css('movie')
     */
    val stringForGotoDeclaration: ElementPattern<StringLiteralExpression> = psiElement(StringLiteralExpression::class.java)
        .withParent(
            psiElement(ParameterList::class.java)
                .withParent(
                    psiElement(MethodReference::class.java)
                        .with(AssetMethodCondition)
                )
        )

    /**
     * Pattern for string literal inside array in parameter list (for completion).
     *
     * Matches: $this->Html->css(['<caret>movie', 'forms'])
     */
    val arrayForCompletion: ElementPattern<LeafPsiElement> = psiElement(LeafPsiElement::class.java)
        .withParent(
            psiElement(StringLiteralExpression::class.java)
                .withParent(
                    psiElement(PhpPsiElement::class.java) // ArrayElement
                        .withParent(
                            psiElement(ArrayCreationExpression::class.java)
                                .withParent(
                                    psiElement(ParameterList::class.java)
                                        .withParent(
                                            psiElement(MethodReference::class.java)
                                                .with(AssetMethodCondition)
                                        )
                                )
                        )
                )
        )

    /**
     * Pattern for string literal inside array in parameter list (for goto declaration).
     *
     * Matches: $this->Html->css(['movie', 'forms'])
     */
    val arrayForGotoDeclaration: ElementPattern<StringLiteralExpression> = psiElement(StringLiteralExpression::class.java)
        .withParent(
            psiElement(PhpPsiElement::class.java) // ArrayElement
                .withParent(
                    psiElement(ArrayCreationExpression::class.java)
                        .withParent(
                            psiElement(ParameterList::class.java)
                                .withParent(
                                    psiElement(MethodReference::class.java)
                                        .with(AssetMethodCondition)
                                )
                        )
                )
        )
}
