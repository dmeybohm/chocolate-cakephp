package com.daveme.chocolateCakePHP.view.viewfileindex

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeControllerFile
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
    val assignedValues: List<String>,  // All literal values the assigned expression can take
    val offset: Int
)

data class ViewBuilderCallInfo(
    val methodName: String,            // "setTemplate" or "setTemplatePath"
    val parameterValues: List<String>, // All literal template names or paths the parameter can take
    val offset: Int,
    val containingMethodStartOffset: Int  // Offset of containing CLASS_METHOD node
)


object ViewFileDataIndexer : DataIndexer<String, List<ViewReferenceData>, FileContent> {
    val logger = this.thisLogger()

    // Robust string literal extraction that handles different PHP plugin versions
    private fun extractStringLiteral(node: ASTNode): String {
        // Try child token
        val lit = node.findChildByType(PhpTokenTypes.STRING_LITERAL)
        val text = (lit ?: node).text
        // Strip quotes if present
        return text.removeSurrounding("'").removeSurrounding("\"")
    }

    /**
     * Per-file state for template name extraction.
     *
     * Caches the plain `$name = <expr>` assignments of each scope (method, function,
     * closure, or the file itself) so that resolving several variables in one method
     * only scans that method once. One instance is created per map() call, so
     * concurrent indexing of different files never shares state.
     */
    private class TemplateNameContext {
        val assignmentsByScope = HashMap<ASTNode, Map<String, List<ASTNode>>>()
    }

    /**
     * Collect every literal string a template expression can evaluate to.
     *
     * Handles:
     *   'literal'                          -> ["literal"]
     *   $cond ? 'a' : 'b'                  -> ["a", "b"]   (the condition is never inspected)
     *   $cond ?: 'b'                       -> ["b"]
     *   match ($x) { 1 => 'a', default => 'b' } -> ["a", "b"]  (arm conditions are never inspected)
     *   ('a')                              -> ["a"]
     *   $var                               -> the values of every `$var = <expr>` that precedes
     *                                         the use in the same scope (see resolveVariable)
     * These nest, so a ternary inside a match arm works too, and a variable may be assigned
     * a ternary of other variables. Anything else yields no values.
     */
    private fun extractTemplateNames(node: ASTNode, ctx: TemplateNameContext): List<String> {
        val result = mutableListOf<String>()
        collectTemplateNames(node, result, ctx, HashSet())
        return result
    }

    private fun collectTemplateNames(
        node: ASTNode,
        result: MutableList<String>,
        ctx: TemplateNameContext,
        visited: MutableSet<ASTNode>
    ) {
        when {
            node.isString() -> {
                result.add(extractStringLiteral(node))
            }
            node.isVariable() -> {
                resolveVariable(node, result, ctx, visited)
            }
            node.isTernaryExpression() -> {
                // Children: <condition> ? <true> : <false>   or   <condition> ?: <false>
                // Only the expression nodes after the "?" are possible template names.
                var afterQuestion = false
                var child = node.firstChildNode
                while (child != null) {
                    if (child.elementType == PhpTokenTypes.opQUEST) {
                        afterQuestion = true
                    } else if (afterQuestion && isExpressionNode(child)) {
                        collectTemplateNames(child, result, ctx, visited)
                    }
                    child = child.treeNext
                }
            }
            node.isMatchExpression() -> {
                var child = node.firstChildNode
                while (child != null) {
                    if (child.isMatchArm() || child.isDefaultMatchArm()) {
                        collectMatchArmBodyNames(child, result, ctx, visited)
                    }
                    child = child.treeNext
                }
            }
            node.isParenthesizedExpression() -> {
                var child = node.firstChildNode
                while (child != null) {
                    if (isExpressionNode(child)) {
                        collectTemplateNames(child, result, ctx, visited)
                    }
                    child = child.treeNext
                }
            }
        }
    }

    private fun collectMatchArmBodyNames(
        armNode: ASTNode,
        result: MutableList<String>,
        ctx: TemplateNameContext,
        visited: MutableSet<ASTNode>
    ) {
        // Children: <cond>, <cond>, ... => <body>
        // Only the expression after "=>" is a possible template name.
        var afterArrow = false
        var child = armNode.firstChildNode
        while (child != null) {
            if (child.elementType == PhpTokenTypes.opHASH_ARRAY) {
                afterArrow = true
            } else if (afterArrow && isExpressionNode(child)) {
                collectTemplateNames(child, result, ctx, visited)
            }
            child = child.treeNext
        }
    }

    /**
     * Resolve a `$name` use to the template names of every plain `$name = <expr>` assignment
     * that textually precedes the use in the same scope. All preceding assignments count, so
     * assignments in both branches of an if/else are found; a stale earlier value after a
     * sequential reassignment is included too, which is preferred over missing a branch.
     *
     * Not resolved: `$this`, method parameters, properties, `.=` and other compound
     * assignments, or anything assigned in a nested closure.
     *
     * The visited set holds assignment nodes already expanded on the current path and
     * stops cycles such as `$a = $b; $b = $a;` or `$a = $a ?: 'x'`.
     */
    private fun resolveVariable(
        variableNode: ASTNode,
        result: MutableList<String>,
        ctx: TemplateNameContext,
        visited: MutableSet<ASTNode>
    ) {
        val name = variableNode.text.removePrefix("$")
        if (name == "this") {
            return
        }
        val scope = scopeNodeOf(variableNode)
        val assignmentsByName = ctx.assignmentsByScope.getOrPut(scope) { collectAssignmentsInScope(scope) }
        val assignments = assignmentsByName[name] ?: return
        val useOffset = variableNode.startOffset

        for (assignment in assignments) {
            if (assignment.startOffset >= useOffset) {
                break  // Assignments are in document order
            }
            if (!visited.add(assignment)) {
                continue
            }
            val value = assignmentValueNode(assignment)
            if (value != null) {
                collectTemplateNames(value, result, ctx, visited)
            }
            visited.remove(assignment)
        }
    }

