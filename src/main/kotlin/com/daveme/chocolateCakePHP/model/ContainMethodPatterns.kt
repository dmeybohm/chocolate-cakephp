package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.isProbablyTableClass
import com.daveme.chocolateCakePHP.isProbablyQueryObject
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Patterns for matching contain() method calls on Table and Query objects.
 *
 * Note on pattern element types:
 * - Completion patterns start with LeafPsiElement because CompletionContributor works at the
 *   character/token level where the caret is positioned during typing.
 * - Goto declaration patterns start with StringLiteralExpression because GotoDeclarationHandler
 *   works at the expression level where navigation occurs from an already-formed element.
 *
 * This is standard IntelliJ Platform API behavior, not an artifact of our implementation.
 */
object ContainMethodPatterns {

    /**
     * Pattern condition that matches contain() method references on Table or Query objects.
     */
    private object ContainMethodCondition :
        PatternCondition<MethodReference>("ContainMethodPattern") {

        override fun accepts(
            methodReference: MethodReference,
            context: ProcessingContext
        ): Boolean {
            if (!"contain".equals(methodReference.name, ignoreCase = true)) {
                return false
            }

            val settings = Settings.getInstance(methodReference.project)
            if (!settings.cake3Enabled) {
                return false
            }

            val classRefType = methodReference.classReference?.type ?: return false
            return classRefType.isProbablyTableClass() || classRefType.isProbablyQueryObject()
        }
    }

    /**
     * Pattern for string literal directly in parameter list (for completion).
     *
     * Matches: $query->contain('<caret>Authors')
     */
    val stringForCompletion: ElementPattern<LeafPsiElement> = psiElement(LeafPsiElement::class.java)
        .withParent(
            psiElement(StringLiteralExpression::class.java)
                .withParent(
                    psiElement(ParameterList::class.java)
                        .withParent(
                            psiElement(MethodReference::class.java)
                                .with(ContainMethodCondition)
                        )
                )
        )

    /**
     * Pattern for string literal directly in parameter list (for goto declaration).
     *
     * Matches: $query->contain('Authors')
     */
    val stringForGotoDeclaration: ElementPattern<StringLiteralExpression> = psiElement(StringLiteralExpression::class.java)
        .withParent(
            psiElement(ParameterList::class.java)
                .withParent(
                    psiElement(MethodReference::class.java)
                        .with(ContainMethodCondition)
                )
        )

    /**
     * Pattern for string literal inside array in parameter list (for completion).
     *
     * Matches: $query->contain(['<caret>Authors', 'Comments'])
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
                                                .with(ContainMethodCondition)
                                        )
                                )
                        )
                )
        )

    /**
     * Pattern for string literal inside array in parameter list (for goto declaration).
     *
     * Matches: $query->contain(['Authors', 'Comments'])
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
                                        .with(ContainMethodCondition)
                                )
                        )
                )
        )
}
