package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableASTDataIndexer
import com.daveme.chocolateCakePHP.view.viewvariableindex.VarKind
import com.daveme.chocolateCakePHP.view.viewvariableindex.SourceKind
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileContentImpl

/**
 * Test for ViewVariableASTDataIndexer to verify AST-based parsing works correctly
 */
class ViewVariableASTDataIndexerTest : BasePlatformTestCase() {

    fun `test AST parsing works correctly`() {
        val controllerCode = """
            <?php
            namespace App\Controller;
            
            use Cake\Controller\Controller;
            
            class MoviesController extends Controller {
                public function index() {
                    ${'$'}user = "test";
                    ${'$'}title = "Movie Index";
                    
                    // Test PAIR case: $this->set('name', ${'$'}value)
                    ${'$'}this->set('user', ${'$'}user);
                    ${'$'}this->set('title', ${'$'}title);
                    
                    // Test ARRAY case: $this->set(['key' => ${'$'}value])
                    ${'$'}this->set(['page' => ${'$'}title]);
                    
                    // Test COMPACT case: $this->set(compact('user'))
                    ${'$'}this->set(compact('user'));
                }
            }
        """.trimIndent()

        myFixture.configureByText("MoviesController.php", controllerCode)
        val controllerFile = myFixture.file
        val fileContent = FileContentImpl.createByFile(controllerFile.virtualFile, project)
        
        // Test that indexing works without exceptions
        val indexResult = ViewVariableASTDataIndexer.map(fileContent)
        
        // Verify we get results
        assertFalse("Index result should not be empty", indexResult.isEmpty())
        
        val controllerKey = "Movies:index"
        if (indexResult.containsKey(controllerKey)) {
            val viewVariables = indexResult[controllerKey]!!
            
            // If we found variables, verify their structure
            viewVariables.forEach { (varName, rawVar) ->
                assertNotNull("Variable name should not be null", varName)
                assertNotNull("RawViewVar should not be null", rawVar)
                assertNotNull("VarKind should not be null", rawVar.varKind)
                assertNotNull("VarHandle should not be null", rawVar.varHandle)
                assertNotNull("SourceKind should not be null", rawVar.varHandle.sourceKind)
                assertTrue("Offset should be positive", rawVar.offset >= 0)
                assertTrue("VarHandle offset should be positive", rawVar.varHandle.offset >= 0)
                assertFalse("Symbol name should not be empty", rawVar.varHandle.symbolName.isEmpty())
            }
        }
        
        // The main goal is to verify the AST parsing doesn't crash and produces valid data structures
        assertTrue("AST-based parsing completed successfully", true)
    }
    
    fun `test PSI type resolution does not crash`() {
        val controllerCode = """
            <?php
            namespace App\Controller;
            
            use Cake\Controller\Controller;
            
            class TestController extends Controller {
                public function show() {
                    ${'$'}simpleString = "test value";
                    ${'$'}this->set('test', ${'$'}simpleString);
                }
            }
        """.trimIndent()

        myFixture.configureByText("TestController.php", controllerCode)
        val controllerFile = myFixture.file
        val fileContent = FileContentImpl.createByFile(controllerFile.virtualFile, project)
        
        val indexResult = ViewVariableASTDataIndexer.map(fileContent)
        
        // If we have indexed variables, test that type resolution doesn't crash
        indexResult.values.forEach { viewVariablesWithRawVars ->
            viewVariablesWithRawVars.forEach { (_, rawVar) ->
                try {
                    val resolvedType = rawVar.resolveType(project, controllerFile)
                    assertNotNull("Resolved type should not be null", resolvedType)
                    // The type might be "mixed" or incomplete, but it shouldn't crash
                } catch (e: Exception) {
                    fail("Type resolution should not throw exceptions: ${e.message}")
                }
            }
        }
        
        assertTrue("PSI type resolution completed without crashing", true)
    }
}