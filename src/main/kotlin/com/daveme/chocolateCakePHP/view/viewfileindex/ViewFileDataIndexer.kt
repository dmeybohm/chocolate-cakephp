package com.daveme.chocolateCakePHP.view.viewfileindex

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeControllerFile
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.parser.PhpElementTypes

// Methods that should not trigger implicit view rendering
private val cakeSkipRenderingMethods : HashSet<String> = listOf(
    "beforefilter",
    "beforerender",
    "afterfilter",
    "initialize",
    "implementedEvents",
    "constructclasses",
    "invokeaction",
    "startupprocess",
    "shutdownprocess",
    "redirect",
    "setaction",
    "render",
    "viewclasses",
    "paginate",
    "isaction",
    "loadcomponent",
    "setrequest",
).map { it.lowercase() }.toHashSet()

// Data structures for AST-level parsing
data class MethodCallInfo(
    val methodName: String,
    val receiverText: String?,
    val firstParameterText: String?,
    val offset: Int
)

data class MethodInfo(
    val name: String,
    val isPublic: Boolean,
    val offset: Int
)


object ViewFileDataIndexer : DataIndexer<String, List<ViewReferenceData>, FileContent> {

    // Robust string literal extraction that handles different PHP plugin versions
    private fun extractStringLiteral(node: ASTNode): String? {
        // Accept either STRING wrapper or direct STRING_LITERAL token
        val strNode = node.takeIf { it.elementType == PhpElementTypes.STRING } ?: node
        
        // Try child token
        val lit = strNode.findChildByType(PhpTokenTypes.STRING_LITERAL)
        val text = (lit ?: strNode).text
        // Strip quotes if present
        return text.removeSurrounding("'").removeSurrounding("\"")
    }

    // AST-based parsing implementation (tested and proven)
    private fun findMethodCallsByName(node: ASTNode, methodName: String): List<MethodCallInfo> {
        val result = mutableListOf<MethodCallInfo>()
        findMethodCallsRecursive(node, methodName, result)
        return result
    }
    
    private fun findMethodCallsRecursive(node: ASTNode, targetMethodName: String, result: MutableList<MethodCallInfo>) {
        // Check if this node represents a method reference
        if (isMethodReference(node)) {
            val methodCall = parseMethodCall(node, targetMethodName)
            if (methodCall != null) {
                result.add(methodCall)
            }
        }
        
        // Recursively check child nodes - avoid toList() allocation
        var child = node.firstChildNode
        while (child != null) {
            findMethodCallsRecursive(child, targetMethodName, result)
            child = child.treeNext
        }
    }
    
    private fun isMethodReference(node: ASTNode): Boolean {
        return node.elementType == PhpElementTypes.METHOD_REFERENCE
    }
    
