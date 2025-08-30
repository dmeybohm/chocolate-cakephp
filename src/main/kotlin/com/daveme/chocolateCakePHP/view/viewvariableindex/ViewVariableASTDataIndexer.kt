package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.cake.controllerPathFromControllerFile
import com.daveme.chocolateCakePHP.cake.isCakeControllerFile
import com.intellij.lang.ASTNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.parser.PhpElementTypes

// Simplified data structure for AST-level parsing - only syntax facts
data class SetCallInfo(
    val variableName: String,
    val varKind: VarKind,
    val offset: Int,
    val varHandle: VarHandle
)

data class MethodDeclarationInfo(
    val name: String,
    val isPublic: Boolean,
    val offset: Int,
    val astNode: ASTNode  // Keep reference to AST node for further processing
)

object ViewVariableASTDataIndexer : DataIndexer<ViewVariablesKey, ViewVariablesWithRawVars, FileContent> {

    override fun map(inputData: FileContent): MutableMap<String, ViewVariablesWithRawVars> {
        val result = mutableMapOf<String, ViewVariablesWithRawVars>()
        val psiFile = inputData.psiFile

        val virtualFile = psiFile.virtualFile
        if (virtualFile.nameWithoutExtension.endsWith("Test")) {
            return result
        }

        if (isCakeControllerFile(psiFile)) {
            indexController(result, psiFile, virtualFile)
        }

        return result
    }

    private fun indexController(
        result: MutableMap<String, ViewVariablesWithRawVars>,
        psiFile: PsiFile,
        virtualFile: VirtualFile
    ) {
        val controllerPath = controllerPathFromControllerFile(virtualFile) ?: return
        val rootNode = psiFile.node ?: return
        
        // Find all public methods that can render views using AST
        val publicMethods = findMethodDeclarations(rootNode)
            .filter { it.isPublic && isCustomizableViewMethodAST(it.name) }
        
        if (publicMethods.isEmpty()) {
            return
        }

        publicMethods.forEach { method ->
            val variables = ViewVariablesWithRawVars()
            
            // Find all $this->set() calls within this method using AST
            val setCallsList = findSetCallsInMethod(method.astNode)
            
            setCallsList.forEach { setCall ->
                val rawVar = RawViewVar(
                    variableName = setCall.variableName,
                    varKind = setCall.varKind,
                    offset = setCall.offset,
                    varHandle = setCall.varHandle
                )
                variables[setCall.variableName] = rawVar
            }
            
            val filenameAndMethodKey = controllerMethodKey(controllerPath, method.name)
            result[filenameAndMethodKey] = variables
        }
    }

    // AST-based method to check if method is customizable (equivalent to isCustomizableViewMethod())
    private fun isCustomizableViewMethodAST(methodName: String): Boolean {
        // Replicate the logic from isCustomizableViewMethod() extension function
        val lowerMethodName = methodName.lowercase()
        return !setOf(
            "beforefilter", "beforerender", "afterfilter", "initialize",
            "implementedevents", "constructclasses", "invokeaction",
            "startupprocess", "shutdownprocess", "redirect", "setaction",
            "render", "viewclasses", "paginate", "isaction", "loadcomponent",
            "setrequest"
        ).contains(lowerMethodName)
    }

    private fun findMethodDeclarations(node: ASTNode): List<MethodDeclarationInfo> {
        val result = mutableListOf<MethodDeclarationInfo>()
        findMethodDeclarationsRecursive(node, result)
        return result
    }
    
    private fun findMethodDeclarationsRecursive(node: ASTNode, result: MutableList<MethodDeclarationInfo>) {
        if (node.elementType == PhpElementTypes.CLASS_METHOD) {
            val methodInfo = parseMethodDeclaration(node)
            if (methodInfo != null) {
                result.add(methodInfo)
            }
        }
        
        var child = node.firstChildNode
        while (child != null) {
            findMethodDeclarationsRecursive(child, result)
            child = child.treeNext
        }
    }
    
