package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.*
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.lexer.PhpTokenTypes

/**
 * Test cases for AST-based parsing functions to replace PSI operations in indexers.
 * These tests ensure that AST parsing produces equivalent results to PSI-based parsing.
 */
class ASTParsingTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/fixtures"
    }

    fun `test parse render method calls from AST`() {
        val phpCode = """
            <?php
            class MovieController extends Controller {
                public function index() {
                    ${'$'}this->render('movie/list');
                    ${'$'}this->render("movie/detail");
                    ${'$'}other->render('ignored');
                    ${'$'}this->element('movie/card');
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText(PhpFileType.INSTANCE, phpCode)
        val rootNode = psiFile.node

        // Test cases for render calls
        val allRenderCalls = findMethodCallsByName(rootNode, "render")
        val renderCalls = allRenderCalls.filter { it.receiverText == "this" }
        
        assertEquals("Should find 2 render calls on \$this", 2, renderCalls.size)
        
        if (renderCalls.isNotEmpty()) {
            val firstCall = renderCalls[0]
            assertEquals("render", firstCall.methodName)
            assertEquals("this", firstCall.receiverText)
            assertEquals("movie/list", firstCall.firstParameterText)
            assertTrue("Offset should be positive", firstCall.offset > 0)
        }
        
        if (renderCalls.size > 1) {
            val secondCall = renderCalls[1] 
            assertEquals("render", secondCall.methodName)
            assertEquals("this", secondCall.receiverText)
            assertEquals("movie/detail", secondCall.firstParameterText)
        }
    }

    fun `test parse element method calls from AST`() {
        val phpCode = """
            <?php
            class MovieController extends Controller {
                public function show() {
                    ${'$'}this->element('director/info');
                    ${'$'}this->element("actor/filmography");
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText(PhpFileType.INSTANCE, phpCode)
        val rootNode = psiFile.node

        val elementCalls = findMethodCallsByName(rootNode, "element")

        assertEquals("Should find 2 element calls", 2, elementCalls.size)
        assertEquals("director/info", elementCalls[0].firstParameterText)
        assertEquals("actor/filmography", elementCalls[1].firstParameterText)
    }

    fun `test parse element method calls with parameters from AST`() {
        val phpCode = """
            <?php
            class MovieController extends Controller {
                public function show() {
                    ${'$'}this->element('director/info', ['director' => ${'$'}director]);
                    ${'$'}this->element("actor/filmography", ['actor' => ${'$'}actor, 'movies' => ${'$'}movies]);
                    ${'$'}this->element('simple');
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText(PhpFileType.INSTANCE, phpCode)
        val rootNode = psiFile.node

        val elementCalls = findMethodCallsByName(rootNode, "element")

        assertEquals("Should find 3 element calls", 3, elementCalls.size)
        assertEquals("director/info", elementCalls[0].firstParameterText)
        assertEquals("actor/filmography", elementCalls[1].firstParameterText)
        assertEquals("simple", elementCalls[2].firstParameterText)
    }

    fun `test parse method declarations from AST`() {
        val phpCode = """
            <?php
            class MovieController extends Controller {
                public function index() {
                    // public method
                }
                
                private function helper() {
                    // private method
                }
                
                protected function init() {
                    // protected method  
                }
                
                function implicitPublic() {
                    // implicit public
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText(PhpFileType.INSTANCE, phpCode)
        val rootNode = psiFile.node

        val methods = findMethodDeclarations(rootNode)
        
        val publicMethods = methods.filter { it.isPublic }
        assertEquals("Should find 2 public methods", 2, publicMethods.size)
        
        val methodNames = publicMethods.map { it.name }.sorted()
        assertEquals(listOf("implicitPublic", "index"), methodNames)
    }

    fun `test parse complex method calls with various parameters`() {
        val phpCode = """
            <?php 
            class TestController extends Controller {
                public function test() {
                    // Valid cases
                    ${'$'}this->render('simple');
                    ${'$'}this->render("double_quotes");
                    ${'$'}this->render('path/to/template');
                    
                    // Edge cases that should be ignored
                    ${'$'}this->render(); // no parameters
                    ${'$'}this->render(${'$'}variable); // variable parameter
                    ${'$'}this->render('template', ['data']); // multiple parameters - extracts first
                    ${'$'}other->render('ignored'); // wrong receiver

                    // Nested/complex cases
                    if (true) {
                        ${'$'}this->render('nested/template');
                    }
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText(PhpFileType.INSTANCE, phpCode)
        val rootNode = psiFile.node

        val renderCalls = findMethodCallsByName(rootNode, "render")
        val validCalls = renderCalls.filter {
            it.receiverText == "this" && it.firstParameterText != null
        }

        assertEquals("Should find 5 valid render calls", 5, validCalls.size)

        val expectedTemplates = listOf("simple", "double_quotes", "path/to/template", "template", "nested/template")
        val actualTemplates = validCalls.map { it.firstParameterText }
        assertEquals(expectedTemplates, actualTemplates)
    }

    fun `test AST parsing matches PSI parsing results`() {
        // This test compares AST results with PSI results to ensure equivalence
        val phpCode = """
            <?php
            class ComparisonController extends Controller {
                public function action1() {
                    ${'$'}this->render('template1');
                }
                
                public function action2() {
                    ${'$'}this->element('element1');
                }
                
                private function privateMethod() {
                    // should be excluded
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText(PhpFileType.INSTANCE, phpCode)
        
        // PSI-based results (current implementation)
        val psiMethods = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            psiFile, com.jetbrains.php.lang.psi.elements.Method::class.java
        ).filter { it.name.isNotEmpty() }
        
        val psiMethodRefs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            psiFile, com.jetbrains.php.lang.psi.elements.MethodReference::class.java
        )
        
        // AST-based results (new implementation)
        val astMethods = findMethodDeclarations(psiFile.node)
        val astRenderCalls = findMethodCallsByName(psiFile.node, "render")
        val astElementCalls = findMethodCallsByName(psiFile.node, "element")
        
        // Compare method declarations
        assertEquals("Method count should match", psiMethods.size, astMethods.size)
        
        // Compare method calls
        val expectedRenderCalls = psiMethodRefs.count { 
            it.name == "render" && (it.firstChild as? com.jetbrains.php.lang.psi.elements.Variable)?.name == "this"
        }
        assertEquals("Render call count should match", expectedRenderCalls, astRenderCalls.size)
    }

    // AST-based parsing implementation
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
        return node.isMethodReference()
    }
    
    private fun parseMethodCall(node: ASTNode, targetMethodName: String): MethodCallInfo? {
        val text = node.text.trim()
        
        // More precise parsing: find child nodes
        val children = node.getChildren(null).toList()
        
        // Look for VARIABLE, arrow, identifier, parameter list pattern
        var receiverName: String? = null
        var methodName: String? = null
        var parameterValue: String? = null
        
        // Parse structure based on AST: VARIABLE -> arrow -> identifier -> (...)
        for ((index, child) in children.withIndex()) {
            when {
                child.isVariable() -> {
                    receiverName = child.text.removePrefix("$")
                }
                child.isIdentifier() -> {
                    methodName = child.text
                }
                child.isParameterList() -> {
                    // Extract the first string parameter, ignoring additional parameters
                    val childNodes = child.getChildren(null).toList()
                    val significantChildren = childNodes.filter {
                        val type = it.elementType.toString()
                        type != "WHITE_SPACE" && type != "comma"
                    }

                    // Get the first String parameter if it exists
                    val firstStringParam = significantChildren.firstOrNull {
                        it.isString()
                    }
                    if (firstStringParam != null) {
                        parameterValue = firstStringParam.text.removeSurrounding("'").removeSurrounding("\"")
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
        return node.isClassMethod()
    }
    
    private fun parseMethodDeclaration(node: ASTNode): MethodInfo? {
        val children = node.getChildren(null).toList()
        
        var visibility = "public" // default
        var methodName: String? = null
        
        // Parse structure: Modifier list, function keyword, identifier
        for (child in children) {
            when {
                child.isModifierList() -> {
                    val modifierText = child.text.trim()
                    if (modifierText in listOf("private", "protected", "public")) {
                        visibility = modifierText
                    }
                }
                child.isIdentifier() -> {
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
    
    // Data classes for test results
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
}
