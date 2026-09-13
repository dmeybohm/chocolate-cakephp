package com.daveme.chocolateCakePHP.view.viewfileindex

import com.daveme.chocolateCakePHP.*
import com.intellij.lang.ASTNode
import com.jetbrains.php.lang.lexer.PhpTokenTypes

/*
 * AST-level resolution of "template name" expressions, shared by the view file index
 * (render / element / setTemplate arguments) and the view variable index (the element
 * name in `$this->element($name, $data)`). Nothing here touches PSI or the project.
 */

// Robust string literal extraction that handles different PHP plugin versions
private fun extractStringLiteral(node: ASTNode): String {
    // Try child token
    val lit = node.findChildByType(PhpTokenTypes.STRING_LITERAL)
    val text = (lit ?: node).text
    // Strip quotes if present
    return text.removeSurrounding("'").removeSurrounding("\"")
}

/**
 * Per-file state for template name extraction.
 *
 * Caches the plain `$name = <expr>` assignments of each scope (method, function,
 * closure, or the file itself) so that resolving several variables in one method
 * only scans that method once. One instance is created per map() call, so
 * concurrent indexing of different files never shares state.
 */
internal class TemplateNameContext {
    val assignmentsByScope = HashMap<ASTNode, Map<String, List<ASTNode>>>()
}

/**
 * Collect every literal string a template expression can evaluate to.
 *
 * Handles:
 *   'literal'                          -> ["literal"]
 *   $cond ? 'a' : 'b'                  -> ["a", "b"]   (the condition is never inspected)
 *   $cond ?: 'b'                       -> ["b"]
 *   match ($x) { 1 => 'a', default => 'b' } -> ["a", "b"]  (arm conditions are never inspected)
 *   ('a')                              -> ["a"]
 *   $var                               -> the values of every `$var = <expr>` that precedes
 *                                         the use in the same scope (see resolveVariable)
 * These nest, so a ternary inside a match arm works too, and a variable may be assigned
 * a ternary of other variables. Anything else yields no values.
 */
internal fun extractTemplateNames(node: ASTNode, ctx: TemplateNameContext): List<String> {
    val result = mutableListOf<String>()
    collectTemplateNames(node, result, ctx, HashSet())
    // Several branches or assignments may yield the same name; index it once
    return result.distinct()
}

private fun collectTemplateNames(
    node: ASTNode,
    result: MutableList<String>,
    ctx: TemplateNameContext,
    visited: MutableSet<ASTNode>
) {
    when {
        node.isString() -> {
            result.add(extractStringLiteral(node))
        }
        node.isVariable() -> {
            resolveVariable(node, result, ctx, visited)
        }
        node.isTernaryExpression() -> {
            // Children: <condition> ? <true> : <false>   or   <condition> ?: <false>
            // Only the expression nodes after the "?" are possible template names.
            var afterQuestion = false
            var child = node.firstChildNode
            while (child != null) {
                if (child.elementType == PhpTokenTypes.opQUEST) {
                    afterQuestion = true
                } else if (afterQuestion && isExpressionNode(child)) {
                    collectTemplateNames(child, result, ctx, visited)
                }
                child = child.treeNext
            }
        }
        node.isMatchExpression() -> {
            var child = node.firstChildNode
            while (child != null) {
                if (child.isMatchArm() || child.isDefaultMatchArm()) {
                    collectMatchArmBodyNames(child, result, ctx, visited)
                }
                child = child.treeNext
            }
        }
        node.isParenthesizedExpression() -> {
            var child = node.firstChildNode
            while (child != null) {
                if (isExpressionNode(child)) {
                    collectTemplateNames(child, result, ctx, visited)
                }
                child = child.treeNext
            }
        }
    }
}

private fun collectMatchArmBodyNames(
    armNode: ASTNode,
    result: MutableList<String>,
    ctx: TemplateNameContext,
    visited: MutableSet<ASTNode>
) {
    // Children: <cond>, <cond>, ... => <body>
    // Only the expression after "=>" is a possible template name.
    var afterArrow = false
    var child = armNode.firstChildNode
    while (child != null) {
        if (child.elementType == PhpTokenTypes.opHASH_ARRAY) {
            afterArrow = true
        } else if (afterArrow && isExpressionNode(child)) {
            collectTemplateNames(child, result, ctx, visited)
        }
        child = child.treeNext
    }
}

