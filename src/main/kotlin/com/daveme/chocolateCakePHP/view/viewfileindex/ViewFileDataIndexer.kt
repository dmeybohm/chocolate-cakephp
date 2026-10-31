package com.daveme.chocolateCakePHP.view.viewfileindex

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeControllerFile
import com.daveme.chocolateCakePHP.cake.cellDirNameFromSourceFile
import com.daveme.chocolateCakePHP.cake.isCakeCellFile
import com.daveme.chocolateCakePHP.cake.parseCellName
import com.daveme.chocolateCakePHP.cake.TemplatesDir
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.view.TemplateDirStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.daveme.chocolateCakePHP.*
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.php.lang.lexer.PhpTokenTypes

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
    val firstParameterValues: List<String>,  // All literal values the first parameter can take
    val offset: Int,
    val secondParameterValues: List<String> = emptyList()
)

data class MethodInfo(
    val name: String,
    val isPublic: Boolean,
    val offset: Int
)

data class FieldAssignmentInfo(
    val fieldName: String,
    val receiverText: String?,
    val assignedValues: List<String>,  // All literal values the assigned expression can take
    val offset: Int
)

data class ViewBuilderCallInfo(
    val methodName: String,            // "setTemplate" or "setTemplatePath"
    val parameterValues: List<String>, // All literal template names or paths the parameter can take
    val offset: Int,
    val containingMethodStartOffset: Int  // Offset of containing CLASS_METHOD node
)

data class CellReferenceInfo(
    val methodName: String,            // "cell" or "render"
    val cellNames: List<String>,       // All literal cell strings, such as "Plugin.Name::action"
    val templates: List<String>?,      // The render() templates, or null for the action's own template
    val offset: Int                    // Offset of the method name, so the reference resolves to this call
)


/**
 * How the view names in one file resolve, by the conventions of its template directory structure.
 */
class ReferencePluginContext(val structure: TemplateDirStructure) {

    /**
     * A view name without its plugin prefix, and the plugin the prefix names.
     */
    fun split(name: String): Pair<String, String?> = structure.resolveName(name)
}

private fun referencePluginContext(
    project: Project,
    settings: Settings,
    file: VirtualFile,
    isController: Boolean
): ReferencePluginContext? =
    TemplateDirStructure.ofSourceFile(project, settings, file, isController)?.let { ReferencePluginContext(it) }

object ViewFileDataIndexer : DataIndexer<String, List<ViewReferenceData>, FileContent> {
    val logger = this.thisLogger()

    // AST-based parsing implementation (tested and proven)
    private fun findMethodCallsByName(node: ASTNode, methodName: String, ctx: TemplateNameContext): List<MethodCallInfo> {
        val result = mutableListOf<MethodCallInfo>()
        findMethodCallsRecursive(node, methodName, result, ctx)
        return result
    }
    
    private fun findMethodCallsRecursive(
        node: ASTNode,
        targetMethodName: String,
        result: MutableList<MethodCallInfo>,
        ctx: TemplateNameContext
    ) {
        // Check if this node represents a method reference
        if (isMethodReference(node)) {
            val methodCall = parseMethodCall(node, targetMethodName, ctx)
            if (methodCall != null) {
                result.add(methodCall)
            }
        }
        
        // Recursively check child nodes - avoid toList() allocation
        var child = node.firstChildNode
        while (child != null) {
            findMethodCallsRecursive(child, targetMethodName, result, ctx)
            child = child.treeNext
        }
    }
    
    private fun isMethodReference(node: ASTNode): Boolean {
        return node.isMethodReference()
    }
    
