package com.daveme.chocolateCakePHP.configuredmemberindex

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableArgumentParser
import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Indexes the calls and property arrays that configure the dynamic properties of a class,
 * keyed by the FQN of the class they appear in.
 */
object ConfiguredMemberDataIndexer : DataIndexer<String, List<MemberLoad>, FileContent> {

    private val textNeedles = listOf(
        "load", "addHelper", "belongsTo", "hasOne", "hasMany", "addAssociations", "helpers"
    )

    override fun map(inputData: FileContent): Map<String, List<MemberLoad>> {
        val text = inputData.contentAsText
        if (textNeedles.none { text.contains(it, ignoreCase = true) }) {
            return emptyMap()
        }
        val result = mutableMapOf<String, MutableList<MemberLoad>>()
        for (phpClass in PsiTreeUtil.findChildrenOfType(inputData.psiFile, PhpClass::class.java)) {
            val loads = mutableListOf<MemberLoad>()
            readPropertyArrays(phpClass, loads)
            collectMethodCallLoads(phpClass.node, null, loads)
            if (loads.isNotEmpty()) {
                result.getOrPut(phpClass.fqn) { mutableListOf() } += loads
            }
        }
        return result
    }

    private fun readPropertyArrays(phpClass: PhpClass, out: MutableList<MemberLoad>) {
        for ((fieldName, kind) in propertyArraySources) {
            val defaultValue = phpClass.findOwnFieldByName(fieldName, false)?.defaultValue?.node ?: continue
            if (defaultValue.isArrayCreationExpression()) {
                readLoadArray(defaultValue, kind, LoadSource.PROPERTY_ARRAY, null, out)
            }
        }
    }

    private fun collectMethodCallLoads(node: ASTNode, methodName: String?, out: MutableList<MemberLoad>) {
        var child = node.firstChildNode
        while (child != null) {
            when {
                // Nested classes get their own key:
                child.psi is PhpClass -> {}
                child.isClassMethod() -> collectMethodCallLoads(child, declaredMethodName(child), out)
                else -> {
                    val call = child.readMethodCall()
                    if (call != null) {
                        readMethodCall(call, methodName, out)
                    }
                    collectMethodCallLoads(child, methodName, out)
                }
            }
            child = child.treeNext
        }
    }

    private fun declaredMethodName(methodNode: ASTNode): String? =
        methodNode.findChildByType(PhpTokenTypes.IDENTIFIER)?.text

    private fun readMethodCall(call: MethodCallParts, methodName: String?, out: MutableList<MemberLoad>) {
        if (call.receiverName != "this") {
            return
        }
        val calledName = call.methodName?.lowercase() ?: return
        val firstParam = call.parameters.getOrNull(0) ?: return
        if (calledName == ADD_ASSOCIATIONS_METHOD) {
            readAddAssociations(firstParam, methodName, out)
            return
        }
        val kind = methodCallSources[calledName] ?: return
        val loadedName = literalString(firstParam) ?: return
        out += memberLoad(kind, LoadSource.METHOD_CALL, loadedName, call.parameters.getOrNull(1),
            methodName, firstParam.startOffset)
    }

    private fun readAddAssociations(arrayNode: ASTNode, methodName: String?, out: MutableList<MemberLoad>) {
        if (!arrayNode.isArrayCreationExpression()) {
            return
        }
        var child = arrayNode.firstChildNode
        while (child != null) {
            if (child.isHashArrayElement()) {
                val key = ViewVariableArgumentParser.hashElementKey(child)
                val value = ViewVariableArgumentParser.hashElementValueNode(child)
                if (key != null && value != null && value.isArrayCreationExpression() &&
                    associationTypeKeys.any { it.equals(key, ignoreCase = true) }
                ) {
                    readLoadArray(value, MemberKind.ASSOCIATION, LoadSource.METHOD_CALL, methodName, out)
                }
            }
            child = child.treeNext
        }
    }

    /**
     * Reads an array of loads such as `['Html', 'Fmt' => ['className' => 'MovieFormatter']]`:
     * a string value is a plain load, a string key with an array value is a load with options.
     */
    private fun readLoadArray(
        arrayNode: ASTNode,
        kind: MemberKind,
        source: LoadSource,
        methodName: String?,
        out: MutableList<MemberLoad>
    ) {
        var child = arrayNode.firstChildNode
        while (child != null) {
            if (child.isArrayValue()) {
                val loadedName = child.firstChildNode?.let { literalString(it) }
                if (loadedName != null) {
                    out += memberLoad(kind, source, loadedName, null, methodName, child.startOffset)
                }
            } else if (child.isHashArrayElement()) {
                val loadedName = ViewVariableArgumentParser.hashElementKey(child)
                val options = ViewVariableArgumentParser.hashElementValueNode(child)
                if (loadedName != null && loadedName.isNotEmpty() &&
                    options != null && options.isArrayCreationExpression()
                ) {
                    out += memberLoad(kind, source, loadedName, options, methodName, child.startOffset)
                }
            }
            child = child.treeNext
        }
    }

    private fun memberLoad(
        kind: MemberKind,
        source: LoadSource,
        loadedName: String,
        options: ASTNode?,
        methodName: String?,
        offset: Int
    ) = MemberLoad(
        kind = kind,
        source = source,
        alias = loadedName.substringAfterLast('.'),
        loadedName = loadedName,
        className = options?.let { classNameOption(it) },
        methodName = methodName,
        offset = offset,
    )

    private fun classNameOption(options: ASTNode): String? {
        if (!options.isArrayCreationExpression()) {
            return null
        }
        var child = options.firstChildNode
        while (child != null) {
            if (child.isHashArrayElement() && ViewVariableArgumentParser.hashElementKey(child) == "className") {
                val value = ViewVariableArgumentParser.hashElementValueNode(child) ?: return null
                // A string is kept as written; it is not necessarily an FQN.
                return literalString(value)
                    ?: (value.psi as? ClassConstantReference)?.getStaticClassRefOrNull()
            }
            child = child.treeNext
        }
        return null
    }

    private fun literalString(node: ASTNode): String? =
        if (node.isString()) node.stringLiteralValue()?.takeIf { it.isNotEmpty() } else null
}
