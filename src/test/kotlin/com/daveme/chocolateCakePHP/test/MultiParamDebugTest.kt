package com.daveme.chocolateCakePHP.test

import com.intellij.lang.ASTNode
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.PhpFileType

class MultiParamDebugTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String {
        return "src/test/fixtures"
    }

    fun `test debug multi parameter structure`() {
        val phpCode = """
            <?php
            class TestController {
                public function test() {
                    ${'$'}this->render('template', ['data']);
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText(PhpFileType.INSTANCE, phpCode)
        val rootNode = psiFile.node

        // Find the method reference
        val methodRefs = findMethodReferences(rootNode)
        
        val debugOutput = buildString {
            methodRefs.forEach { node ->
                appendLine("=== Method Reference: ${node.text.trim()} ===")
                val children = node.getChildren(null).toList()
                children.forEachIndexed { index, child ->
                    appendLine("Child $index: ${child.elementType} = '${child.text.trim()}'")
                    
                    if (child.elementType.toString() == "Parameter list") {
                        appendLine("  Parameter list children:")
                        val paramChildren = child.getChildren(null).toList()
                        paramChildren.forEachIndexed { pIndex, pChild ->
                            appendLine("    Param $pIndex: ${pChild.elementType} = '${pChild.text.trim()}'")
                        }
                    }
                }
            }
        }
        
        java.io.File("multi-param-debug.txt").writeText(debugOutput)
        
        assertTrue("Should find at least one method reference", methodRefs.isNotEmpty())
    }
    
    private fun findMethodReferences(node: ASTNode): List<ASTNode> {
        val result = mutableListOf<ASTNode>()
        
        if (node.elementType.toString() == "Method reference") {
            result.add(node)
        }
        
        for (child in node.getChildren(null)) {
            result.addAll(findMethodReferences(child))
        }
        
        return result
    }
}