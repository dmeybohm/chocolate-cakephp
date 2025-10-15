package com.daveme.chocolateCakePHP.view.viewfileindex

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeControllerFile
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.thisLogger
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

data class FieldAssignmentInfo(
    val fieldName: String,
    val receiverText: String?,
    val assignedValue: String?,
    val offset: Int
)

data class ViewBuilderCallInfo(
    val methodName: String,        // "setTemplate" or "setTemplatePath"
    val parameterValue: String?,   // The template name or path
    val offset: Int,
    val containingMethodStartOffset: Int  // Offset of containing CLASS_METHOD node
)


object ViewFileDataIndexer : DataIndexer<String, List<ViewReferenceData>, FileContent> {
    val logger = this.thisLogger()

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

    private fun findFieldAssignments(node: ASTNode, fieldName: String): List<FieldAssignmentInfo> {
        val result = mutableListOf<FieldAssignmentInfo>()
        findFieldAssignmentsRecursive(node, fieldName, result)
        return result
    }

    private fun findFieldAssignmentsRecursive(node: ASTNode, targetFieldName: String, result: MutableList<FieldAssignmentInfo>) {
        // Check if this node represents an assignment expression
        if (node.elementType == PhpElementTypes.ASSIGNMENT_EXPRESSION) {
            val fieldAssignment = parseFieldAssignment(node, targetFieldName)
            if (fieldAssignment != null) {
                result.add(fieldAssignment)
            }
        }

        // Recursively check child nodes
        var child = node.firstChildNode
        while (child != null) {
            findFieldAssignmentsRecursive(child, targetFieldName, result)
            child = child.treeNext
        }
    }

