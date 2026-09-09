package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.controllerPathFromControllerFile
import com.daveme.chocolateCakePHP.cake.isCakeControllerFile
import com.intellij.lang.ASTNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.lexer.PhpTokenTypes

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
            findSetCallsInMethod(method.astNode).forEach { rawVar ->
                variables[rawVar.variableName] = rawVar
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
        if (node.isClassMethod()) {
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
            when {
                child.isModifierList() -> {
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
                child.elementType == PhpTokenTypes.IDENTIFIER -> methodName = child.text
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
    private fun findSetCallsInMethod(methodNode: ASTNode): List<RawViewVar> =
        methodNode.collectMethodCalls { it.isThisCall("set") }.flatMap { parseSetCall(it) }

    /**
     * All syntactic forms of `$this->set(...)`:
     *
     *   set('name', $value)                  PAIR
     *   set(['name' => $value])              ARRAY          (shared parser)
     *   set(compact('name'))                 COMPACT        (shared parser)
     *   set($vars)                           VARIABLE_ARRAY (shared parser)
     *   set(['n1', 'n2'], [$v1, $v2])        TUPLE
     *   set($keys, $vals) / mixed            MIXED_TUPLE
     *
     * The single-argument forms are exactly the "data argument" shapes that `element()` also
     * accepts, so they go through [ViewVariableArgumentParser]; the two-argument forms are
     * specific to `set()` and stay here.
     */
    internal fun parseSetCall(call: MethodCallParts): List<RawViewVar> {
        val params = call.parameters
        return when (params.size) {
            1 -> ViewVariableArgumentParser.parseDataArgument(params[0], allowVariableIndirection = true)
            2 -> parseTwoArgumentSet(call.node, params[0], params[1])
            else -> emptyList()
        }
    }

    private fun parseTwoArgumentSet(callNode: ASTNode, keys: ASTNode, values: ASTNode): List<RawViewVar> {
        // Case 1: $this->set('name', $value)
        val name = keys.stringLiteralValue()
        if (name != null) {
            return listOf(
                RawViewVar(
                    variableName = name,
                    varKind = VarKind.PAIR,
                    offset = callNode.startOffset,
                    varHandle = ViewVariableArgumentParser.valueHandle(values)
                )
            )
        }
        // Case 4: $this->set(['name1', 'name2'], [$val1, $val2])
        if (keys.isArrayCreationExpression() && values.isArrayCreationExpression()) {
            return extractVariablesFromTupleAssignment(keys, values)
        }
        // Case 7: $this->set($keysVar, $valsVar) where either side may be a variable
        if ((keys.isArrayCreationExpression() && values.isVariable()) ||
            (keys.isVariable() && values.isArrayCreationExpression()) ||
            (keys.isVariable() && values.isVariable())
        ) {
            return extractVariablesFromMixedTupleAssignment(keys, values)
        }
        return emptyList()
    }

    // Extract variables from tuple assignment: $this->set(['n1', 'n2'], [$v1, $v2])
    private fun extractVariablesFromTupleAssignment(keysArray: ASTNode, valuesArray: ASTNode): List<RawViewVar> {
        val variables = mutableListOf<RawViewVar>()
        
        // Extract string literals from keys array
        val keyNames = mutableListOf<String>()
        var keyChild = keysArray.firstChildNode
        while (keyChild != null) {
            if (keyChild.isArrayValue()) {
                val valueChild = keyChild.firstChildNode
                if (valueChild != null) {
                    val keyName = valueChild.stringLiteralValue()
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
            if (valueChild.isArrayValue()) {
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
                variables.add(RawViewVar(
                    variableName = keyNames[i],
                    varKind = VarKind.TUPLE,
                    offset = keysArray.startOffset,
                    varHandle = ViewVariableArgumentParser.valueHandle(valueNodes[i])
                ))
            }
        }
        
        return variables
    }
    
    // Extract variables from mixed tuple assignment cases like:
    // Case 7: $this->set($caseSevenKeys, $caseSevenVals) where either keys or vals is a variable
    private fun extractVariablesFromMixedTupleAssignment(keysParam: ASTNode, valsParam: ASTNode): List<RawViewVar> {
        // For case 7, we need to store information about both parameters and let later resolution
        // figure out what variables are actually created. This is similar to variable indirection
        // but with two parameters that need to be paired up.
        
        val keysVariableName = if (keysParam.isVariable()) {
            keysParam.text.removePrefix("$")
        } else {
            null
        }

        val valsVariableName = if (valsParam.isVariable()) {
            valsParam.text.removePrefix("$")
        } else {
            null
        }
        
        // Create a placeholder entry that indicates this is a mixed tuple assignment
        // The actual variable names will be resolved later when we can access PSI to find assignments
        val combinedName = "${keysVariableName ?: "array"}_${valsVariableName ?: "array"}_mixed_tuple"
        
        return listOf(RawViewVar(
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
}
