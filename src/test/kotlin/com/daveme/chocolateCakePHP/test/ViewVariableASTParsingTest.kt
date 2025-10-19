package com.daveme.chocolateCakePHP.test

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.parser.PhpElementTypes
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference

// Data structures for AST-level parsing
data class ASTMethodInfo(
    val name: String,
    val isPublic: Boolean,
    val offset: Int
)

data class ASTAssignmentInfo(
    val variableName: String?,
    val valueText: String?,
    val valueType: String?, // "compact", "array", "variable", "string", etc.
    val offset: Int
)

data class ASTSetCallInfo(
    val receiverText: String?,
    val firstParameterText: String?,
    val firstParameterType: String?, // "string", "array", "compact", "variable"
    val secondParameterText: String?,
    val secondParameterType: String?,
    val offset: Int
)

class ViewVariableASTParsingTest : BasePlatformTestCase() {

    // AST-based parsing implementation for methods
    private fun findMethodDeclarations(node: ASTNode): List<ASTMethodInfo> {
        val result = mutableListOf<ASTMethodInfo>()
        findMethodDeclarationsRecursive(node, result)
        return result
    }
    
    private fun findMethodDeclarationsRecursive(node: ASTNode, result: MutableList<ASTMethodInfo>) {
        if (node.elementType == PhpElementTypes.CLASS_METHOD) {
            val methodInfo = parseMethodDeclaration(node)
            if (methodInfo != null) {
                result.add(methodInfo)
            }
        }
        
        for (child in node.getChildren(null)) {
            findMethodDeclarationsRecursive(child, result)
        }
    }
    
