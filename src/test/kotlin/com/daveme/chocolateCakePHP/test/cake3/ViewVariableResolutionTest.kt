package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableASTDataIndexer
import com.intellij.util.indexing.FileContentImpl

class ViewVariableResolutionTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/vendor/cakephp.php"
        )
    }

    fun `test compact with parameter resolves type correctly`() {
        val controllerCode = """
            <?php
            namespace App\Controller;
            use Cake\Controller\Controller;

            class TestController extends Controller {
                public function testAction(int ${'$'}movieId) {
                    ${'$'}this->set(compact('movieId'));
                }
            }
        """.trimIndent()

        // Create controller file
        val controllerFile = myFixture.addFileToProject("cake3/src/Controller/TestController.php", controllerCode)

        // Index it
        val fileContent = FileContentImpl.createByFile(controllerFile.virtualFile, project)
        val indexResult = ViewVariableASTDataIndexer.map(fileContent)

        // Verify it was indexed
        val controllerKey = "Test:testAction"
        assertTrue("Should have indexed Test:testAction", indexResult.containsKey(controllerKey))

        val variables = indexResult[controllerKey]!!
        assertTrue("Should have movieId variable", variables.containsKey("movieId"))

        val movieIdVar = variables["movieId"]!!

        // Now test type resolution
        val resolvedType = movieIdVar.resolveType(project, controllerFile)

        assertNotNull("Resolved type should not be null", resolvedType)
        val typeString = resolvedType.toString()

        // The type should contain "int" since the parameter is typed as int
        assertTrue("Type should contain 'int' but got: $typeString",
                   typeString.contains("int", ignoreCase = true) || typeString.contains("integer", ignoreCase = true))
    }
}