    private fun parseMethodCall(node: ASTNode, targetMethodName: String): MethodCallInfo? {
        // Look for VARIABLE, arrow, identifier, parameter list pattern
        var receiverName: String? = null
        var methodName: String? = null
        var parameterValue: String? = null
        
        // Parse structure based on AST: VARIABLE -> arrow -> identifier -> (...) - avoid toList()
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
                    // Only accept if there's exactly one parameter (ignoring whitespace/commas)
                    val significantChildren = mutableListOf<ASTNode>()
                    var paramChild = child.firstChildNode
                    while (paramChild != null) {
                        if (paramChild.elementType != TokenType.WHITE_SPACE && paramChild.elementType != PhpTokenTypes.opCOMMA) {
                            significantChildren.add(paramChild)
                        }
                        paramChild = paramChild.treeNext
                    }
                    
                    // Only process if there's exactly one significant child
                    if (significantChildren.size == 1) {
                        parameterValue = extractStringLiteral(significantChildren[0])
                    }
                }
            }
            child = child.treeNext
        }
        
        // Only return if method name matches target and we have all required parts
        if (methodName?.equals(targetMethodName, ignoreCase = true) == true && 
            receiverName != null && parameterValue != null) {
            return MethodCallInfo(
                methodName = methodName,
                receiverText = receiverName,
                firstParameterText = parameterValue,
                offset = node.startOffset
            )
        }
        
        return null
    }

    private fun findMethodDeclarations(node: ASTNode): List<MethodInfo> {
        val result = mutableListOf<MethodInfo>()
        findMethodDeclarationsRecursive(node, result)
        return result
    }
    
    private fun findMethodDeclarationsRecursive(node: ASTNode, result: MutableList<MethodInfo>) {
        // Check if this node represents a method declaration
        if (isMethodDeclaration(node)) {
            val methodInfo = parseMethodDeclaration(node)
            if (methodInfo != null) {
                result.add(methodInfo)
            }
        }
        
        // Recursively check child nodes - avoid toList() allocation
        var child = node.firstChildNode
        while (child != null) {
            findMethodDeclarationsRecursive(child, result)
            child = child.treeNext
        }
    }
    
    private fun isMethodDeclaration(node: ASTNode): Boolean {
        return node.elementType == PhpElementTypes.CLASS_METHOD
    }
    
    private fun parseMethodDeclaration(node: ASTNode): MethodInfo? {
        var isPublic = true  // PHP default visibility is public if unspecified
        var methodName: String? = null

        var child = node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                PhpElementTypes.MODIFIER_LIST -> {
                    var m = child.firstChildNode
                    while (m != null) {
                        when (m.elementType) {
                            PhpTokenTypes.kwPUBLIC    -> isPublic = true
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
            MethodInfo(name = it, isPublic = isPublic, offset = node.startOffset)
        }
    }
    

    override fun map(inputData: FileContent): MutableMap<String, List<ViewReferenceData>> {
        val result = mutableMapOf<String, List<ViewReferenceData>>()
        val psiFile = inputData.psiFile
        val project = psiFile.project
        val projectDir = project.guessProjectDir() ?: return result
        val settings = Settings.getInstance(project)

        if (!settings.enabled) {
            return result
        }

        val virtualFile = psiFile.virtualFile
        if (virtualFile.nameWithoutExtension.endsWith("Test")) {
            return result
        }

        // Use AST traversal instead of PSI for method calls
        val rootNode = psiFile.node ?: return result
        val astRenderCalls = findMethodCallsByName(rootNode, "render")
            .filter { it.receiverText == "this" && it.firstParameterText != null }
        val astElementCalls = findMethodCallsByName(rootNode, "element")
            .filter { it.receiverText == "this" && it.firstParameterText != null }

        val isController = isCakeControllerFile(virtualFile)
        if (
            astRenderCalls.isEmpty() &&
            astElementCalls.isEmpty() &&
            !isController
        ) {
            return result
        }

        indexRenderCalls(result, projectDir, astRenderCalls, virtualFile)
        indexElementCalls(result, projectDir, astElementCalls, virtualFile)

        if (isController) {
            // Use AST traversal instead of PSI for method declarations
            val astMethods = findMethodDeclarations(rootNode)
                .filter { it.isPublic && !cakeSkipRenderingMethods.contains(it.name.lowercase()) }
            
            indexImplicitRender(result, projectDir, settings, astMethods, virtualFile)
        }

        return result
    }

    private fun indexRenderCalls(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        renderCalls: List<MethodCallInfo>,
        virtualFile: VirtualFile
    ) {
        if (renderCalls.isEmpty()) {
            return
        }

        val viewPathPrefix = viewPathPrefixFromSourceFile(projectDir, virtualFile)
            ?: return

        setViewPath(renderCalls, viewPathPrefix, result)
    }

    private fun indexElementCalls(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        elementCalls: List<MethodCallInfo>,
        virtualFile: VirtualFile
    ) {
        if (elementCalls.isEmpty()) {
            return
        }

        val viewPathPrefix = elementPathPrefixFromSourceFile(projectDir, virtualFile)
            ?: return

        setViewPath(elementCalls, viewPathPrefix, result)
    }


    private fun indexImplicitRender(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        settings: Settings,
        methods: List<MethodInfo>,
        controllerFile: VirtualFile
    ) {
        // todo check for $this->autoRender = false
        if (methods.isEmpty()) {
            return
        }

        val viewPathPrefix = viewPathPrefixFromSourceFile(projectDir, controllerFile)
            ?: return

        val controllerInfo = lookupControllerFileInfo(controllerFile, settings)

        methods.forEach { method ->
            val fullViewPath = fullImplicitViewPath(
                viewPathPrefix,
                controllerInfo,
                method.name
            )
            val oldList = result.getOrDefault(fullViewPath, emptyList())
            val newViewReferenceData = ViewReferenceData(
                methodName = method.name,
                elementType = ElementType.METHOD,
                offset = method.offset
            )
            val newList = oldList + listOf(newViewReferenceData)
            result[fullViewPath] = newList
        }
    }

    private fun setViewPath(
        methodCalls: List<MethodCallInfo>,
        viewPathPrefix: ViewPathPrefix,
        result: MutableMap<String, List<ViewReferenceData>>
    ) {
        for (methodCall in methodCalls) {
            val parameterText = methodCall.firstParameterText ?: continue
            val content = RenderPath(parameterText)

            if (content.path.isEmpty()) {
                continue
            }
            val fullViewPath = fullExplicitViewPath(
                viewPathPrefix,
                content
            )
            val oldList = result.getOrDefault(fullViewPath, emptyList())
            val newViewReferenceData = ViewReferenceData(
                methodName = methodCall.methodName,
                elementType = ElementType.METHOD_REFERENCE,
                offset = methodCall.offset
            )
            val newList = oldList + listOf(newViewReferenceData)
            result[fullViewPath] = newList
        }
    }

}
