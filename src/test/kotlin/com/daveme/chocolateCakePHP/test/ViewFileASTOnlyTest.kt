package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.*
import com.intellij.lang.ASTNode
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.parser.PhpElementTypes
import java.io.File

/**
 * Test to understand PHP array AST structure for implementing array parsing
 */
class ViewFileASTOnlyTest : BasePlatformTestCase() {

    fun `test debug array AST structure`() {
        val phpCode = """
            <?php
            class TestController {
                public function test() {
                    ${'$'}this->set(['user' => ${'$'}currentUser, 'title' => ${'$'}pageTitle]);
                    ${'$'}this->set(['simple' => 'value']);
                    ${'$'}this->set(['nested' => ['key' => 'val']]);
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText(PhpFileType.INSTANCE, phpCode)
        val rootNode = psiFile.node

        // Find all array creation expressions
        val arrayNodes = findArrayCreationExpressions(rootNode)
        
        // Basic assertion to ensure test passes
        assertTrue("Should find at least one array", arrayNodes.isNotEmpty())
    }
    
    private fun findArrayCreationExpressions(node: ASTNode): List<ASTNode> {
        val result = mutableListOf<ASTNode>()
        findArrayCreationExpressionsRecursive(node, result)
        return result
    }
    
    private fun findArrayCreationExpressionsRecursive(node: ASTNode, result: MutableList<ASTNode>) {
        if (node.isArrayCreationExpression()) {
            result.add(node)
        }

        var child = node.firstChildNode
        while (child != null) {
            findArrayCreationExpressionsRecursive(child, result)
            child = child.treeNext
        }
    }
    
    private fun debugNodeStructure(node: ASTNode, builder: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        builder.appendLine("${indent}${node.elementType} = '${node.text.take(50).replace("\n", "\\n")}'")
        
        var child = node.firstChildNode
        while (child != null) {
            debugNodeStructure(child, builder, depth + 1)
            child = child.treeNext
        }
    }
}
