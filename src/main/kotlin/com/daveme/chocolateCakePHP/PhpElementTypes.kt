package com.daveme.chocolateCakePHP

import com.intellij.lang.ASTNode
import com.jetbrains.php.lang.psi.PhpElementType
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
private const val IDENTIFIER = "identifier"
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
fun ASTNode.isIdentifier() = this.elementType.toString() == IDENTIFIER
fun ASTNode.isMethodReference() = this.elementType == PhpParserElementTypes.METHOD_REFERENCE
fun ASTNode.isParameterList() = this.elementType == PhpParserElementTypes.PARAMETER_LIST
fun ASTNode.isAssignmentExpression() = this.elementType == PhpParserElementTypes.ASSIGNMENT_EXPRESSION
fun ASTNode.isFieldReference() = this.elementType == PhpParserElementTypes.FIELD_REFERENCE
fun ASTNode.isString() = this.elementType == PhpParserElementTypes.STRING
fun ASTNode.isArrayCreationExpression() = this.elementType == PhpParserElementTypes.ARRAY_CREATION_EXPRESSION
fun ASTNode.isFunctionCall() = this.elementType == PhpParserElementTypes.FUNCTION_CALL
fun ASTNode.isModifierList() = this.elementType == PhpParserElementTypes.MODIFIER_LIST
fun ASTNode.isArrayAccessExpression() = this.elementType == PhpParserElementTypes.ARRAY_ACCESS_EXPRESSION
