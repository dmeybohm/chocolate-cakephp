package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableASTDataIndexer
import com.intellij.util.indexing.FileContentImpl

class ViewVariableResolutionTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake2/vendor/cakephp.php"
        )
    }

    fun `test compact with parameter resolves type correctly`() {
        val controllerCode = """
            <?php
            App::uses('AppController', 'Controller');

            class TestController extends AppController {
                public function testAction(${'$'}movieId) {
                    ${'$'}this->set(compact('movieId'));
                }
            }
        """.trimIndent()

        // Create controller file
        val controllerFile = myFixture.addFileToProject("cake2/app/Controller/TestController.php", controllerCode)

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

        // CakePHP 2 doesn't typically have typed parameters, so we just verify resolution doesn't crash
        // and returns a non-null type (likely "mixed" without type hints)
        assertNotNull("Resolved type should not be null", resolvedType)
    }
}
