package com.daveme.chocolateCakePHP

import com.intellij.lang.ASTNode

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
private const val METHOD_REFERENCE = "Method reference"
private const val PARAMETER_LIST = "Parameter list"
private const val ASSIGNMENT_EXPRESSION = "Assignment expression"
private const val FIELD_REFERENCE = "Field reference"
private const val STRING = "String"
private const val ARRAY_CREATION_EXPRESSION = "Array creation expression"
private const val FUNCTION_CALL = "Function call"
private const val MODIFIER_LIST = "Modifier list"
private const val ARRAY_ACCESS_EXPRESSION = "Array access expression"

// Extension functions for type checking
fun ASTNode.isVariable() = this.elementType.toString() == VARIABLE
fun ASTNode.isClassMethod() = this.elementType.toString() == CLASS_METHOD
fun ASTNode.isMethodReference() = this.elementType.toString() == METHOD_REFERENCE
fun ASTNode.isParameterList() = this.elementType.toString() == PARAMETER_LIST
fun ASTNode.isAssignmentExpression() = this.elementType.toString() == ASSIGNMENT_EXPRESSION
fun ASTNode.isFieldReference() = this.elementType.toString() == FIELD_REFERENCE
fun ASTNode.isString() = this.elementType.toString() == STRING
fun ASTNode.isArrayCreationExpression() = this.elementType.toString() == ARRAY_CREATION_EXPRESSION
fun ASTNode.isFunctionCall() = this.elementType.toString() == FUNCTION_CALL
fun ASTNode.isModifierList() = this.elementType.toString() == MODIFIER_LIST
fun ASTNode.isArrayAccessExpression() = this.elementType.toString() == ARRAY_ACCESS_EXPRESSION
