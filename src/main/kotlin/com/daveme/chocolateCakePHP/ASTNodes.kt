package com.daveme.chocolateCakePHP

import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.parser.PhpElementTypes

/**
 * Helper functions for checking PHP element types using string comparison instead of
 * direct static field references to PhpElementTypes constants.
 *
 * This approach avoids plugin verifier warnings about unresolved fields that might
 * occur if JetBrains moves these constants within the PHP plugin's class hierarchy.
 * Since IElementType.toString() returns the debug name of the element type, string
 * comparison is a stable way to identify element types across PHP plugin versions.
 */

// Element type name constants
private const val VARIABLE = "VARIABLE"
private const val CLASS_METHOD = "CLASS_METHOD"
private const val IDENTIFIER = "IDENTIFIER"
private const val FUNCTION = "FUNCTION"

/**
 * Compares two strings by matching only alphabetic characters, case-insensitively.
 * Non-alphabetic characters are ignored. Does not allocate memory during comparison.
 *
 * Examples:
 * - "VARIABLE" matches "variable"
 * - "CLASS_METHOD" matches "class method", "ClassMethod", "class-method"
 * - "identifier" matches "IDENTIFIER", "Identifier"
 */
private fun equalsAlphaIgnoreCase(a: CharSequence, b: CharSequence): Boolean {
    var i = 0
    var j = 0
    val na = a.length
    val nb = b.length

    while (true) {
        // Skip non-letters in A
        while (i < na && !a[i].isAsciiLetter()) i++

        // Skip non-letters in B
        while (j < nb && !b[j].isAsciiLetter()) j++

        // Both exhausted
        if (i >= na && j >= nb) return true

        // One exhausted
        if (i >= na || j >= nb) return false

        // Compare lowercased ascii
        if (a[i].lowerAscii() != b[j].lowerAscii()) return false

        i++
        j++
    }
}

private fun Char.isAsciiLetter(): Boolean =
    (this in 'A'..'Z') || (this in 'a'..'z')

private fun Char.lowerAscii(): Char =
    if (this in 'A'..'Z') (this + 32) else this

// Extension functions for type checking
fun ASTNode.isVariable() = equalsAlphaIgnoreCase(this.elementType.toString(), VARIABLE)
fun ASTNode.isClassMethod() = equalsAlphaIgnoreCase(this.elementType.toString(), CLASS_METHOD)
fun ASTNode.isIdentifier() = equalsAlphaIgnoreCase(this.elementType.toString(), IDENTIFIER)
fun ASTNode.isMethodReference() = this.elementType == PhpElementTypes.METHOD_REFERENCE
fun ASTNode.isParameterList() = this.elementType == PhpElementTypes.PARAMETER_LIST
fun ASTNode.isAssignmentExpression() = this.elementType == PhpElementTypes.ASSIGNMENT_EXPRESSION
fun ASTNode.isFieldReference() = this.elementType == PhpElementTypes.FIELD_REFERENCE
fun ASTNode.isString() = this.elementType == PhpElementTypes.STRING
fun ASTNode.isArrayCreationExpression() = this.elementType == PhpElementTypes.ARRAY_CREATION_EXPRESSION
fun ASTNode.isFunctionCall() = this.elementType == PhpElementTypes.FUNCTION_CALL
fun ASTNode.isModifierList() = this.elementType == PhpElementTypes.MODIFIER_LIST
fun ASTNode.isArrayAccessExpression() = this.elementType == PhpElementTypes.ARRAY_ACCESS_EXPRESSION
fun ASTNode.isHashArrayElement() = this.elementType == PhpElementTypes.HASH_ARRAY_ELEMENT
fun ASTNode.isArrayKey() = this.elementType == PhpElementTypes.ARRAY_KEY
fun ASTNode.isArrayValue() = this.elementType == PhpElementTypes.ARRAY_VALUE
fun ASTNode.isTernaryExpression() = this.elementType == PhpElementTypes.TERNARY_EXPRESSION
fun ASTNode.isMatchExpression() = this.elementType == PhpElementTypes.MATCH_EXPRESSION
fun ASTNode.isMatchArm() = this.elementType == PhpElementTypes.MATCH_ARM
fun ASTNode.isParenthesizedExpression() = this.elementType == PhpElementTypes.PARENTHESIZED_EXPRESSION
fun ASTNode.isDefaultMatchArm() = this.elementType == PhpElementTypes.DEFAULT_MATCH_ARM
fun ASTNode.isFunction() = equalsAlphaIgnoreCase(this.elementType.toString(), FUNCTION)
fun ASTNode.isClosure() = this.elementType == PhpElementTypes.CLOSURE

