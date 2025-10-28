package com.daveme.chocolateCakePHP.test

import com.intellij.lang.ASTNode
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.PhpFileType

/**
 * Simple debug test to get AST parsing working step by step
 */
class ASTParsingDebugTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/fixtures"
    }

    fun `test simple AST node traversal`() {
        val phpCode = """
            <?php
            class TestController {
                public function test() {
                    ${'$'}this->render('template');
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText(PhpFileType.INSTANCE, phpCode)
        val rootNode = psiFile.node

        // Debug: Print all node types to understand structure
        val allNodes = mutableListOf<String>()
        collectNodeTypes(rootNode, allNodes, 0)
        
        // Look for render method calls
        val renderNodes = findNodesContainingText(rootNode, "render")
        
        // Basic assertion - should find at least one render call
        assertTrue("Should find at least one node containing 'render'", renderNodes.isNotEmpty())
    }

    private fun collectNodeTypes(node: ASTNode, result: MutableList<String>, depth: Int) {
        val indent = "  ".repeat(depth)
        val nodeInfo = "$indent${node.elementType} = '${node.text.take(50).replace("\n", "\\n")}'"
        result.add(nodeInfo)
        
        if (depth < 8) { // Limit recursion depth
            for (child in node.getChildren(null)) {
                collectNodeTypes(child, result, depth + 1)
            }
        }
    }
    
    private fun findNodesContainingText(node: ASTNode, text: String): List<ASTNode> {
        val result = mutableListOf<ASTNode>()
        
        if (node.text.contains(text, ignoreCase = true)) {
            result.add(node)
        }
        
        for (child in node.getChildren(null)) {
            result.addAll(findNodesContainingText(child, text))
        }
        
        return result
    }
}
