package com.daveme.chocolateCakePHP.view.viewfileindex

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeControllerFile
import com.daveme.chocolateCakePHP.isCustomizableViewMethod
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.parser.PhpElementTypes
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import org.jetbrains.annotations.Unmodifiable

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
        
        // Recursively check child nodes
        for (child in node.getChildren(null)) {
            findMethodCallsRecursive(child, targetMethodName, result)
        }
    }
    
    private fun isMethodReference(node: ASTNode): Boolean {
        return node.elementType == PhpElementTypes.METHOD_REFERENCE
    }
    
    private fun parseMethodCall(node: ASTNode, targetMethodName: String): MethodCallInfo? {
        val children = node.getChildren(null).toList()
        
        // Look for VARIABLE, arrow, identifier, parameter list pattern
        var receiverName: String? = null
        var methodName: String? = null
        var parameterValue: String? = null
        
        // Parse structure based on AST: VARIABLE -> arrow -> identifier -> (...)
        for (child in children) {
            when (child.elementType) {
                PhpElementTypes.VARIABLE -> {
                    receiverName = child.text.removePrefix("$")
                }
                PhpTokenTypes.IDENTIFIER -> {
                    methodName = child.text
                }
                PhpElementTypes.PARAMETER_LIST -> {
                    // Only accept if there's exactly one parameter (ignoring whitespace/commas)
                    val childNodes = child.getChildren(null).toList()
                    val significantChildren = childNodes.filter { 
                        it.elementType != PhpTokenTypes.WHITE_SPACE && it.elementType != PhpTokenTypes.opCOMMA
                    }
                    
                    // Only process if there's exactly one significant child and it's a String
                    if (significantChildren.size == 1 && significantChildren[0].elementType == PhpElementTypes.STRING) {
                        parameterValue = significantChildren[0].text.removeSurrounding("'").removeSurrounding("\"")
                    }
                }
            }
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
        
        // Recursively check child nodes
        for (child in node.getChildren(null)) {
            findMethodDeclarationsRecursive(child, result)
        }
    }
    
    private fun isMethodDeclaration(node: ASTNode): Boolean {
        return node.elementType == PhpElementTypes.CLASS_METHOD
    }
    
    private fun parseMethodDeclaration(node: ASTNode): MethodInfo? {
        val children = node.getChildren(null).toList()
        
        var visibility = "public" // default
        var methodName: String? = null
        
        // Parse structure: Modifier list, function keyword, identifier
        for (child in children) {
            when (child.elementType) {
                PhpElementTypes.MODIFIER_LIST -> {
                    val modifierText = child.text.trim()
                    if (modifierText in listOf("private", "protected", "public")) {
                        visibility = modifierText
                    }
                }
                PhpTokenTypes.IDENTIFIER -> {
                    methodName = child.text
                }
            }
        }
        
        return if (methodName != null) {
            MethodInfo(
                name = methodName,
                isPublic = visibility == "public",
                offset = node.startOffset
            )
        } else null
    }
    
    // Helper to create a compatible MethodReference-like object from AST data
    private fun astCallToMethodReference(astCall: MethodCallInfo, psiFile: PsiFile): MethodReference {
        // Find the actual PSI element at the offset for compatibility
        val element = psiFile.findElementAt(astCall.offset)
        val methodRef = PsiTreeUtil.getParentOfType(element, MethodReference::class.java)
        return methodRef ?: throw IllegalStateException("Could not find MethodReference at offset ${astCall.offset}")
    }
    
    // Helper to create a compatible Method-like object from AST data
    private fun astMethodToMethod(astMethod: MethodInfo, psiFile: PsiFile): Method {
        // Find the actual PSI element at the offset for compatibility
        val element = psiFile.findElementAt(astMethod.offset)
        val method = PsiTreeUtil.getParentOfType(element, Method::class.java)
        return method ?: throw IllegalStateException("Could not find Method at offset ${astMethod.offset}")
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
        val rootNode = psiFile.node
        val astRenderCalls = findMethodCallsByName(rootNode, "render")
            .filter { it.receiverText == "this" }
        val astElementCalls = findMethodCallsByName(rootNode, "element")
            .filter { it.receiverText == "this" }
        
        // Convert AST results to compatible format for existing logic
        val renderCalls = astRenderCalls.map { astCallToMethodReference(it, psiFile) }
        val elementCalls = astElementCalls.map { astCallToMethodReference(it, psiFile) }

        val isController = isCakeControllerFile(psiFile)
        if (
            renderCalls.isEmpty() &&
            elementCalls.isEmpty() &&
            !isController
        ) {
            return result
        }

        indexRenderCalls(result, projectDir, renderCalls, virtualFile)
        indexElementCalls(result, projectDir, elementCalls, virtualFile)

        if (isController) {
            // Use AST traversal instead of PSI for method declarations
            val astMethods = findMethodDeclarations(rootNode)
                .filter { it.isPublic }
            
            // Convert AST results to compatible format for existing logic  
            val methods = astMethods.map { astMethodToMethod(it, psiFile) }
            indexImplicitRender(result, projectDir, settings, methods, virtualFile)
        }

        return result
    }

    private fun indexRenderCalls(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        renderCalls: List<MethodReference>,
        virtualFile: VirtualFile
    ) {
        val withThis = filterRenderOrElementCalls(renderCalls)
        if (withThis.isEmpty()) {
            return
        }

        val viewPathPrefix = viewPathPrefixFromSourceFile(projectDir, virtualFile)
            ?: return

        setViewPath(withThis, viewPathPrefix, result)
    }

    private fun indexElementCalls(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        elementCalls: List<MethodReference>,
        virtualFile: VirtualFile
    ) {
        val withThis = filterRenderOrElementCalls(elementCalls)
        if (withThis.isEmpty()) {
            return
        }

        val viewPathPrefix = elementPathPrefixFromSourceFile(projectDir, virtualFile)
            ?: return

        setViewPath(withThis, viewPathPrefix, result)
    }

    private fun filterRenderOrElementCalls(methodCalls: List<MethodReference>): List<MethodReference> {
        val withThis = methodCalls.filter { method ->
            val variable = method.firstChild as? Variable ?: return@filter false
            variable.name == "this" &&
                    method.parameters.isNotEmpty() &&
                    method.parameters.first() is StringLiteralExpression
        }
        return withThis
    }

    private fun indexImplicitRender(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        settings: Settings,
        methods: @Unmodifiable Collection<Method>,
        controllerFile: VirtualFile
    ) {
        // todo check for $this->autoRender = false
        val relevantMethods = methods.filter { it.isCustomizableViewMethod() }
        if (relevantMethods.isEmpty()) {
            return
        }

        val viewPathPrefix = viewPathPrefixFromSourceFile(projectDir, controllerFile)
            ?: return

        val controllerInfo = lookupControllerFileInfo(controllerFile, settings)

        relevantMethods.forEach { method ->
            val fullViewPath = fullImplicitViewPath(
                viewPathPrefix,
                controllerInfo,
                method.name
            )
            val oldList = result.getOrDefault(fullViewPath, emptyList())
            val newViewReferenceData = ViewReferenceData(
                methodName = method.name,
                elementType = "Method",
                offset = method.textOffset
            )
            val newList = oldList + listOf(newViewReferenceData)
            result[fullViewPath] = newList
        }
    }

    private fun setViewPath(
        withThis: List<MethodReference>,
        viewPathPrefix: ViewPathPrefix,
        result: MutableMap<String, List<ViewReferenceData>>
    ) {
        for (method in withThis) {
            val parameterName = method.parameters.first() as StringLiteralExpression
            val content = RenderPath(parameterName.contents)

            if (content.path.isEmpty()) {
                continue
            }
            val fullViewPath = fullExplicitViewPath(
                viewPathPrefix,
                content
            )
            val oldList = result.getOrDefault(fullViewPath, emptyList())
            val newViewReferenceData = ViewReferenceData(
                methodName = method.name ?: "render",
                elementType = "MethodReference",
                offset = method.textOffset
            )
            val newList = oldList + listOf(newViewReferenceData)
            result[fullViewPath] = newList
        }
    }

}
