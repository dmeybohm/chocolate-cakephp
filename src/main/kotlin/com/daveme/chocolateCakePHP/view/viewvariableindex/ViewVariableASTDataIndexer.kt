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
    
    // Extract string literal from AST node (borrowed from ViewFileDataIndexer)
    private fun extractStringLiteral(node: ASTNode): String? {
        val strNode = node.takeIf { it.elementType == PhpElementTypes.STRING } ?: node
        val lit = strNode.findChildByType(PhpTokenTypes.STRING_LITERAL)
        val text = (lit ?: strNode).text
        return text.removeSurrounding("'").removeSurrounding("\"")
    }
}