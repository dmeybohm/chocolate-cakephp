package com.daveme.chocolateCakePHP.configuredmemberindex

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableArgumentParser
import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.parser.PhpElementTypes
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Indexes the calls and property arrays that configure the dynamic properties of a class,
 * keyed by the FQN of the class they appear in.
 */
object ConfiguredMemberDataIndexer : DataIndexer<String, ClassConfiguration, FileContent> {

    private val textNeedles = listOf(
        "load", "addHelper", "belongsTo", "hasOne", "hasMany", "addAssociations", "helpers",
        "components", "uses", "bindModel",
    )

    override fun map(inputData: FileContent): Map<String, ClassConfiguration> {
        val text = inputData.contentAsText
        if (textNeedles.none { text.contains(it, ignoreCase = true) }) {
            return emptyMap()
        }
        val loadsByClass = mutableMapOf<String, MutableList<MemberLoad>>()
        val declarationsByClass = mutableMapOf<String, MutableList<PropertyDeclaration>>()
        for (phpClass in PsiTreeUtil.findChildrenOfType(inputData.psiFile, PhpClass::class.java)) {
            val loads = mutableListOf<MemberLoad>()
            val declarations = mutableListOf<PropertyDeclaration>()
            readPropertyArrays(phpClass, loads, declarations)
            collectMethodCallLoads(phpClass.node, null, loads)
            if (loads.isNotEmpty() || declarations.isNotEmpty()) {
                loadsByClass.getOrPut(phpClass.fqn) { mutableListOf() } += loads
                declarationsByClass.getOrPut(phpClass.fqn) { mutableListOf() } += declarations
            }
        }
        return loadsByClass.mapValues { (fqn, loads) ->
            ClassConfiguration(loads, declarationsByClass[fqn].orEmpty())
        }
    }

    private fun readPropertyArrays(
        phpClass: PhpClass,
        out: MutableList<MemberLoad>,
        declarations: MutableList<PropertyDeclaration>
    ) {
        for ((fieldName, kind) in propertyArraySources) {
            val field = phpClass.findOwnFieldByName(fieldName, false) ?: continue
            val defaultValue = field.defaultValue?.node
            val value = declaredValue(defaultValue)
            val reader = LoadReader(kind, LoadSource.PROPERTY_ARRAY, null, fieldName, out)
            val isComplete = when {
                defaultValue == null -> true
                value == DeclaredValue.ARRAY -> reader.readArray(defaultValue)
                value == DeclaredValue.STRING && kind == MemberKind.ASSOCIATION ->
                    reader.readCommaSeparated(defaultValue)
                value == DeclaredValue.STRING || value == DeclaredValue.OTHER -> false
                else -> true
            }
            declarations += PropertyDeclaration(
                property = fieldName,
                kind = kind,
                value = value,
                isComplete = isComplete,
                offset = (defaultValue ?: field.node).startOffset,
            )
        }
    }

    private fun declaredValue(node: ASTNode?): DeclaredValue {
        if (node == null) {
            return DeclaredValue.NULL
        }
        if (node.isArrayCreationExpression()) {
            return DeclaredValue.ARRAY
        }
        if (node.isString()) {
            return if (node.stringLiteralValue() != null) DeclaredValue.STRING else DeclaredValue.OTHER
        }
        if (node.elementType == PhpElementTypes.CONSTANT_REF) {
            return when (node.text.lowercase()) {
                "true" -> DeclaredValue.TRUE
                "false" -> DeclaredValue.FALSE
                "null" -> DeclaredValue.NULL
                else -> DeclaredValue.OTHER
            }
        }
        return DeclaredValue.OTHER
    }