    private fun parseMethodDeclaration(node: ASTNode): ASTMethodInfo? {
        val children = node.getChildren(null).toList()
        
        var visibility = "public" // default
        var methodName: String? = null
        
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
            ASTMethodInfo(
                name = methodName,
                isPublic = visibility == "public",
                offset = node.startOffset
            )
        } else null
    }

    // AST-based parsing for assignments
    private fun findAssignmentExpressions(node: ASTNode): List<ASTAssignmentInfo> {
        val result = mutableListOf<ASTAssignmentInfo>()
        findAssignmentExpressionsRecursive(node, result)
        return result
    }
    
    private fun findAssignmentExpressionsRecursive(node: ASTNode, result: MutableList<ASTAssignmentInfo>) {
        if (node.elementType == PhpElementTypes.ASSIGNMENT_EXPRESSION) {
            val assignmentInfo = parseAssignmentExpression(node)
            if (assignmentInfo != null) {
                result.add(assignmentInfo)
            }
        }
        
        for (child in node.getChildren(null)) {
            findAssignmentExpressionsRecursive(child, result)
        }
    }
    
    private fun parseAssignmentExpression(node: ASTNode): ASTAssignmentInfo? {
        val children = node.getChildren(null).toList()

        var variableName: String? = null
        var valueText: String? = null
        var valueType: String? = null

        for (child in children) {
            when (child.elementType) {
                PhpElementTypes.VARIABLE -> {
                    if (variableName == null) {
                        variableName = child.text.removePrefix("$")
                    } else {
                        valueText = child.text
                        valueType = "variable"
                    }
                }
                PhpElementTypes.ARRAY_CREATION_EXPRESSION -> {
                    valueText = child.text
                    valueType = "array"
                }
                PhpElementTypes.STRING -> {
                    valueText = child.text
                    valueType = "string"
                }
                else -> {
                    // For any other type, check if it's a compact() call by text
                    val childText = child.text.trim()
                    if (childText.startsWith("compact(", ignoreCase = true) ||
                        childText.contains("compact(", ignoreCase = true)) {
                        valueText = child.text
                        valueType = "compact"
                    } else if (valueText == null && child.text.isNotBlank()) {
                        // Capture any other value
                        valueText = child.text
                        if (valueType == null) {
                            valueType = "unknown"
                        }
                    }
                }
            }
        }

        return if (variableName != null) {
            ASTAssignmentInfo(
                variableName = variableName,
                valueText = valueText,
                valueType = valueType,
                offset = node.startOffset
            )
        } else null
    }

    // AST-based parsing for set() calls
    private fun findSetCalls(node: ASTNode): List<ASTSetCallInfo> {
        val result = mutableListOf<ASTSetCallInfo>()
        findSetCallsRecursive(node, result)
        return result
    }
    
    private fun findSetCallsRecursive(node: ASTNode, result: MutableList<ASTSetCallInfo>) {
        if (node.elementType == PhpElementTypes.METHOD_REFERENCE) {
            val setCall = parseSetCall(node)
            if (setCall != null) {
                result.add(setCall)
            }
        }
        
        for (child in node.getChildren(null)) {
            findSetCallsRecursive(child, result)
        }
    }
    
    private fun parseSetCall(node: ASTNode): ASTSetCallInfo? {
        val children = node.getChildren(null).toList()
        
        var receiverName: String? = null
        var methodName: String? = null
        var firstParameterText: String? = null
        var firstParameterType: String? = null
        var secondParameterText: String? = null
        var secondParameterType: String? = null
        
        for (child in children) {
            when (child.elementType) {
                PhpElementTypes.VARIABLE -> {
                    receiverName = child.text.removePrefix("$")
                }
                PhpTokenTypes.IDENTIFIER -> {
                    methodName = child.text
                }
                PhpElementTypes.PARAMETER_LIST -> {
                    val params = parseParameterList(child)
                    if (params.isNotEmpty()) {
                        firstParameterText = params[0].first
                        firstParameterType = params[0].second
                    }
                    if (params.size > 1) {
                        secondParameterText = params[1].first
                        secondParameterType = params[1].second
                    }
                }
            }
        }
        
        // Only return if method name is "set" and receiver is "this"
        if (methodName?.equals("set", ignoreCase = true) == true && 
            receiverName == "this") {
            return ASTSetCallInfo(
                receiverText = receiverName,
                firstParameterText = firstParameterText,
                firstParameterType = firstParameterType,
                secondParameterText = secondParameterText,
                secondParameterType = secondParameterType,
                offset = node.startOffset
            )
        }
        
        return null
    }
    
    private fun parseParameterList(parameterListNode: ASTNode): List<Pair<String, String>> {
        val parameters = mutableListOf<Pair<String, String>>()
        val children = parameterListNode.getChildren(null).toList()

        for (child in children) {
            when (child.elementType) {
                PhpElementTypes.STRING -> {
                    parameters.add(Pair(child.text.removeSurrounding("'").removeSurrounding("\""), "string"))
                }
                PhpElementTypes.VARIABLE -> {
                    parameters.add(Pair(child.text, "variable"))
                }
                PhpElementTypes.ARRAY_CREATION_EXPRESSION -> {
                    parameters.add(Pair(child.text, "array"))
                }
                else -> {
                    // Check if it's compact() by text content
                    val childText = child.text.trim()
                    if (childText.startsWith("compact(", ignoreCase = true) ||
                        childText.contains("compact(", ignoreCase = true)) {
                        parameters.add(Pair(child.text, "compact"))
                    } else if (child.text.isNotBlank() &&
                               child.elementType != PhpTokenTypes.chLPAREN &&
                               child.elementType != PhpTokenTypes.chRPAREN &&
                               child.elementType != PhpTokenTypes.opCOMMA) {
                        // Some other non-whitespace parameter
                        parameters.add(Pair(child.text, "unknown"))
                    }
                }
            }
        }

        return parameters
    }

    fun testSimpleMethodDeclarations() {
        val code = """
            <?php
            class TestController {
                public function index() {}
                private function helper() {}
                protected function init() {}
            }
        """.trimIndent()
        
        val psiFile = createPhpFile(code)
        
        // PSI-based approach
        val psiMethods = PsiTreeUtil.findChildrenOfType(psiFile, Method::class.java).toList()
        
        // AST-based approach
        val astMethods = findMethodDeclarations(psiFile.node)
        
        // Both should find 3 methods
        assertEquals(3, psiMethods.size)
        assertEquals(3, astMethods.size)
        
        // Check method names match
        val psiMethodNames = psiMethods.map { it.name }.sorted()
        val astMethodNames = astMethods.map { it.name }.sorted()
        assertEquals(psiMethodNames, astMethodNames)
        
        // Check visibility detection
        val astPublicMethods = astMethods.filter { it.isPublic }
        val psiPublicMethods = psiMethods.filter { it.access.isPublic }
        assertEquals(psiPublicMethods.size, astPublicMethods.size)
        assertEquals("index", astPublicMethods.first().name)
    }

    fun testSimpleAssignmentExpressions() {
        val code = """
            <?php
            class TestController {
                public function index() {
                    ${'$'}users = array();
                    ${'$'}data = compact('users');
                    ${'$'}result = ${'$'}users;
                }
            }
        """.trimIndent()
        
        val psiFile = createPhpFile(code)
        
        // PSI-based approach
        val psiAssignments = PsiTreeUtil.findChildrenOfType(psiFile, AssignmentExpression::class.java).toList()
        
        // AST-based approach  
        val astAssignments = findAssignmentExpressions(psiFile.node)
        
        // Both should find 3 assignments
        assertEquals(3, psiAssignments.size)
        assertEquals(3, astAssignments.size)
        
        // Check variable names
        val astVariableNames = astAssignments.mapNotNull { it.variableName }.sorted()
        assertEquals(listOf("data", "result", "users"), astVariableNames)
        
        // Check value types
        val dataAssignment = astAssignments.find { it.variableName == "data" }
        assertEquals("compact", dataAssignment?.valueType)
        
        val usersAssignment = astAssignments.find { it.variableName == "users" }
        assertEquals("array", usersAssignment?.valueType)
        
        val resultAssignment = astAssignments.find { it.variableName == "result" }
        assertEquals("variable", resultAssignment?.valueType)
    }

    fun testSimpleSetCalls() {
        val code = """
            <?php
            class TestController {
                public function index() {
                    ${'$'}this->set('users', ${'$'}userList);
                    ${'$'}this->set(compact('data'));
                    ${'$'}this->set(['key' => 'value']);
                }
            }
        """.trimIndent()
        
        val psiFile = createPhpFile(code)
        
        // PSI-based approach
        val psiSetCalls = PsiTreeUtil.findChildrenOfType(psiFile, MethodReference::class.java)
            .filter { it.name.equals("set", ignoreCase = true) }
            .toList()
        
        // AST-based approach
        val astSetCalls = findSetCalls(psiFile.node)
        
        // Both should find 3 set calls
        assertEquals(3, psiSetCalls.size)
        assertEquals(3, astSetCalls.size)
        
        // Check first parameter types
        val stringCall = astSetCalls.find { it.firstParameterType == "string" }
        assertNotNull(stringCall)
        assertEquals("users", stringCall?.firstParameterText)
        
        val compactCall = astSetCalls.find { it.firstParameterType == "compact" }
        assertNotNull(compactCall)
        assertTrue(compactCall?.firstParameterText?.contains("compact") == true)
        
        val arrayCall = astSetCalls.find { it.firstParameterType == "array" }
        assertNotNull(arrayCall)
        assertTrue(arrayCall?.firstParameterText?.startsWith("[") == true)
    }

    fun testComplexSetCallWithMultipleParameters() {
        val code = """
            <?php
            class TestController {
                public function index() {
                    ${'$'}this->set(['name1', 'name2'], [${'$'}value1, ${'$'}value2]);
                    ${'$'}this->set('single', ${'$'}singleValue);
                }
            }
        """.trimIndent()
        
        val psiFile = createPhpFile(code)
        
        // AST-based approach
        val astSetCalls = findSetCalls(psiFile.node)
        
        assertEquals(2, astSetCalls.size)
        
        // Check multi-parameter call
        val multiParamCall = astSetCalls.find { 
            it.firstParameterType == "array" && it.secondParameterType == "array" 
        }
        assertNotNull(multiParamCall)
        
        // Check single parameter call
        val singleParamCall = astSetCalls.find { 
            it.firstParameterType == "string" && it.secondParameterType == "variable"
        }
        assertNotNull(singleParamCall)
        assertEquals("single", singleParamCall?.firstParameterText)
    }

    fun testIndirectSetCallsWithAssignments() {
        val code = """
            <?php
            class TestController {
                public function index() {
                    ${'$'}compactData = compact('users', 'posts');
                    ${'$'}arrayData = ['key' => 'value'];
                    ${'$'}this->set(${'$'}compactData);
                    ${'$'}this->set(${'$'}arrayData);
                }
            }
        """.trimIndent()
        
        val psiFile = createPhpFile(code)
        
        // AST-based approach
        val astSetCalls = findSetCalls(psiFile.node)
        val astAssignments = findAssignmentExpressions(psiFile.node)
        
        assertEquals(2, astSetCalls.size)
        assertEquals(2, astAssignments.size)
        
        // Check assignments
        val compactAssignment = astAssignments.find { it.variableName == "compactData" }
        assertNotNull(compactAssignment)
        assertEquals("compact", compactAssignment?.valueType)
        
        val arrayAssignment = astAssignments.find { it.variableName == "arrayData" }
        assertNotNull(arrayAssignment)
        assertEquals("array", arrayAssignment?.valueType)
        
        // Check set calls use variables
        val variableSetCalls = astSetCalls.filter { it.firstParameterType == "variable" }
        assertEquals(2, variableSetCalls.size)
    }

    private fun createPhpFile(content: String): PsiFile {
        return myFixture.configureByText("TestFile.php", content)
    }
}