/**
 * A node that introduces a new local variable scope: a method, a function, or a closure.
 * Arrow functions (`fn() => ...`) count as a scope too, even though PHP captures the
 * enclosing scope by value. This is a known simplification.
 */
fun ASTNode.isScopeNode() = this.isClassMethod() || this.isFunction() || this.isClosure()

/**
 * The syntactic parts of a METHOD_REFERENCE node such as `$this->set('a', $b)`.
 *
 * [receiverName] is the bare variable name of the receiver ("this" for `$this->...`), or null
 * when the receiver is not a plain variable (for example `$this->Html->link(...)`, whose
 * receiver is a field reference). [parameters] are the significant children of the
 * PARAMETER_LIST: whitespace and commas are dropped, everything else is kept in order.
 */
data class MethodCallParts(
    val node: ASTNode,
    val receiverName: String?,
    val methodName: String?,
    val parameters: List<ASTNode>
) {
    /** True for `$this->name(...)`; the method name is compared ignoring case, as PHP does. */
    fun isThisCall(name: String): Boolean =
        receiverName == "this" && methodName?.equals(name, ignoreCase = true) == true
}

/** Significant children of a PARAMETER_LIST node (no whitespace, no commas). */
fun ASTNode.parameterNodes(): List<ASTNode> {
    val result = mutableListOf<ASTNode>()
    var child = firstChildNode
    while (child != null) {
        if (child.elementType != TokenType.WHITE_SPACE && child.elementType != PhpTokenTypes.opCOMMA) {
            result.add(child)
        }
        child = child.treeNext
    }
    return result
}

/** Reads receiver, method name and parameters of a METHOD_REFERENCE; null for any other node. */
fun ASTNode.readMethodCall(): MethodCallParts? {
    if (!isMethodReference()) {
        return null
    }
    var receiverName: String? = null
    var methodName: String? = null
    var parameters: List<ASTNode> = emptyList()
    var child = firstChildNode
    while (child != null) {
        when {
            child.isVariable() -> receiverName = child.text.removePrefix("$")
            child.elementType == PhpTokenTypes.IDENTIFIER -> methodName = child.text
            child.isParameterList() -> parameters = child.parameterNodes()
        }
        child = child.treeNext
    }
    return MethodCallParts(this, receiverName, methodName, parameters)
}

/**
 * Every METHOD_REFERENCE at or below this node, in document order, whose parts satisfy
 * [predicate]. Nested calls inside a matching call's arguments are visited too.
 */
fun ASTNode.collectMethodCalls(predicate: (MethodCallParts) -> Boolean): List<MethodCallParts> {
    val result = mutableListOf<MethodCallParts>()
    collectMethodCallsInto(this, result, predicate)
    return result
}

private fun collectMethodCallsInto(
    node: ASTNode,
    result: MutableList<MethodCallParts>,
    predicate: (MethodCallParts) -> Boolean
) {
    val parts = node.readMethodCall()
    if (parts != null && predicate(parts)) {
        result.add(parts)
    }
    var child = node.firstChildNode
    while (child != null) {
        collectMethodCallsInto(child, result, predicate)
        child = child.treeNext
    }
}

/**
 * The unquoted value of a STRING node, or of a wrapper node (such as ARRAY_KEY or ARRAY_VALUE)
 * whose direct child is a STRING. Null for anything else, so a variable such as `$key` is never
 * mistaken for a literal.
 */
fun ASTNode.stringLiteralValue(): String? {
    if (isString()) {
        return unquotedStringText(this)
    }
    var child = firstChildNode
    while (child != null) {
        if (child.isString()) {
            return unquotedStringText(child)
        }
        child = child.treeNext
    }
    return null
}

private fun unquotedStringText(stringNode: ASTNode): String {
    val lit = stringNode.findChildByType(PhpTokenTypes.STRING_LITERAL)
    return (lit ?: stringNode).text.removeSurrounding("'").removeSurrounding("\"")
}

/** True for a `compact(...)` function call (name compared ignoring case, leading backslash allowed). */
fun ASTNode.isCompactCall(): Boolean {
    if (!isFunctionCall()) {
        return false
    }
    var child = firstChildNode
    while (child != null) {
        if (child.isParameterList()) {
            return false
        }
        if (child.text.trim().removePrefix("\\").equals("compact", ignoreCase = true)) {
            return true
        }
        child = child.treeNext
    }
    return false
}