    private fun collectMethodCallLoads(node: ASTNode, methodName: String?, out: MutableList<MemberLoad>) {
        var child = node.firstChildNode
        while (child != null) {
            when {
                // Nested classes get their own key:
                child.elementType == PhpElementTypes.CLASS -> {}
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
            readAssociationsByType(firstParam, associationTypeKeys, LoadSource.METHOD_CALL, methodName, out)
            return
        }
        if (calledName == BIND_MODEL_METHOD) {
            readAssociationsByType(firstParam, cakeTwoAssociationTypeKeys, LoadSource.BIND_MODEL, methodName, out)
            return
        }
        val kind = methodCallSources[calledName] ?: return
        val loadedName = literalString(firstParam) ?: return
        val options = if (calledName in sourcesWithoutOptions) null else call.parameters.getOrNull(1)
        out += LoadReader(kind, LoadSource.METHOD_CALL, methodName, null, out)
            .memberLoad(loadedName, options, firstParam.startOffset)
    }

    /**
     * Reads associations keyed by their type, such as `['hasMany' => ['Comments']]`.
     */
    private fun readAssociationsByType(
        arrayNode: ASTNode,
        typeKeys: Set<String>,
        source: LoadSource,
        methodName: String?,
        out: MutableList<MemberLoad>
    ) {
        if (!arrayNode.isArrayCreationExpression()) {
            return
        }
        var child = arrayNode.firstChildNode
        while (child != null) {
            if (child.isHashArrayElement()) {
                val key = ViewVariableArgumentParser.hashElementKey(child)
                val value = ViewVariableArgumentParser.hashElementValueNode(child)
                val typeKey = typeKeys.firstOrNull { it.equals(key, ignoreCase = true) }
                if (typeKey != null && value != null && value.isArrayCreationExpression()) {
                    // bindModel() adds to the model's association property of the same name:
                    val property = if (source == LoadSource.BIND_MODEL) typeKey else null
                    LoadReader(MemberKind.ASSOCIATION, source, methodName, property, out).readArray(value)
                }
            }
            child = child.treeNext
        }
    }

    /**
     * Reads the loads of one configuring property or method call into [out].
     */
    private class LoadReader(
        val kind: MemberKind,
        val source: LoadSource,
        val methodName: String?,
        val property: String?,
        val out: MutableList<MemberLoad>,
    ) {

        /**
         * Reads an array of loads such as `['Html', 'Fmt' => ['className' => 'MovieFormatter']]`:
         * a string value is a plain load, a string key with an array value is a load with
         * options. Returns whether every entry could be read.
         */
        fun readArray(arrayNode: ASTNode): Boolean {
            var isComplete = true
            var child = arrayNode.firstChildNode
            while (child != null) {
                if (child.isArrayValue()) {
                    val loadedName = child.firstChildNode?.let { literalString(it) }
                    if (loadedName != null) {
                        out += memberLoad(loadedName, null, child.startOffset)
                    } else {
                        isComplete = false
                    }
                } else if (child.isHashArrayElement()) {
                    val loadedName = ViewVariableArgumentParser.hashElementKey(child)
                    val options = ViewVariableArgumentParser.hashElementValueNode(child)
                    if (loadedName != null && loadedName.isNotEmpty() &&
                        options != null && options.isArrayCreationExpression()
                    ) {
                        out += memberLoad(loadedName, options, child.startOffset)
                        if (property == HABTM_PROPERTY && !readJoinModel(options)) {
                            isComplete = false
                        }
                    } else {
                        isComplete = false
                    }
                }
                child = child.treeNext
            }
            return isComplete
        }

        /**
         * Reads a comma-separated string of plain loads, such as `'User, Category'`.
         */
        fun readCommaSeparated(stringNode: ASTNode): Boolean {
            val value = stringNode.stringLiteralValue() ?: return false
            value.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { out += memberLoad(it, null, stringNode.startOffset) }
            return true
        }

        // The join model a `with` option names is lazy-loaded as a property too:
        private fun readJoinModel(options: ASTNode): Boolean {
            val with = optionValue(options, "with") ?: return true
            val loadedName = literalString(with)
                ?: firstStringKey(with)
                ?: return false
            out += memberLoad(loadedName, null, with.startOffset)
            return true
        }

        private fun firstStringKey(arrayNode: ASTNode): String? {
            if (!arrayNode.isArrayCreationExpression()) {
                return null
            }
            var child = arrayNode.firstChildNode
            while (child != null) {
                if (child.isHashArrayElement()) {
                    return ViewVariableArgumentParser.hashElementKey(child)?.takeIf { it.isNotEmpty() }
                }
                if (child.isArrayValue()) {
                    return null
                }
                child = child.treeNext
            }
            return null
        }

        fun memberLoad(loadedName: String, options: ASTNode?, offset: Int): MemberLoad {
            val classNameNode = options?.let { optionValue(it, "className") }
            // A string is kept as written; it is not necessarily an FQN.
            val className = classNameNode?.let {
                literalString(it) ?: (it.psi as? ClassConstantReference)?.getStaticClassRefOrNull()
            }
            return MemberLoad(
                kind = kind,
                source = source,
                alias = loadedName.substringAfterLast('.'),
                loadedName = loadedName,
                className = className,
                methodName = methodName,
                offset = offset,
                property = property,
                hasUnreadClassName = classNameNode != null && className == null,
            )
        }
    }
}

private fun optionValue(options: ASTNode, key: String): ASTNode? {
    if (!options.isArrayCreationExpression()) {
        return null
    }
    var child = options.firstChildNode
    while (child != null) {
        if (child.isHashArrayElement() && ViewVariableArgumentParser.hashElementKey(child) == key) {
            return ViewVariableArgumentParser.hashElementValueNode(child)
        }
        child = child.treeNext
    }
    return null
}

private fun literalString(node: ASTNode): String? =
    if (node.isString()) node.stringLiteralValue()?.takeIf { it.isNotEmpty() } else null