    private fun parseFieldAssignment(node: ASTNode, targetFieldName: String): FieldAssignmentInfo? {
        // Look for: $this->view = 'template_name'
        // AST structure: ASSIGNMENT_EXPRESSION -> FIELD_REFERENCE (left) -> STRING (right)
        var fieldReference: ASTNode? = null
        var assignedValue: String? = null

        var child = node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                PhpElementTypes.FIELD_REFERENCE -> {
                    fieldReference = child
                }
                PhpElementTypes.STRING -> {
                    assignedValue = extractStringLiteral(child)
                }
            }
            child = child.treeNext
        }

        // Parse the field reference to ensure it's $this->view
        if (fieldReference != null && assignedValue != null) {
            var receiverName: String? = null
            var fieldName: String? = null

            var refChild = fieldReference.firstChildNode
            while (refChild != null) {
                when (refChild.elementType) {
                    PhpElementTypes.VARIABLE -> {
                        receiverName = refChild.text.removePrefix("$")
                    }
                    PhpTokenTypes.IDENTIFIER -> {
                        fieldName = refChild.text
                    }
                }
                refChild = refChild.treeNext
            }

            if (receiverName == "this" && fieldName?.equals(targetFieldName, ignoreCase = true) == true) {
                return FieldAssignmentInfo(
                    fieldName = fieldName,
                    receiverText = receiverName,
                    assignedValue = assignedValue,
                    offset = fieldReference.startOffset
                )
            }
        }

        return null
    }

    private fun findViewBuilderCalls(node: ASTNode): List<ViewBuilderCallInfo> {
        val result = mutableListOf<ViewBuilderCallInfo>()
        findViewBuilderCallsRecursive(node, result)
        return result
    }

    private fun findViewBuilderCallsRecursive(node: ASTNode, result: MutableList<ViewBuilderCallInfo>) {
        // Check if this node represents a method reference
        if (node.elementType == PhpElementTypes.METHOD_REFERENCE) {
            val viewBuilderCall = parseViewBuilderCall(node)
            if (viewBuilderCall != null) {
                result.add(viewBuilderCall)
            }
        }

        // Recursively check child nodes
        var child = node.firstChildNode
        while (child != null) {
            findViewBuilderCallsRecursive(child, result)
            child = child.treeNext
        }
    }

    private fun parseViewBuilderCall(node: ASTNode): ViewBuilderCallInfo? {
        // Look for: $this->viewBuilder()->setTemplate('name')
        // or: $this->viewBuilder()->setTemplatePath('path')
        // AST structure: METHOD_REFERENCE (setTemplate/setTemplatePath)
        //   -> receiver: METHOD_REFERENCE (viewBuilder)
        //       -> receiver: VARIABLE ($this)

        var methodName: String? = null
        var parameterValue: String? = null
        var receiverMethodRef: ASTNode? = null

        // Parse the outer method reference (setTemplate or setTemplatePath)
        var child = node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                PhpElementTypes.METHOD_REFERENCE -> {
                    receiverMethodRef = child
                }
                PhpTokenTypes.IDENTIFIER -> {
                    methodName = child.text
                }
                PhpElementTypes.PARAMETER_LIST -> {
                    // Extract single string parameter
                    val significantChildren = mutableListOf<ASTNode>()
                    var paramChild = child.firstChildNode
                    while (paramChild != null) {
                        if (paramChild.elementType != TokenType.WHITE_SPACE && paramChild.elementType != PhpTokenTypes.opCOMMA) {
                            significantChildren.add(paramChild)
                        }
                        paramChild = paramChild.treeNext
                    }
                    if (significantChildren.size == 1) {
                        parameterValue = extractStringLiteral(significantChildren[0])
                    }
                }
            }
            child = child.treeNext
        }

        // Check if method name is setTemplate or setTemplatePath
        if (methodName == null || (methodName != "setTemplate" && methodName != "setTemplatePath")) {
            return null
        }

        // Check if the receiver is $this->viewBuilder()
        if (receiverMethodRef != null && isViewBuilderMethodCall(receiverMethodRef)) {
            // Find containing method
            val containingMethod = findContainingMethod(node)
            val methodStartOffset = containingMethod?.startOffset ?: -1

            return ViewBuilderCallInfo(
                methodName = methodName,
                parameterValue = parameterValue,
                offset = node.startOffset,
                containingMethodStartOffset = methodStartOffset
            )
        }

        return null
    }

    private fun isViewBuilderMethodCall(node: ASTNode): Boolean {
        // Check if this is a METHOD_REFERENCE with name "viewBuilder" and receiver "$this"
        var receiverVariable: String? = null
        var methodName: String? = null

        var child = node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                PhpElementTypes.VARIABLE -> {
                    receiverVariable = child.text.removePrefix("$")
                }
                PhpTokenTypes.IDENTIFIER -> {
                    methodName = child.text
                }
            }
            child = child.treeNext
        }

        return receiverVariable == "this" && methodName == "viewBuilder"
    }

    private fun findContainingMethod(node: ASTNode): ASTNode? {
        var current = node.treeParent
        while (current != null) {
            if (current.elementType == PhpElementTypes.CLASS_METHOD) {
                return current
            }
            current = current.treeParent
        }
        return null
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
        val astViewFieldAssignments = findFieldAssignments(rootNode, "view")
            .filter { it.receiverText == "this" && it.assignedValue != null }
        val astViewBuilderCalls = findViewBuilderCalls(rootNode)
            .filter { it.parameterValue != null }

        val isController = isCakeControllerFile(virtualFile)
        if (
            astRenderCalls.isEmpty() &&
            astElementCalls.isEmpty() &&
            astViewFieldAssignments.isEmpty() &&
            astViewBuilderCalls.isEmpty() &&
            !isController
        ) {
            return result
        }

        indexRenderCalls(result, projectDir, astRenderCalls, virtualFile)
        indexElementCalls(result, projectDir, astElementCalls, virtualFile)
        indexViewFieldAssignments(result, projectDir, astViewFieldAssignments, virtualFile)
        indexViewBuilderCalls(result, projectDir, astViewBuilderCalls, virtualFile)

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

    private fun indexViewFieldAssignments(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        fieldAssignments: List<FieldAssignmentInfo>,
        virtualFile: VirtualFile
    ) {
        if (fieldAssignments.isEmpty()) {
            return
        }

        val viewPathPrefix = viewPathPrefixFromSourceFile(projectDir, virtualFile)
            ?: return

        for (assignment in fieldAssignments) {
            val assignedValue = assignment.assignedValue ?: continue
            val renderPath = RenderPath(assignedValue)

            if (renderPath.path.isEmpty()) {
                continue
            }

            val fullViewPath = fullExplicitViewPath(viewPathPrefix, renderPath)
            val oldList = result.getOrDefault(fullViewPath, emptyList())
            val newViewReferenceData = ViewReferenceData(
                methodName = assignment.fieldName,
                elementType = ElementType.FIELD_ASSIGNMENT,
                offset = assignment.offset
            )
            val newList = oldList + listOf(newViewReferenceData)
            result[fullViewPath] = newList
        }
    }

    private fun indexViewBuilderCalls(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        builderCalls: List<ViewBuilderCallInfo>,
        virtualFile: VirtualFile
    ) {
        if (builderCalls.isEmpty()) {
            return
        }

        val viewPathPrefix = viewPathPrefixFromSourceFile(projectDir, virtualFile)
            ?: return

        // Group by containing method
        val callsByMethod = builderCalls.groupBy { it.containingMethodStartOffset }

        for ((_, calls) in callsByMethod) {
            // Sort by offset to maintain order
            val sortedCalls = calls.sortedBy { it.offset }

            // Track the most recent setTemplatePath
            var currentTemplatePath: String? = null

            for (call in sortedCalls) {
                val parameterValue = call.parameterValue ?: continue

                when (call.methodName) {
                    "setTemplatePath" -> {
                        // Update the current template path for subsequent setTemplate calls
                        currentTemplatePath = parameterValue
                        // Note: We don't index setTemplatePath calls directly
                    }
                    "setTemplate" -> {
                        // Build the final path combining setTemplatePath (if any) with setTemplate
                        logger.error("XXX: currentTemplatePath = ${currentTemplatePath}")
                        val finalPath = if (currentTemplatePath != null) {
                            // setTemplatePath provides an absolute path from templates root
                            // Prefix with "/" to make it absolute so it's not combined with controller path
                            "/$currentTemplatePath/$parameterValue"
                        } else {
                            parameterValue
                        }

                        val renderPath = RenderPath(finalPath)
                        if (renderPath.path.isEmpty()) {
                            continue
                        }

                        val fullViewPath = fullExplicitViewPath(viewPathPrefix, renderPath)
                        val oldList = result.getOrDefault(fullViewPath, emptyList())
                        val newViewReferenceData = ViewReferenceData(
                            methodName = call.methodName,
                            elementType = ElementType.VIEW_BUILDER,
                            offset = call.offset
                        )
                        val newList = oldList + listOf(newViewReferenceData)
                        result[fullViewPath] = newList
                    }
                }
            }
        }
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
