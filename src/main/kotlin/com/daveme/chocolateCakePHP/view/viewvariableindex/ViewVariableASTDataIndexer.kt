package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.cake.controllerPathFromControllerFile
import com.daveme.chocolateCakePHP.cake.isCakeControllerFile
import com.daveme.chocolateCakePHP.isCustomizableViewMethod
import com.intellij.lang.ASTNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.parser.PhpElementTypes

// Data structures for AST-level parsing
data class SetCallInfo(
    val variableName: String,
    val variableType: String,
    val offset: Int
)

data class MethodDeclarationInfo(
    val name: String,
    val isPublic: Boolean,
    val offset: Int,
    val astNode: ASTNode  // Keep reference to AST node for further processing
)

object ViewVariableASTDataIndexer : DataIndexer<ViewVariablesKey, ViewVariables, FileContent> {

    private const val FALLBACK_VIEW_VARIABLE_TYPE = "mixed"

    override fun map(inputData: FileContent): MutableMap<String, ViewVariables> {
        val result = mutableMapOf<String, ViewVariables>()
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
        result: MutableMap<String, ViewVariables>,
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
            val variables = ViewVariables()
            
            // Find all $this->set() calls within this method using AST
            val setCallsList = findSetCallsInMethod(method.astNode)
            
            setCallsList.forEach { setCall ->
                variables[setCall.variableName] = ViewVariableValue(
                    setCall.variableType,
                    setCall.offset
                )
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
    private fun parseSetCall(node: ASTNode): SetCallInfo? {
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
                    return SetCallInfo(
                        variableName = firstParamValue,
                        variableType = FALLBACK_VIEW_VARIABLE_TYPE,
                        offset = node.startOffset
                    )
                }
            }
            // Case 2: $this->set(['name' => $value])
            else if (firstParamNode.elementType == PhpElementTypes.ARRAY_CREATION_EXPRESSION) {
                val arrayVariables = extractVariablesFromArrayCreation(firstParamNode)
                // Return the first variable for now (we'll need to handle multiple variables later)
                if (arrayVariables.isNotEmpty()) {
                    return arrayVariables.first()
                }
            }
        }
        
        return null
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
    // For now, implement a simplified version that handles basic array structure
    private fun extractVariablesFromArrayCreation(arrayNode: ASTNode): List<SetCallInfo> {
        val variables = mutableListOf<SetCallInfo>()
        
        // Simple implementation for case 2 - just return one dummy variable for now
        // TODO: Properly parse hash elements when we understand the correct AST structure
        variables.add(SetCallInfo(
            variableName = "arrayVariable", 
            variableType = FALLBACK_VIEW_VARIABLE_TYPE,
            offset = arrayNode.startOffset
        ))
        
        return variables
    }
    
    // Extract string literal from AST node (borrowed from ViewFileDataIndexer)
    private fun extractStringLiteral(node: ASTNode): String? {
        val strNode = node.takeIf { it.elementType == PhpElementTypes.STRING } ?: node
        val lit = strNode.findChildByType(PhpTokenTypes.STRING_LITERAL)
        val text = (lit ?: strNode).text
        return text.removeSurrounding("'").removeSurrounding("\"")
    }
}