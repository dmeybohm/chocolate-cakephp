package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.getPossibleTableClasses
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.parser.PhpElementTypes
import com.intellij.psi.PsiElement

/**
 * A string that names an association path: the call it is the first argument of, and the
 * segments of the array keys it is nested under.
 */
data class AssociationPathPosition(
    val method: MethodReference,
    val enclosingSegments: List<String>,
)

/**
 * The position of [literal] in an association path argument, or null when it is not in one.
 */
fun associationPathPosition(literal: StringLiteralExpression): AssociationPathPosition? {
    val segments = ArrayDeque<String>()
    var current: PsiElement = when {
        literal.parent is ParameterList -> literal
        literal.parent.isArrayValue() -> literal.parent.parent as? ArrayCreationExpression ?: return null
        literal.parent.isArrayKey() -> literal.parent.parent.parent as? ArrayCreationExpression ?: return null
        else -> return null
    }
    while (current.parent !is ParameterList) {
        // An array nested in another array is the value of the key above it:
        val hashElement = current.parent?.takeIf { it.isArrayValue() }?.parent as? ArrayHashElement
            ?: return null
        val key = hashElement.key as? StringLiteralExpression ?: return null
        segments.addAll(0, key.contents.split('.'))
        current = hashElement.parent as? ArrayCreationExpression ?: return null
    }
    val parameterList = current.parent as ParameterList
    if (parameterList.parameters.firstOrNull() !== current) {
        return null
    }
    val method = parameterList.parent as? MethodReference ?: return null
    return AssociationPathPosition(method, segments)
}

private fun PsiElement?.isArrayValue(): Boolean =
    this?.node?.elementType == PhpElementTypes.ARRAY_VALUE

private fun PsiElement?.isArrayKey(): Boolean =
    this?.node?.elementType == PhpElementTypes.ARRAY_KEY

object AssociationPathResolver {

    /**
     * The tables reached from [tables] by following [hops], one association per segment.
     */
    fun walk(project: Project, tables: Collection<String>, hops: List<String>): List<String> {
        var current = tables.toList()
        for (hop in hops) {
            current = targetTables(project, current, hop)
            if (current.isEmpty()) {
                break
            }
        }
        return current
    }

    /**
     * The tables the association [alias] of [tables] resolves to, by convention when no
     * configuration of [tables] determines it.
     */
    fun targetTables(project: Project, tables: Collection<String>, alias: String): List<String> {
        val configured = ConfiguredMemberService.memberType(
            project, tables, setOf(MemberKind.ASSOCIATION), alias
        )
        val result = configured?.classes.orEmpty().map { it.fqn }.toMutableList()
        if (configured == null || !configured.hasInitializeLoad) {
            PhpIndex.getInstance(project)
                .getPossibleTableClasses(Settings.getInstance(project), alias)
                .mapTo(result) { it.fqn }
        }
        return result.distinct()
    }
}
