package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.isAnyTableClass
import com.daveme.chocolateCakePHP.isProbablyQueryObject
import com.daveme.chocolateCakePHP.isProbablyTableClass
import com.daveme.chocolateCakePHP.isQueryObject
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Patterns for strings that name association paths in query methods on Table and Query objects.
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

    private val associationPathMethods = setOf(
        "contain",
        "matching",
        "notmatching",
        "innerjoinwith",
        "leftjoinwith",
    )

    /**
     * Matches a string in the association path argument of a query method: the argument itself,
     * an array element, an array key, or an element or key of an array nested under a key.
     */
    private object AssociationPathCondition :
        PatternCondition<StringLiteralExpression>("AssociationPathPattern") {

        override fun accepts(
            literal: StringLiteralExpression,
            context: ProcessingContext
        ): Boolean {
            val method = associationPathPosition(literal)?.method ?: return false
            if (method.name?.lowercase() !in associationPathMethods) {
                return false
            }
            if (!Settings.getInstance(method.project).cake3Enabled) {
                return false
            }
            val classRefType = method.classReference?.type ?: return false
            if (classRefType.isProbablyTableClass() || classRefType.isProbablyQueryObject()) {
                return true
            }
            // The signature of a chain does not have to mention a table or a query:
            return classRefType.lookupCompleteType(method.project, null).types.any {
                it.isAnyTableClass() || it.isQueryObject()
            }
        }
    }

    val stringForGotoDeclaration: ElementPattern<StringLiteralExpression> =
        psiElement(StringLiteralExpression::class.java).with(AssociationPathCondition)

    val stringForCompletion: ElementPattern<LeafPsiElement> =
        psiElement(LeafPsiElement::class.java).withParent(stringForGotoDeclaration)
}
