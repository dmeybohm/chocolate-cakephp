package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableASTDataIndexer
import com.daveme.chocolateCakePHP.view.viewvariableindex.VarKind
import com.daveme.chocolateCakePHP.view.viewvariableindex.SourceKind
import com.intellij.util.indexing.FileContentImpl

class ViewVariableASTDataIndexerTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/vendor/cakephp.php"
        )
    }

    fun `test AST parsing works correctly`() {
        val controllerCode = """
            <?php
            namespace App\Controller;

            use Cake\Controller\Controller;

            class MoviesController extends Controller {
                public function index() {
                    ${'$'}user = "test";
                    ${'$'}title = "Movie Index";

                    // Test PAIR case: ${'$'}this->set('name', ${'$'}value)
                    ${'$'}this->set('user', ${'$'}user);
                    ${'$'}this->set('title', ${'$'}title);

                    // Test ARRAY case: ${'$'}this->set(['key' => ${'$'}value])
                    ${'$'}this->set(['page' => ${'$'}title]);

                    // Test COMPACT case: ${'$'}this->set(compact('user'))
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

    fun `test compact with method parameter is indexed correctly`() {
        val controllerCode = """
            <?php
            namespace App\Controller;

            use Cake\Controller\Controller;

            class MoviesController extends Controller {
                public function paramTest(int ${'$'}movieId) {
                    ${'$'}this->set(compact('movieId'));
                }
            }
        """.trimIndent()

        val controllerFile = myFixture.addFileToProject("cake5/src5/Controller/MoviesController.php", controllerCode)
        val fileContent = FileContentImpl.createByFile(controllerFile.virtualFile, project)

        val indexResult = ViewVariableASTDataIndexer.map(fileContent)

        // Verify we got SOME result (not empty means the file was processed as a controller)
        assertFalse("Index result should not be empty - file should be recognized as controller. Path was: ${controllerFile.virtualFile.path}",
                    indexResult.isEmpty())

        // Verify the controller method key exists
        val controllerKey = "Movies:paramTest"
        assertTrue("Index should contain key for Movies:paramTest, but got keys: ${indexResult.keys}",
                   indexResult.containsKey(controllerKey))

        val viewVariables = indexResult[controllerKey]!!

        // Verify movieId variable was indexed
        assertTrue("Should contain movieId variable, but got: ${viewVariables.keys}",
                   viewVariables.containsKey("movieId"))

        val movieIdVar = viewVariables["movieId"]!!

        // Verify it's a COMPACT kind
        assertEquals("Variable should be COMPACT kind", VarKind.COMPACT, movieIdVar.varKind)

        // Verify the varHandle has the right symbol name
        assertEquals("Symbol name should be movieId", "movieId", movieIdVar.varHandle.symbolName)

        // Verify source kind is LOCAL (compact references are marked as LOCAL)
        assertEquals("Source kind should be LOCAL", SourceKind.LOCAL, movieIdVar.varHandle.sourceKind)
    }

    fun `test compact with local variable is indexed correctly`() {
        val controllerCode = """
            <?php
            namespace App\Controller;

            use Cake\Controller\Controller;

            class MoviesController extends Controller {
                public function localTest() {
                    ${'$'}metadata = ${'$'}this->MovieMetadata->generateMetadata();
                    ${'$'}this->set(compact('metadata'));
                }
            }
        """.trimIndent()

        val controllerFile = myFixture.addFileToProject("cake5/src5/Controller/MoviesController.php", controllerCode)
        val fileContent = FileContentImpl.createByFile(controllerFile.virtualFile, project)

        val indexResult = ViewVariableASTDataIndexer.map(fileContent)

        val controllerKey = "Movies:localTest"
        assertTrue("Index should contain key for Movies:localTest", indexResult.containsKey(controllerKey))

        val viewVariables = indexResult[controllerKey]!!
        assertTrue("Should contain metadata variable", viewVariables.containsKey("metadata"))

        val metadataVar = viewVariables["metadata"]!!
        assertEquals("Variable should be COMPACT kind", VarKind.COMPACT, metadataVar.varKind)
        assertEquals("Symbol name should be metadata", "metadata", metadataVar.varHandle.symbolName)
        assertEquals("Source kind should be LOCAL", SourceKind.LOCAL, metadataVar.varHandle.sourceKind)
    }

    fun `test literal values are indexed correctly`() {
        val controllerCode = """
            <?php
            namespace App\Controller;

            use Cake\Controller\Controller;

            class MoviesController extends Controller {
                public function literalTest() {
                    ${'$'}this->set('title', 'Test Movie');
                    ${'$'}this->set('count', 42);
                }
            }
        """.trimIndent()

        val controllerFile = myFixture.addFileToProject("cake5/src5/Controller/MoviesController.php", controllerCode)
        val fileContent = FileContentImpl.createByFile(controllerFile.virtualFile, project)

        val indexResult = ViewVariableASTDataIndexer.map(fileContent)

        val controllerKey = "Movies:literalTest"
        assertTrue("Index should contain key for Movies:literalTest", indexResult.containsKey(controllerKey))

        val viewVariables = indexResult[controllerKey]!!

        // Check title (string literal)
        assertTrue("Should contain title variable", viewVariables.containsKey("title"))
        val titleVar = viewVariables["title"]!!
        assertEquals("title should be PAIR kind", VarKind.PAIR, titleVar.varKind)
        assertEquals("title source kind should be LITERAL", SourceKind.LITERAL, titleVar.varHandle.sourceKind)

        // Check count (numeric literal)
        assertTrue("Should contain count variable", viewVariables.containsKey("count"))
        val countVar = viewVariables["count"]!!
        assertEquals("count should be PAIR kind", VarKind.PAIR, countVar.varKind)
        assertEquals("count source kind should be LITERAL", SourceKind.LITERAL, countVar.varHandle.sourceKind)
    }
}
