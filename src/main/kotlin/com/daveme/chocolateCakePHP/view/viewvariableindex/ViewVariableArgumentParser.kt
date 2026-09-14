package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.*
import com.intellij.lang.ASTNode

/**
 * AST-only parser for the "view variable data" argument shapes that several CakePHP
 * calls share:
 *
 *   $this->set(['name' => $value])            ARRAY
 *   $this->set(compact('name'))               COMPACT
 *   $this->set($vars)                         VARIABLE_ARRAY  (resolved later via PSI)
 *   $this->element('x', ['name' => $value])   ARRAY
 *   $this->element('x', compact('name'))      COMPACT
 *   $this->element('x', $vars)                VARIABLE_ARRAY  (resolved later via PSI)
 *
 * Only syntax facts and offsets are recorded, never types: the returned [RawViewVar]s are
 * resolved lazily with PSI in [RawViewVar.resolveType] once a consumer needs them.
 */
object ViewVariableArgumentParser {

    /**
     * Parse one argument node that is expected to hold view variables.
     */
    fun parseDataArgument(node: ASTNode): List<RawViewVar> {
        return when {
            node.isArrayCreationExpression() -> parseArrayCreation(node)
            node.isCompactCall() -> parseCompactCall(node)
            node.isVariable() -> parseVariableIndirection(node)
            else -> emptyList()
        }
    }

    /** `['name' => $value, 'title' => $pageTitle]` -> one ARRAY entry per string-keyed element. */
    internal fun parseArrayCreation(arrayNode: ASTNode): List<RawViewVar> {
        val variables = mutableListOf<RawViewVar>()
        var child = arrayNode.firstChildNode
        while (child != null) {
            if (child.isHashArrayElement()) {
                val key = hashElementKey(child)
                if (key != null) {
                    val valueNode = hashElementValueNode(child)
                    val handle = if (valueNode != null) {
                        valueHandle(valueNode)
                    } else {
                        VarHandle(SourceKind.UNKNOWN, "unknown_array_value", child.startOffset)
                    }
                    variables.add(RawViewVar(key, VarKind.ARRAY, child.startOffset, handle))
                }
            }
            child = child.treeNext
        }
        return variables
    }

    /** `compact('foo', 'bar')` -> COMPACT entries; each name is also the local symbol to resolve. */
    internal fun parseCompactCall(compactNode: ASTNode): List<RawViewVar> {
        val paramList = firstChildWhere(compactNode) { it.isParameterList() } ?: return emptyList()
        return paramList.parameterNodes().mapNotNull { paramNode ->
            val name = paramNode.stringLiteralValue() ?: return@mapNotNull null
            RawViewVar(
                variableName = name,
                varKind = VarKind.COMPACT,
                offset = paramNode.startOffset,
                varHandle = VarHandle(SourceKind.LOCAL, name, paramNode.startOffset)
            )
        }
    }

    /**
     * `$vars` where `$vars = [...]` or `$vars = compact(...)` earlier in the scope. Recorded as
     * VARIABLE_ARRAY; the lookup side inspects the assignment to find the real variable names.
     */
    internal fun parseVariableIndirection(variableNode: ASTNode): List<RawViewVar> {
        val name = variableNode.text.removePrefix("$")
        return listOf(
            RawViewVar(
                variableName = name,
                varKind = VarKind.VARIABLE_ARRAY,
                offset = variableNode.startOffset,
                varHandle = VarHandle(SourceKind.LOCAL, name, variableNode.startOffset)
            )
        )
    }

    /** Describe where a value expression comes from, for later PSI resolution. */
    internal fun valueHandle(valueNode: ASTNode): VarHandle {
        val sourceKind = analyzeValueSource(valueNode)
        val symbolName = when (sourceKind) {
            SourceKind.LOCAL -> valueNode.text.removePrefix("$")
            SourceKind.LITERAL -> valueNode.text.removeSurrounding("'").removeSurrounding("\"")
            else -> valueNode.text
        }
        return VarHandle(sourceKind, symbolName, valueNode.startOffset)
    }

    internal fun analyzeValueSource(valueNode: ASTNode): SourceKind {
        return when {
            // $foo: could be a parameter or a local; resolveByHandle checks both
            valueNode.isVariable() -> SourceKind.LOCAL
            valueNode.isString() -> SourceKind.LITERAL
            valueNode.isMethodReference() -> SourceKind.EXPRESSION
            valueNode.isFunctionCall() -> SourceKind.EXPRESSION
            valueNode.isFieldReference() -> SourceKind.EXPRESSION
            valueNode.isArrayAccessExpression() -> SourceKind.EXPRESSION
            valueNode.text.matches(Regex("\\d+")) -> SourceKind.LITERAL
            else -> SourceKind.UNKNOWN
        }
    }

    /** The string key of a `'key' => value` hash element, or null when the key is not a literal. */
    internal fun hashElementKey(hashElement: ASTNode): String? =
        firstChildWhere(hashElement) { it.isArrayKey() }?.stringLiteralValue()

    /** The value expression node of a `'key' => value` hash element. */
    internal fun hashElementValueNode(hashElement: ASTNode): ASTNode? =
        firstChildWhere(hashElement) { it.isArrayValue() }?.firstChildNode

    private fun firstChildWhere(node: ASTNode, predicate: (ASTNode) -> Boolean): ASTNode? {
        var child = node.firstChildNode
        while (child != null) {
            if (predicate(child)) {
                return child
            }
            child = child.treeNext
        }
        return null
    }
}