    private fun parseMethodCall(node: ASTNode, targetMethodName: String, ctx: TemplateNameContext): MethodCallInfo? {
        // Look for VARIABLE, arrow, identifier, parameter list pattern
        var receiverName: String? = null
        var methodName: String? = null
        var parameterValues: List<String> = emptyList()
        var secondParameterValues: List<String> = emptyList()
        
        // Parse structure based on AST: VARIABLE -> arrow -> identifier -> (...) - avoid toList()
        var child = node.firstChildNode
        while (child != null) {
            when {
                child.isVariable() -> {
                    receiverName = child.text.removePrefix("$")
                }
                child.elementType == PhpTokenTypes.IDENTIFIER -> {
                    methodName = child.text
                }
                child.isParameterList() -> {
                    // The first parameter names the view or element, and render()'s second the layout:
                    var parameterIndex = 0
                    var paramChild = child.firstChildNode
                    while (paramChild != null && parameterIndex < 2) {
                        // Skip whitespace and commas to find the actual parameters
                        if (paramChild.elementType != TokenType.WHITE_SPACE && paramChild.elementType != PhpTokenTypes.opCOMMA) {
                            if (parameterIndex == 0) {
                                parameterValues = extractTemplateNames(paramChild, ctx)
                            } else {
                                secondParameterValues = extractTemplateNames(paramChild, ctx)
                            }
                            parameterIndex++
                        }
                        paramChild = paramChild.treeNext
                    }
                }
            }
            child = child.treeNext
        }
        
        // Only return if method name matches target and we have all required parts
        if (methodName?.equals(targetMethodName, ignoreCase = true) == true && 
            receiverName != null && (parameterValues.isNotEmpty() || secondParameterValues.isNotEmpty())) {
            return MethodCallInfo(
                methodName = methodName,
                receiverText = receiverName,
                firstParameterValues = parameterValues,
                offset = node.startOffset,
                secondParameterValues = secondParameterValues
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
        return node.isClassMethod()
    }
    
    private fun parseMethodDeclaration(node: ASTNode): MethodInfo? {
        var isPublic = true  // PHP default visibility is public if unspecified
        var methodName: String? = null

        var child = node.firstChildNode
        while (child != null) {
            when {
                child.isModifierList() -> {
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
                child.elementType == PhpTokenTypes.IDENTIFIER -> methodName = child.text
            }
            child = child.treeNext
        }

        return methodName?.let {
            MethodInfo(name = it, isPublic = isPublic, offset = node.startOffset)
        }
    }

    private fun findFieldAssignments(node: ASTNode, fieldName: String, ctx: TemplateNameContext): List<FieldAssignmentInfo> {
        val result = mutableListOf<FieldAssignmentInfo>()
        findFieldAssignmentsRecursive(node, fieldName, result, ctx)
        return result
    }

    private fun findFieldAssignmentsRecursive(
        node: ASTNode,
        targetFieldName: String,
        result: MutableList<FieldAssignmentInfo>,
        ctx: TemplateNameContext
    ) {
        // Check if this node represents an assignment expression
        if (node.isAssignmentExpression()) {
            val fieldAssignment = parseFieldAssignment(node, targetFieldName, ctx)
            if (fieldAssignment != null) {
                result.add(fieldAssignment)
            }
        }

        // Recursively check child nodes
        var child = node.firstChildNode
        while (child != null) {
            findFieldAssignmentsRecursive(child, targetFieldName, result, ctx)
            child = child.treeNext
        }
    }

    private fun parseFieldAssignment(node: ASTNode, targetFieldName: String, ctx: TemplateNameContext): FieldAssignmentInfo? {
        // Look for: $this->view = 'template_name'   (or a ternary / match of template names)
        // AST structure: ASSIGNMENT_EXPRESSION -> FIELD_REFERENCE (left) -> "=" -> <expression> (right)
        var fieldReference: ASTNode? = null
        var assignedValues: List<String> = emptyList()

        var child = node.firstChildNode
        while (child != null) {
            when {
                fieldReference == null && child.isFieldReference() -> {
                    fieldReference = child
                }
                fieldReference != null && isExpressionNode(child) -> {
                    assignedValues = extractTemplateNames(child, ctx)
                }
            }
            child = child.treeNext
        }

        // Parse the field reference to ensure it's $this->view
        if (fieldReference != null && assignedValues.isNotEmpty()) {
            var receiverName: String? = null
            var fieldName: String? = null

            var refChild = fieldReference.firstChildNode
            while (refChild != null) {
                when {
                    refChild.isVariable() -> {
                        receiverName = refChild.text.removePrefix("$")
                    }
                    refChild.elementType == PhpTokenTypes.IDENTIFIER -> {
                        fieldName = refChild.text
                    }
                }
                refChild = refChild.treeNext
            }

            if (receiverName == "this" && fieldName?.equals(targetFieldName, ignoreCase = true) == true) {
                return FieldAssignmentInfo(
                    fieldName = fieldName,
                    receiverText = receiverName,
                    assignedValues = assignedValues,
                    offset = fieldReference.startOffset
                )
            }
        }

        return null
    }

    private fun findViewBuilderCalls(node: ASTNode, ctx: TemplateNameContext): List<ViewBuilderCallInfo> {
        val result = mutableListOf<ViewBuilderCallInfo>()
        findViewBuilderCallsRecursive(node, result, ctx, -1)
        return result
    }

    private fun findViewBuilderCallsRecursive(
        node: ASTNode,
        result: MutableList<ViewBuilderCallInfo>,
        ctx: TemplateNameContext,
        containingMethodOffset: Int = -1
    ) {
        // Track when we enter a CLASS_METHOD
        val currentMethodOffset = if (node.isClassMethod()) {
            node.startOffset
        } else {
            containingMethodOffset
        }

        // Check if this node represents a method reference
        if (node.isMethodReference()) {
            val viewBuilderCall = parseViewBuilderCall(node, currentMethodOffset, ctx)
            if (viewBuilderCall != null) {
                result.add(viewBuilderCall)
            }
        }

        // Recursively check child nodes
        var child = node.firstChildNode
        while (child != null) {
            findViewBuilderCallsRecursive(child, result, ctx, currentMethodOffset)
            child = child.treeNext
        }
    }

    private fun parseViewBuilderCall(
        node: ASTNode,
        containingMethodOffset: Int,
        ctx: TemplateNameContext
    ): ViewBuilderCallInfo? {
        // Look for:
        //   1. $this->viewBuilder()->setTemplate('name')
        //   2. $this->viewBuilder()->setTemplatePath('path')
        //   3. $this->viewBuilder()->setTemplatePath('path')->setTemplate('name')  (chained)
        //
        // For chained calls, both setTemplatePath and setTemplate are returned as separate
        // ViewBuilderCallInfo objects to preserve state tracking behavior.
        //
        // AST structure:
        //   Normal: METHOD_REFERENCE (setTemplate/setTemplatePath)
        //     -> receiver: METHOD_REFERENCE (viewBuilder)
        //         -> receiver: VARIABLE ($this)
        //   Chained: METHOD_REFERENCE (setTemplate)
        //     -> receiver: METHOD_REFERENCE (setTemplatePath)
        //         -> receiver: METHOD_REFERENCE (viewBuilder)
        //             -> receiver: VARIABLE ($this)

        // Quick check: get method name first for early rejection
        val methodName = getMethodName(node)
        if (methodName != "setTemplate" && methodName != "setTemplatePath") {
            return null  // Early exit for non-target methods
        }

        // Now parse the rest of the structure
        var parameterValues: List<String> = emptyList()
        var receiverMethodRef: ASTNode? = null

        // Parse the outer method reference (setTemplate or setTemplatePath)
        var child = node.firstChildNode
        while (child != null) {
            when {
                child.isMethodReference() -> {
                    receiverMethodRef = child
                }
                child.isParameterList() -> {
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
                        parameterValues = extractTemplateNames(significantChildren[0], ctx)
                    }
                }
            }
            child = child.treeNext
        }

        if (receiverMethodRef == null) {
            return null
        }

        // Pattern 1: Normal calls - receiver is viewBuilder()
        if (isViewBuilderMethodCall(receiverMethodRef)) {
            // Use passed-in offset instead of walking up tree
            return ViewBuilderCallInfo(
                methodName = methodName,
                parameterValues = parameterValues,
                offset = node.startOffset,
                containingMethodStartOffset = containingMethodOffset
            )
        }

        // Pattern 2: Chained calls - setTemplate's receiver is setTemplatePath
        // Check receiver method name to see if it's setTemplatePath
        val receiverMethodName = getMethodName(receiverMethodRef)
        if (methodName == "setTemplate" && receiverMethodName == "setTemplatePath") {
            // Verify the chain goes back to viewBuilder()
            val viewBuilderNode = getReceiverMethodRef(receiverMethodRef)
            if (viewBuilderNode != null && isViewBuilderMethodCall(viewBuilderNode)) {
                // Use passed-in offset instead of walking up tree
                // Return as normal setTemplate - state tracking will handle the path
                return ViewBuilderCallInfo(
                    methodName = "setTemplate",
                    parameterValues = parameterValues,  // Just the template names, not combined!
                    offset = node.startOffset,
                    containingMethodStartOffset = containingMethodOffset
                )
            }
        }

        // Pattern 3: Chained setTemplatePath - will be processed normally by Pattern 1
        // AST traversal visits it separately, so no special handling needed

        return null
    }

    /**
     * Get the method name from a METHOD_REFERENCE node
     */
    private fun getMethodName(node: ASTNode): String? {
        var child = node.firstChildNode
        while (child != null) {
            if (child.elementType == PhpTokenTypes.IDENTIFIER) {
                return child.text
            }
            child = child.treeNext
        }
        return null
    }

    /**
     * Get the receiver METHOD_REFERENCE from a METHOD_REFERENCE node
     */
    private fun getReceiverMethodRef(node: ASTNode): ASTNode? {
        var child = node.firstChildNode
        while (child != null) {
            if (child.isMethodReference()) {
                return child
            }
            child = child.treeNext
        }
        return null
    }

    private fun isViewBuilderMethodCall(node: ASTNode): Boolean {
        // Check if this is a METHOD_REFERENCE with name "viewBuilder" and receiver "$this"
        var receiverVariable: String? = null
        var methodName: String? = null

        var child = node.firstChildNode
        while (child != null) {
            when {
                child.isVariable() -> {
                    receiverVariable = child.text.removePrefix("$")
                }
                child.elementType == PhpTokenTypes.IDENTIFIER -> {
                    methodName = child.text
                }
            }
            child = child.treeNext
        }

        return receiverVariable == "this" && methodName == "viewBuilder"
    }

    private fun findCellReferences(node: ASTNode, ctx: TemplateNameContext): List<CellReferenceInfo> {
        val result = mutableListOf<CellReferenceInfo>()
        findCellReferencesRecursive(node, result, ctx)
        return result
    }

    private fun findCellReferencesRecursive(
        node: ASTNode,
        result: MutableList<CellReferenceInfo>,
        ctx: TemplateNameContext
    ) {
        if (node.isMethodReference()) {
            val cellReference = parseCellReference(node, ctx)
            if (cellReference != null) {
                result.add(cellReference)
            }
        }
        var child = node.firstChildNode
        while (child != null) {
            findCellReferencesRecursive(child, result, ctx)
            child = child.treeNext
        }
    }

    private fun parseCellReference(node: ASTNode, ctx: TemplateNameContext): CellReferenceInfo? {
        // Look for:
        //   1. $this->cell('Name') or $this->_View->cell('Name')
        //   2. $this->cell('Name')->render('template')
        //   3. $cell = $this->cell('Name'); $cell->render('template')
        // A render() with no template renders the action's own template, so it is indexed like
        // a bare cell() call. The cell() call itself is left out when an indexed render() stands
        // for it: chained directly, or as the only later use of the variable it is assigned to.
        val methodName = getMethodName(node) ?: return null
        val offset = getMethodNameOffset(node) ?: return null
        if (methodName.equals("cell", ignoreCase = true)) {
            if (!isCellCall(node) || isReceiverOfIndexedRender(node, ctx) || isOnlyRenderedThroughVariable(node, ctx)) {
                return null
            }
            val cellNames = firstParameterValues(node, ctx)
            if (cellNames.isEmpty()) {
                return null
            }
            return CellReferenceInfo(methodName, cellNames, null, offset)
        }
        if (!methodName.equals("render", ignoreCase = true)) {
            return null
        }
        val receiver = node.firstChildNode ?: return null
        val cellCalls = when {
            receiver.isMethodReference() -> listOf(receiver)
            receiver.isVariable() -> assignedValueNodes(receiver, ctx)
            else -> emptyList()
        }.filter { it.isMethodReference() && isCellCall(it) }
        val cellNames = cellCalls.flatMap { firstParameterValues(it, ctx) }.distinct()
        val templates = renderTemplates(node, ctx)
        if (cellNames.isEmpty() || templates == null) {
            return null
        }
        return CellReferenceInfo(methodName, cellNames, templates.ifEmpty { null }, offset)
    }

    /**
     * The templates a render() call names: empty when it has no template argument, and null
     * when the argument does not resolve to any template name.
     */
    private fun renderTemplates(renderNode: ASTNode, ctx: TemplateNameContext): List<String>? {
        val argument = firstParameterNode(renderNode) ?: return emptyList()
        return extractTemplateNames(argument, ctx).ifEmpty { null }
    }

    private fun isCellCall(node: ASTNode): Boolean {
        if (getMethodName(node)?.equals("cell", ignoreCase = true) != true) {
            return false
        }
        val receiver = node.firstChildNode ?: return false
        return when {
            receiver.isVariable() -> receiver.text == "\$this"
            receiver.isFieldReference() -> {
                val fieldReceiver = receiver.firstChildNode
                fieldReceiver != null && fieldReceiver.isVariable() && fieldReceiver.text == "\$this" &&
                    getIdentifierText(receiver) == "_View"
            }
            else -> false
        }
    }

    private fun isReceiverOfIndexedRender(node: ASTNode, ctx: TemplateNameContext): Boolean {
        val parent = node.treeParent ?: return false
        return parent.isMethodReference() &&
            parent.firstChildNode == node &&
            getMethodName(parent)?.equals("render", ignoreCase = true) == true &&
            renderTemplates(parent, ctx) != null
    }

    // $cell = $this->cell('Name'), where every later use of $cell is an indexed $cell->render()
    private fun isOnlyRenderedThroughVariable(node: ASTNode, ctx: TemplateNameContext): Boolean {
        val uses = laterUsesOfAssignedVariable(node) ?: return false
        return uses.isNotEmpty() && uses.all { isReceiverOfIndexedRender(it, ctx) }
    }

    private fun firstParameterValues(node: ASTNode, ctx: TemplateNameContext): List<String> {
        val parameter = firstParameterNode(node) ?: return emptyList()
        return extractTemplateNames(parameter, ctx)
    }

    private fun firstParameterNode(node: ASTNode): ASTNode? {
        var child = node.firstChildNode
        while (child != null) {
            if (child.isParameterList()) {
                var paramChild = child.firstChildNode
                while (paramChild != null) {
                    if (paramChild.elementType != TokenType.WHITE_SPACE && paramChild.elementType != PhpTokenTypes.opCOMMA) {
                        return paramChild
                    }
                    paramChild = paramChild.treeNext
                }
                return null
            }
            child = child.treeNext
        }
        return null
    }

    private fun getMethodNameOffset(node: ASTNode): Int? {
        var child = node.firstChildNode
        while (child != null) {
            if (child.elementType == PhpTokenTypes.IDENTIFIER) {
                return child.startOffset
            }
            child = child.treeNext
        }
        return null
    }

    private fun getIdentifierText(node: ASTNode): String? {
        var child = node.firstChildNode
        while (child != null) {
            if (child.elementType == PhpTokenTypes.IDENTIFIER) {
                return child.text
            }
            child = child.treeNext
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
        val ctx = TemplateNameContext()
        val astRenderCalls = findMethodCallsByName(rootNode, "render", ctx)
            .filter { it.receiverText == "this" }
        val astElementCalls = findMethodCallsByName(rootNode, "element", ctx)
            .filter { it.receiverText == "this" && it.firstParameterValues.isNotEmpty() }
        val astViewFieldAssignments = findFieldAssignments(rootNode, "view", ctx)
            .filter { it.receiverText == "this" && it.assignedValues.isNotEmpty() }
        val astLayoutAssignments = findFieldAssignments(rootNode, "layout", ctx)
            .filter { it.receiverText == "this" && it.assignedValues.isNotEmpty() }

        // Early bailout: Quick text scan before AST traversal for viewBuilder calls
        val astViewBuilderCalls = if (!inputData.contentAsText.contains("viewBuilder")) {
            // Skip viewBuilder parsing entirely
            emptyList()
        } else {
            findViewBuilderCalls(rootNode, ctx)
                .filter { it.parameterValues.isNotEmpty() }
        }

        val astCellReferences = if (!settings.cake3Enabled || !inputData.contentAsText.contains("cell", ignoreCase = true)) {
            emptyList()
        } else {
            findCellReferences(rootNode, ctx)
        }

        val isController = isCakeControllerFile(virtualFile) ||
            (settings.cake3Enabled && isCakeCellFile(virtualFile))
        if (
            astRenderCalls.isEmpty() &&
            astElementCalls.isEmpty() &&
            astViewFieldAssignments.isEmpty() &&
            astLayoutAssignments.isEmpty() &&
            astViewBuilderCalls.isEmpty() &&
            astCellReferences.isEmpty() &&
            !isController
        ) {
            return result
        }

        val pluginContext = referencePluginContext(project, settings, virtualFile, isController)
        indexLayoutReferences(result, psiFile, astRenderCalls, astLayoutAssignments, isController, pluginContext)
        indexRenderCalls(result, projectDir, astRenderCalls, virtualFile, pluginContext)
        indexElementCalls(result, astElementCalls, templatesDirectoryOfViewFile(project, settings, virtualFile), pluginContext)
        indexViewFieldAssignments(result, projectDir, astViewFieldAssignments, virtualFile, pluginContext)
        indexViewBuilderCalls(result, projectDir, astViewBuilderCalls, virtualFile)
        indexCellReferences(result, projectDir, astCellReferences, virtualFile)

        if (isController) {
            // Use AST traversal instead of PSI for method declarations
            val astMethods = findMethodDeclarations(rootNode)
                .filter { it.isPublic && !cakeSkipRenderingMethods.contains(it.name.lowercase()) }
            
            indexImplicitRender(result, projectDir, settings, astMethods, virtualFile, pluginContext)
        }

        return result
    }

    private fun indexRenderCalls(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        renderCalls: List<MethodCallInfo>,
        virtualFile: VirtualFile,
        pluginContext: ReferencePluginContext?
    ) {
        if (renderCalls.isEmpty()) {
            return
        }

        val viewPathPrefix = viewPathPrefixFromSourceFile(projectDir, virtualFile)
            ?: return

        setViewPath(renderCalls, viewPathPrefix, result, pluginContext)
    }

    private fun indexElementCalls(
        result: MutableMap<String, List<ViewReferenceData>>,
        elementCalls: List<MethodCallInfo>,
        templatesDir: TemplatesDir?,
        pluginContext: ReferencePluginContext?
    ) {
        if (elementCalls.isEmpty() || templatesDir == null) {
            return
        }

        setViewPath(elementCalls, elementPathPrefix(templatesDir), result, pluginContext)
    }

    private fun indexViewFieldAssignments(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        fieldAssignments: List<FieldAssignmentInfo>,
        virtualFile: VirtualFile,
        pluginContext: ReferencePluginContext?
    ) {
        if (fieldAssignments.isEmpty()) {
            return
        }

        val viewPathPrefix = viewPathPrefixFromSourceFile(projectDir, virtualFile)
            ?: return

        for (assignment in fieldAssignments) {
            for (assignedValue in assignment.assignedValues) {
                val (path, plugin) = pluginContext?.split(assignedValue) ?: (assignedValue to null)
                val renderPath = RenderPath(path)

                if (renderPath.path.isEmpty()) {
                    continue
                }

                val fullViewPath = fullExplicitViewPath(viewPathPrefix, renderPath)
                addViewReference(result, fullViewPath, ViewReferenceData(
                    methodName = assignment.fieldName,
                    elementType = ElementType.FIELD_ASSIGNMENT,
                    offset = assignment.offset,
                    pluginPrefix = plugin
                ))
            }
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

            // Track the most recent setTemplatePath. It is a list because the path itself
            // may be a ternary or match expression with several possible values.
            var currentTemplatePaths: List<String>? = null

            for (call in sortedCalls) {
                if (call.parameterValues.isEmpty()) continue

                when (call.methodName) {
                    "setTemplatePath" -> {
                        // Update the current template path for subsequent setTemplate calls
                        currentTemplatePaths = call.parameterValues
                        // Note: We don't index setTemplatePath calls directly
                    }
                    "setTemplate" -> {
                        // Build the final paths combining setTemplatePath (if any) with setTemplate
                        val finalPaths = if (currentTemplatePaths != null) {
                            // setTemplatePath provides an absolute path from templates root
                            // Prefix with "/" to make it absolute so it's not combined with controller path
                            currentTemplatePaths.flatMap { templatePath ->
                                call.parameterValues.map { "/$templatePath/$it" }
                            }
                        } else {
                            call.parameterValues
                        }

                        for (finalPath in finalPaths) {
                            val renderPath = RenderPath(finalPath)
                            if (renderPath.path.isEmpty()) {
                                continue
                            }

                            val fullViewPath = fullExplicitViewPath(viewPathPrefix, renderPath)
                            addViewReference(result, fullViewPath, ViewReferenceData(
                                methodName = call.methodName,
                                elementType = ElementType.VIEW_BUILDER,
                                offset = call.offset
                            ))
                        }
                    }
                }
            }
        }
    }


    private fun indexCellReferences(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        cellReferences: List<CellReferenceInfo>,
        virtualFile: VirtualFile
    ) {
        if (cellReferences.isEmpty()) {
            return
        }

        val cellDirName = cellDirNameFromSourceFile(projectDir, virtualFile)

        for (reference in cellReferences) {
            for (cellString in reference.cellNames) {
                val cellName = parseCellName(cellString) ?: continue
                val templates = reference.templates
                    ?: listOf(cellName.actionOrDefault.camelCaseToUnderscore())
                for (template in templates) {
                    if (template.isEmpty() || template.startsWith("/")) {
                        continue
                    }
                    addViewReference(result, "${cellDirName}/${cellName.name}/${template}", ViewReferenceData(
                        methodName = reference.methodName,
                        elementType = ElementType.CELL_RENDER,
                        offset = reference.offset,
                        pluginPrefix = cellName.plugin
                    ))
                }
            }
        }
    }

    private fun indexImplicitRender(
        result: MutableMap<String, List<ViewReferenceData>>,
        projectDir: VirtualFile,
        settings: Settings,
        methods: List<MethodInfo>,
        controllerFile: VirtualFile,
        pluginContext: ReferencePluginContext?
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
                offset = method.offset,
                pluginPrefix = null
            )
            val newList = oldList + listOf(newViewReferenceData)
            result[fullViewPath] = newList
        }
    }

    private fun setViewPath(
        methodCalls: List<MethodCallInfo>,
        viewPathPrefix: ViewPathPrefix,
        result: MutableMap<String, List<ViewReferenceData>>,
        pluginContext: ReferencePluginContext?
    ) {
        for (methodCall in methodCalls) {
            for (parameterText in methodCall.firstParameterValues) {
                val (path, plugin) = pluginContext?.split(parameterText) ?: (parameterText to null)
                val content = RenderPath(path)

                if (content.path.isEmpty()) {
                    continue
                }
                val fullViewPath = fullExplicitViewPath(
                    viewPathPrefix,
                    content
                )
                addViewReference(result, fullViewPath, ViewReferenceData(
                    methodName = methodCall.methodName,
                    elementType = ElementType.METHOD_REFERENCE,
                    offset = methodCall.offset,
                    pluginPrefix = plugin
                ))
            }
        }
    }

    // Layout names are relative to the layout directory, whatever file names them:
    private fun indexLayoutReferences(
        result: MutableMap<String, List<ViewReferenceData>>,
        psiFile: PsiFile,
        renderCalls: List<MethodCallInfo>,
        layoutAssignments: List<FieldAssignmentInfo>,
        isController: Boolean,
        pluginContext: ReferencePluginContext?
    ) {
        val structure = pluginContext?.structure ?: return
        val layoutDirectory = structure.layoutDirectory?.takeIf { structure.indexesLayoutReferences } ?: return
        fun addLayout(name: String, data: (String?) -> ViewReferenceData) {
            val (layoutName, pluginPrefix) = pluginContext.split(name)
            if (layoutName.isNotEmpty() && !layoutName.startsWith("/")) {
                addViewReference(result, "$layoutDirectory/$layoutName", data(pluginPrefix))
            }
        }
        for (call in renderCalls) {
            for (name in call.secondParameterValues) {
                addLayout(name) { ViewReferenceData(call.methodName, ElementType.METHOD_REFERENCE, call.offset, it) }
            }
        }
        for (assignment in layoutAssignments) {
            for (name in assignment.assignedValues) {
                addLayout(name) { ViewReferenceData(assignment.fieldName, ElementType.FIELD_ASSIGNMENT, assignment.offset, it) }
            }
        }
        if (!isController) {
            return
        }
        for (phpClass in PsiTreeUtil.findChildrenOfType(psiFile, PhpClass::class.java)) {
            val field = phpClass.findOwnFieldByName("layout", false) ?: continue
            val name = field.defaultValue?.node?.stringLiteralValue() ?: continue
            addLayout(name) { ViewReferenceData("layout", ElementType.FIELD, field.textOffset, it) }
        }
    }

    private fun addViewReference(
        result: MutableMap<String, List<ViewReferenceData>>,
        fullViewPath: String,
        data: ViewReferenceData
    ) {
        val oldList = result.getOrDefault(fullViewPath, emptyList())
        result[fullViewPath] = oldList + listOf(data)
    }

}