    private fun parseMethodDeclaration(node: ASTNode): MethodDeclarationInfo? {
        var isPublic = true  // PHP default visibility is public if unspecified
        var methodName: String? = null

        var child = node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                PhpElementTypes.MODIFIER_LIST -> {
                    var m = child.firstChildNode
                    while (m != null) {
                        when (m.elementType) {
                            PhpTokenTypes.kwPUBLIC -> isPublic = true
                            PhpTokenTypes.kwPRIVATE,
                            PhpTokenTypes.kwPROTECTED -> isPublic = false
                        }
                        m = m.treeNext
                    }
                }
                PhpTokenTypes.IDENTIFIER -> methodName = child.text
            }
            child = child.treeNext
        }

        return methodName?.let {
            MethodDeclarationInfo(
                name = it,
                isPublic = isPublic,
                offset = node.startOffset,
                astNode = node
            )
        }
    }

    // Find $this->set() calls within a specific method node
    private fun findSetCallsInMethod(methodNode: ASTNode): List<SetCallInfo> {
        val result = mutableListOf<SetCallInfo>()
        findSetCallsRecursive(methodNode, result)
        return result
    }
    
    private fun findSetCallsRecursive(node: ASTNode, result: MutableList<SetCallInfo>) {
        // Check if this is a method reference that could be $this->set(...)
        if (node.elementType == PhpElementTypes.METHOD_REFERENCE) {
            val setCalls = parseSetCalls(node) // Note: now returns a list
            result.addAll(setCalls)
        }
        
        var child = node.firstChildNode
        while (child != null) {
            findSetCallsRecursive(child, result)
            child = child.treeNext
        }
    }
    
    // Parse a method reference node to extract set call information
    // This implements case 1: $this->set('name', $value) and case 2: $this->set(['name' => $value])
    // Returns a list because case 2 can have multiple variables
    private fun parseSetCalls(node: ASTNode): List<SetCallInfo> {
        var receiverName: String? = null
        var methodName: String? = null
        var firstParamNode: ASTNode? = null
        var hasSecondParam = false
        
        var child = node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                PhpElementTypes.VARIABLE -> {
                    receiverName = child.text.removePrefix("$")
                }
                PhpTokenTypes.IDENTIFIER -> {
                    methodName = child.text
                }
                PhpElementTypes.PARAMETER_LIST -> {
                    val paramNodes = extractParameterNodes(child)
                    if (paramNodes.size == 2) {
                        firstParamNode = paramNodes[0]
                        hasSecondParam = true
                    } else if (paramNodes.size == 1) {
                        firstParamNode = paramNodes[0]
                        hasSecondParam = false
                    }
                }
            }
            child = child.treeNext
        }
        
        if (receiverName == "this" && 
            methodName?.equals("set", ignoreCase = true) == true && 
            firstParamNode != null) {
            
            // Case 1: $this->set('name', $value)
            if (hasSecondParam) {
                val firstParamValue = extractStringLiteral(firstParamNode)
                if (firstParamValue != null) {
                    // Find parameter list to get the second parameter (the variable name)
                    var paramList: ASTNode? = null
                    var paramChild = node.firstChildNode
                    while (paramChild != null) {
                        if (paramChild.elementType == PhpElementTypes.PARAMETER_LIST) {
                            paramList = paramChild
                            break
                        }
                        paramChild = paramChild.treeNext
                    }
                    
                    val paramNodes = paramList?.let { extractParameterNodes(it) } ?: emptyList()
                    val secondParamText = if (paramNodes.size >= 2) paramNodes[1].text else "unknownVar"
                    
                    // Analyze the second parameter to determine SourceKind
                    val sourceKind = analyzeValueSource(paramNodes[1])
                    val symbolName = secondParamText.removePrefix("$")
                    
                    return listOf(SetCallInfo(
                        variableName = firstParamValue,
                        varKind = VarKind.PAIR,
                        offset = node.startOffset,
                        varHandle = VarHandle(sourceKind, symbolName, paramNodes[1].startOffset)
                    ))
                }
            }
            // Case 2: $this->set(['name' => $value])
            else if (firstParamNode.elementType == PhpElementTypes.ARRAY_CREATION_EXPRESSION) {
                return extractVariablesFromArrayCreation(firstParamNode)
            }
            // Case 3: $this->set(compact('value'))
            else if (isCompactFunctionCall(firstParamNode)) {
                return extractVariablesFromCompactCall(firstParamNode)
            }
            // Case 5: $this->set($var) where $var = compact('name')
            // Case 6: $this->set($var) where $var = ['key' => 'val']
            else if (firstParamNode.elementType == PhpElementTypes.VARIABLE) {
                return extractVariablesFromVariableIndirection(firstParamNode, node)
            }
        }
        
        // Case 4: $this->set(['name1', 'name2'], [$val1, $val2]) - tuple assignment
        if (receiverName == "this" && 
            methodName?.equals("set", ignoreCase = true) == true && 
            hasSecondParam && 
            firstParamNode?.elementType == PhpElementTypes.ARRAY_CREATION_EXPRESSION) {
            
            val paramNodes = extractParameterNodes(
                node.findChildByType(PhpElementTypes.PARAMETER_LIST) ?: return emptyList()
            )
            if (paramNodes.size == 2 && paramNodes[1].elementType == PhpElementTypes.ARRAY_CREATION_EXPRESSION) {
                return extractVariablesFromTupleAssignment(paramNodes[0], paramNodes[1])
            }
        }
        
        // Case 7: $this->set($caseSevenKeys, $caseSevenVals) where either keys or vals is a variable
        if (receiverName == "this" && 
            methodName?.equals("set", ignoreCase = true) == true && 
            hasSecondParam) {
            
            val paramNodes = extractParameterNodes(
                node.findChildByType(PhpElementTypes.PARAMETER_LIST) ?: return emptyList()
            )
            if (paramNodes.size == 2) {
                val keysParam = paramNodes[0]
                val valsParam = paramNodes[1]
                
                // Handle mixed cases where one param is array and other is variable
                if ((keysParam.elementType == PhpElementTypes.ARRAY_CREATION_EXPRESSION && valsParam.elementType == PhpElementTypes.VARIABLE) ||
                    (keysParam.elementType == PhpElementTypes.VARIABLE && valsParam.elementType == PhpElementTypes.ARRAY_CREATION_EXPRESSION) ||
                    (keysParam.elementType == PhpElementTypes.VARIABLE && valsParam.elementType == PhpElementTypes.VARIABLE)) {
                    
                    return extractVariablesFromMixedTupleAssignment(keysParam, valsParam)
                }
            }
        }
        
        return emptyList()
    }
    
    // Extract parameter nodes from a parameter list (returns actual AST nodes, not just strings)
    private fun extractParameterNodes(paramListNode: ASTNode): List<ASTNode> {
        val paramNodes = mutableListOf<ASTNode>()
        
        var child = paramListNode.firstChildNode
        while (child != null) {
            if (child.elementType != TokenType.WHITE_SPACE && 
                child.elementType != PhpTokenTypes.opCOMMA) {
                paramNodes.add(child)
            }
            child = child.treeNext
        }
        
        return paramNodes
    }
    
    // Extract variables from array creation expression: ['name' => $value, 'title' => $pageTitle]
    // Parses hash array elements to extract key-value pairs
    private fun extractVariablesFromArrayCreation(arrayNode: ASTNode): List<SetCallInfo> {
        val variables = mutableListOf<SetCallInfo>()
        
        // Find all hash array elements within the array creation expression
        var child = arrayNode.firstChildNode
        while (child != null) {
            if (child.elementType.toString() == "Hash array element") {
                val keyValuePair = parseHashArrayElement(child)
                if (keyValuePair != null) {
                    // For array case, we need to find the value part of the hash element
                    val valueHandle = extractArrayValueHandle(child)
                    variables.add(SetCallInfo(
                        variableName = keyValuePair,
                        varKind = VarKind.ARRAY,
                        offset = child.startOffset,
                        varHandle = valueHandle
                    ))
                }
            }
            child = child.treeNext
        }
        
        return variables
    }
    
    // Parse a single hash array element: 'key' => $value
    // Returns the key string if it's a string literal, null otherwise
    private fun parseHashArrayElement(hashElement: ASTNode): String? {
        var keyNode: ASTNode? = null
        
        // Find the Array key child node
        var child = hashElement.firstChildNode
        while (child != null) {
            if (child.elementType.toString() == "Array key") {
                keyNode = child
                break
            }
            child = child.treeNext
        }
        
        // Extract string literal from the key node
        return keyNode?.let { extractStringLiteral(it) }
    }
    
    // Extract VarHandle from the value part of a hash array element
    private fun extractArrayValueHandle(hashElement: ASTNode): VarHandle {
        var valueNode: ASTNode? = null
        
        // Find the Array value child node
        var child = hashElement.firstChildNode
        while (child != null) {
            if (child.elementType.toString() == "Array value") {
                // Get the actual value node (first child of Array value)
                valueNode = child.firstChildNode
                break
            }
            child = child.treeNext
        }
        
        return if (valueNode != null) {
            val sourceKind = analyzeValueSource(valueNode)
            val symbolName = when (sourceKind) {
                SourceKind.LOCAL -> valueNode.text.removePrefix("$")
                SourceKind.LITERAL -> valueNode.text.removeSurrounding("'").removeSurrounding("\"")
                else -> valueNode.text
            }
            VarHandle(sourceKind, symbolName, valueNode.startOffset)
        } else {
            // Fallback for unknown array values
            VarHandle(SourceKind.UNKNOWN, "unknown_array_value", hashElement.startOffset)
        }
    }
    
    // Analyze an AST node to determine what kind of value source it represents
    private fun analyzeValueSource(valueNode: ASTNode): SourceKind {
        return when (valueNode.elementType) {
            PhpElementTypes.VARIABLE -> {
                // $foo - could be PARAM, LOCAL, or UNKNOWN
                // For now, we'll mark as LOCAL and let resolveByHandle figure it out
                SourceKind.LOCAL
            }
            PhpElementTypes.STRING -> SourceKind.LITERAL
            PhpElementTypes.METHOD_REFERENCE -> SourceKind.CALL
            PhpElementTypes.FIELD_REFERENCE -> {
                // $this->foo
                SourceKind.PROPERTY
            }
            else -> {
                // Check for numeric literals
                if (valueNode.text.matches(Regex("\\d+"))) {
                    SourceKind.LITERAL
                } else {
                    SourceKind.UNKNOWN
                }
            }
        }
    }
    
    // Check if a node is a compact() function call
    // For now, we'll do a simple text-based check and improve later
    private fun isCompactFunctionCall(node: ASTNode): Boolean {
        val nodeText = node.text.trim()
        return nodeText.startsWith("compact(", ignoreCase = true) || 
               nodeText.contains("compact(", ignoreCase = true)
    }
    
    // Extract variables from compact() function call: compact('foo', 'bar') -> [foo, bar]
    private fun extractVariablesFromCompactCall(compactNode: ASTNode): List<SetCallInfo> {
        val variables = mutableListOf<SetCallInfo>()
        
        // Find parameter list in compact() call
        var paramList: ASTNode? = null
        var child = compactNode.firstChildNode
        while (child != null) {
            if (child.elementType == PhpElementTypes.PARAMETER_LIST) {
                paramList = child
                break
            }
            child = child.treeNext
        }
        
        paramList?.let { paramListNode ->
            val paramNodes = extractParameterNodes(paramListNode)
            paramNodes.forEach { paramNode ->
                val variableName = extractStringLiteral(paramNode)
                if (variableName != null) {
                    // For compact, the variable name in the string becomes both the key and the symbol to resolve
                    val varHandle = VarHandle(
                        sourceKind = SourceKind.LOCAL, // compact() references local variables
                        symbolName = variableName,
                        offset = paramNode.startOffset
                    )
                    variables.add(SetCallInfo(
                        variableName = variableName,
                        varKind = VarKind.COMPACT,
                        offset = paramNode.startOffset,
                        varHandle = varHandle
                    ))
                }
            }
        }
        
        return variables
    }
    
    // Extract variables from tuple assignment: $this->set(['n1', 'n2'], [$v1, $v2])
    private fun extractVariablesFromTupleAssignment(keysArray: ASTNode, valuesArray: ASTNode): List<SetCallInfo> {
        val variables = mutableListOf<SetCallInfo>()
        
        // Extract string literals from keys array
        val keyNames = mutableListOf<String>()
        var keyChild = keysArray.firstChildNode
        while (keyChild != null) {
            if (keyChild.elementType.toString() == "Array value") {
                val valueChild = keyChild.firstChildNode
                if (valueChild != null) {
                    val keyName = extractStringLiteral(valueChild)
                    if (keyName != null) {
                        keyNames.add(keyName)
                    }
                }
            }
            keyChild = keyChild.treeNext
        }
        
        // Extract value nodes from values array
        val valueNodes = mutableListOf<ASTNode>()
        var valueChild = valuesArray.firstChildNode
        while (valueChild != null) {
            if (valueChild.elementType.toString() == "Array value") {
                val actualValue = valueChild.firstChildNode
                if (actualValue != null) {
                    valueNodes.add(actualValue)
                }
            }
            valueChild = valueChild.treeNext
        }
        
        // Pair up keys and values
        for (i in keyNames.indices) {
            if (i < valueNodes.size) {
                val keyName = keyNames[i]
                val valueNode = valueNodes[i]
                val sourceKind = analyzeValueSource(valueNode)
                val symbolName = when (sourceKind) {
                    SourceKind.LOCAL -> valueNode.text.removePrefix("$")
                    SourceKind.LITERAL -> valueNode.text.removeSurrounding("'").removeSurrounding("\"")
                    else -> valueNode.text
                }
                
                variables.add(SetCallInfo(
                    variableName = keyName,
                    varKind = VarKind.TUPLE,
                    offset = keysArray.startOffset,
                    varHandle = VarHandle(sourceKind, symbolName, valueNode.startOffset)
                ))
            }
        }
        
        return variables
    }
    
    // Extract variables from variable indirection cases like:
    // Case 5: $this->set($var) where $var = compact('name') 
    // Case 6: $this->set($var) where $var = ['key' => 'val']
    private fun extractVariablesFromVariableIndirection(variableNode: ASTNode, @Suppress("UNUSED_PARAMETER") setCallNode: ASTNode): List<SetCallInfo> {
        val variableName = variableNode.text.removePrefix("$")
        
        // Store the variable name as-is. The actual resolution of what variables this creates
        // will be handled later using PSI to find the last assignment or check if it's a parameter
        return listOf(SetCallInfo(
            variableName = variableName, // Store the actual variable name being referenced
            varKind = VarKind.VARIABLE_ARRAY, // Default assumption - could be VARIABLE_COMPACT too
            offset = variableNode.startOffset,
            varHandle = VarHandle(
                sourceKind = SourceKind.LOCAL,
                symbolName = variableName,
                offset = variableNode.startOffset
            )
        ))
    }
    
    // Extract variables from mixed tuple assignment cases like:
    // Case 7: $this->set($caseSevenKeys, $caseSevenVals) where either keys or vals is a variable
    private fun extractVariablesFromMixedTupleAssignment(keysParam: ASTNode, valsParam: ASTNode): List<SetCallInfo> {
        // For case 7, we need to store information about both parameters and let later resolution
        // figure out what variables are actually created. This is similar to variable indirection
        // but with two parameters that need to be paired up.
        
        val keysVariableName = if (keysParam.elementType == PhpElementTypes.VARIABLE) {
            keysParam.text.removePrefix("$")
        } else {
            null
        }
        
        val valsVariableName = if (valsParam.elementType == PhpElementTypes.VARIABLE) {
            valsParam.text.removePrefix("$")
        } else {
            null
        }
        
        // Create a placeholder entry that indicates this is a mixed tuple assignment
        // The actual variable names will be resolved later when we can access PSI to find assignments
        val combinedName = "${keysVariableName ?: "array"}_${valsVariableName ?: "array"}_mixed_tuple"
        
        return listOf(SetCallInfo(
            variableName = combinedName,
            varKind = VarKind.MIXED_TUPLE, // New kind for this case
            offset = keysParam.startOffset,
            varHandle = VarHandle(
                sourceKind = SourceKind.MIXED_ASSIGNMENT,
                symbolName = "${keysVariableName ?: ""}|${valsVariableName ?: ""}",
                offset = keysParam.startOffset
            )
        ))
    }
    
    // Extract string literal from AST node (borrowed from ViewFileDataIndexer)
    private fun extractStringLiteral(node: ASTNode): String? {
        val strNode = node.takeIf { it.elementType == PhpElementTypes.STRING } ?: node
        val lit = strNode.findChildByType(PhpTokenTypes.STRING_LITERAL)
        val text = (lit ?: strNode).text
        return text.removeSurrounding("'").removeSurrounding("\"")
    }
}