    /** The nearest enclosing method, function or closure, or the file root. */
    private fun scopeNodeOf(node: ASTNode): ASTNode {
        var current = node
        while (true) {
            val parent = current.treeParent ?: return current
            if (parent.isScopeNode()) {
                return parent
            }
            current = parent
        }
    }

    /**
     * All `$name = <expr>` assignments directly within a scope, grouped by variable name and
     * kept in document order. Nested scopes (closures, functions, methods) are not entered.
     * SELF_ASSIGNMENT_EXPRESSION (`.=`, `+=`, ...) is a different element type and is skipped
     * by isAssignmentExpression().
     */
    private fun collectAssignmentsInScope(scope: ASTNode): Map<String, List<ASTNode>> {
        val result = HashMap<String, MutableList<ASTNode>>()
        collectAssignmentsRecursive(scope, result)
        return result
    }

    private fun collectAssignmentsRecursive(node: ASTNode, result: MutableMap<String, MutableList<ASTNode>>) {
        if (node.isAssignmentExpression()) {
            val target = firstExpressionChild(node)
            if (target != null && target.isVariable()) {
                val name = target.text.removePrefix("$")
                result.getOrPut(name) { mutableListOf() }.add(node)
            }
        }
        var child = node.firstChildNode
        while (child != null) {
            if (!child.isScopeNode()) {
                collectAssignmentsRecursive(child, result)
            }
            child = child.treeNext
        }
    }

    /** The right-hand side of an ASSIGNMENT_EXPRESSION: the first expression after "=". */
    private fun assignmentValueNode(assignment: ASTNode): ASTNode? {
        var afterAssign = false
        var child = assignment.firstChildNode
        while (child != null) {
            if (child.elementType == PhpTokenTypes.opASGN) {
                afterAssign = true
            } else if (afterAssign && isExpressionNode(child)) {
                return child
            }
            child = child.treeNext
        }
        return null
    }

    private fun firstExpressionChild(node: ASTNode): ASTNode? {
        var child = node.firstChildNode
        while (child != null) {
            if (isExpressionNode(child)) {
                return child
            }
            child = child.treeNext
        }
        return null
    }

    // Composite nodes are expressions; leaf tokens (operators, whitespace, comments) are not.
    private fun isExpressionNode(node: ASTNode): Boolean {
        return node.firstChildNode != null
    }

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
                    // Extract the first parameter, ignoring additional parameters
                    // Both element() and render() accept optional parameters - we only need the first one
                    var paramChild = child.firstChildNode
                    while (paramChild != null) {
                        // Skip whitespace and commas to find the first actual parameter
                        if (paramChild.elementType != TokenType.WHITE_SPACE && paramChild.elementType != PhpTokenTypes.opCOMMA) {
                            parameterValues = extractTemplateNames(paramChild, ctx)
                            break
                        }
                        paramChild = paramChild.treeNext
                    }
                }
            }
            child = child.treeNext
        }
        
        // Only return if method name matches target and we have all required parts
        if (methodName?.equals(targetMethodName, ignoreCase = true) == true && 
            receiverName != null && parameterValues.isNotEmpty()) {
            return MethodCallInfo(
                methodName = methodName,
                receiverText = receiverName,
                firstParameterValues = parameterValues,
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
            .filter { it.receiverText == "this" && it.firstParameterValues.isNotEmpty() }
        val astElementCalls = findMethodCallsByName(rootNode, "element", ctx)
            .filter { it.receiverText == "this" && it.firstParameterValues.isNotEmpty() }
        val astViewFieldAssignments = findFieldAssignments(rootNode, "view", ctx)
            .filter { it.receiverText == "this" && it.assignedValues.isNotEmpty() }

        // Early bailout: Quick text scan before AST traversal for viewBuilder calls
        val astViewBuilderCalls = if (!inputData.contentAsText.contains("viewBuilder")) {
            // Skip viewBuilder parsing entirely
            emptyList()
        } else {
            findViewBuilderCalls(rootNode, ctx)
                .filter { it.parameterValues.isNotEmpty() }
        }

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
            for (assignedValue in assignment.assignedValues) {
                val renderPath = RenderPath(assignedValue)

                if (renderPath.path.isEmpty()) {
                    continue
                }

                val fullViewPath = fullExplicitViewPath(viewPathPrefix, renderPath)
                addViewReference(result, fullViewPath, ViewReferenceData(
                    methodName = assignment.fieldName,
                    elementType = ElementType.FIELD_ASSIGNMENT,
                    offset = assignment.offset
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
            for (parameterText in methodCall.firstParameterValues) {
                val content = RenderPath(parameterText)

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
                    offset = methodCall.offset
                ))
            }
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