/**
 * Resolve a `$name` use to the template names of every plain `$name = <expr>` assignment
 * that textually precedes the use in the same scope. All preceding assignments count, so
 * assignments in both branches of an if/else are found; a stale earlier value after a
 * sequential reassignment is included too, which is preferred over missing a branch.
 *
 * Not resolved: `$this`, method parameters, properties, `.=` and other compound
 * assignments, or anything assigned in a nested closure. Arrow functions (`fn() => ...`)
 * are treated as their own scope like closures, so a variable assigned in the enclosing
 * method is not resolved inside one even though PHP captures it by value. This is a
 * known simplification.
 *
 * The visited set holds assignment nodes already expanded on the current path and
 * stops cycles such as `$a = $b; $b = $a;` or `$a = $a ?: 'x'`.
 */
private fun resolveVariable(
    variableNode: ASTNode,
    result: MutableList<String>,
    ctx: TemplateNameContext,
    visited: MutableSet<ASTNode>
) {
    val name = variableNode.text.removePrefix("$")
    if (name == "this") {
        return
    }
    val scope = scopeNodeOf(variableNode)
    val assignmentsByName = ctx.assignmentsByScope.getOrPut(scope) { collectAssignmentsInScope(scope) }
    val assignments = assignmentsByName[name] ?: return
    val useOffset = variableNode.startOffset

    for (assignment in assignments) {
        if (assignment.startOffset >= useOffset) {
            break  // Assignments are in document order
        }
        if (!visited.add(assignment)) {
            continue
        }
        val value = assignmentValueNode(assignment)
        if (value != null) {
            collectTemplateNames(value, result, ctx, visited)
        }
        visited.remove(assignment)
    }
}

/** The nearest enclosing method, function or closure, or the file root. */
private fun scopeNodeOf(node: ASTNode): ASTNode {
    var current = node
    while (true) {
        val parent = current.treeParent ?: return current
        if (parent.isScopeNode()) {
            return parent
        }
        current = parent
    }
}

/**
 * All `$name = <expr>` assignments directly within a scope, grouped by variable name and
 * kept in document order. Nested scopes (closures, functions, methods) are not entered.
 * SELF_ASSIGNMENT_EXPRESSION (`.=`, `+=`, ...) is a different element type and is skipped
 * by isAssignmentExpression().
 */
private fun collectAssignmentsInScope(scope: ASTNode): Map<String, List<ASTNode>> {
    val result = HashMap<String, MutableList<ASTNode>>()
    collectAssignmentsRecursive(scope, result)
    return result
}

private fun collectAssignmentsRecursive(node: ASTNode, result: MutableMap<String, MutableList<ASTNode>>) {
    if (node.isAssignmentExpression()) {
        val target = firstExpressionChild(node)
        if (target != null && target.isVariable()) {
            val name = target.text.removePrefix("$")
            result.getOrPut(name) { mutableListOf() }.add(node)
        }
    }
    var child = node.firstChildNode
    while (child != null) {
        if (!child.isScopeNode()) {
            collectAssignmentsRecursive(child, result)
        }
        child = child.treeNext
    }
}

/** The right-hand side of an ASSIGNMENT_EXPRESSION: the first expression after "=". */
private fun assignmentValueNode(assignment: ASTNode): ASTNode? {
    var afterAssign = false
    var child = assignment.firstChildNode
    while (child != null) {
        if (child.elementType == PhpTokenTypes.opASGN) {
            afterAssign = true
        } else if (afterAssign && isExpressionNode(child)) {
            return child
        }
        child = child.treeNext
    }
    return null
}

private fun firstExpressionChild(node: ASTNode): ASTNode? {
    var child = node.firstChildNode
    while (child != null) {
        if (isExpressionNode(child)) {
            return child
        }
        child = child.treeNext
    }
    return null
}

// Composite nodes are expressions; leaf tokens (operators, whitespace, comments) are not.
internal fun isExpressionNode(node: ASTNode): Boolean {
    return node.firstChildNode != null
}
