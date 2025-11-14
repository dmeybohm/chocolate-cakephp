package com.daveme.chocolateCakePHP

import com.intellij.lang.ASTNode
import com.jetbrains.php.lang.parser.PhpElementTypes as PhpParserElementTypes;

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
fun ASTNode.isMethodReference() = this.elementType == PhpParserElementTypes.METHOD_REFERENCE
fun ASTNode.isParameterList() = this.elementType == PhpParserElementTypes.PARAMETER_LIST
fun ASTNode.isAssignmentExpression() = this.elementType == PhpParserElementTypes.ASSIGNMENT_EXPRESSION
fun ASTNode.isFieldReference() = this.elementType == PhpParserElementTypes.FIELD_REFERENCE
fun ASTNode.isString() = this.elementType == PhpParserElementTypes.STRING
fun ASTNode.isArrayCreationExpression() = this.elementType == PhpParserElementTypes.ARRAY_CREATION_EXPRESSION
fun ASTNode.isFunctionCall() = this.elementType == PhpParserElementTypes.FUNCTION_CALL
fun ASTNode.isModifierList() = this.elementType == PhpParserElementTypes.MODIFIER_LIST
fun ASTNode.isArrayAccessExpression() = this.elementType == PhpParserElementTypes.ARRAY_ACCESS_EXPRESSION
fun ASTNode.isHashArrayElement() = this.elementType == PhpParserElementTypes.HASH_ARRAY_ELEMENT
fun ASTNode.isArrayKey() = this.elementType == PhpParserElementTypes.ARRAY_KEY
fun ASTNode.isArrayValue() = this.elementType == PhpParserElementTypes.ARRAY_VALUE
