package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileDataIndexer
import com.intellij.util.indexing.FileContentImpl

/**
 * Direct tests of the view file indexer for template names given through local variables.
 * Each test indexes a MovieController written inline and asserts on the produced keys.
 */
class ViewFileDataIndexerVariableTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/vendor/cakephp.php"
        )
    }

    private fun indexKeysOfController(methodBody: String, methodSignature: String = "public function index()"): Set<String> {
        val controllerCode = """
            <?php
            namespace App\Controller;

            use Cake\Controller\Controller;

            class MovieController extends Controller {
                $methodSignature {
            $methodBody
                }
            }
        """.trimIndent()
        val controllerFile = myFixture.addFileToProject("cake5/src5/Controller/MovieController.php", controllerCode)
        val fileContent = FileContentImpl.createByFile(controllerFile.virtualFile, project)
        return ViewFileDataIndexer.map(fileContent).keys
    }

    private fun assertKeys(keys: Set<String>, vararg expected: String) {
        for (key in expected) {
            assertTrue("Expected key '$key' in $keys", keys.contains(key))
        }
    }

    private fun assertNoKeys(keys: Set<String>, vararg unexpected: String) {
        for (key in unexpected) {
            assertFalse("Did not expect key '$key' in $keys", keys.contains(key))
        }
    }

    fun `test variable assigned a literal resolves in setTemplate`() {
        val keys = indexKeysOfController("""
            ${'$'}template = 'artist';
            ${'$'}this->viewBuilder()->setTemplate(${'$'}template);
        """)
        assertKeys(keys, "Movie/artist")
    }

    fun `test variable assigned a literal resolves in render`() {
        val keys = indexKeysOfController("""
            ${'$'}template = 'artist';
            ${'$'}this->render(${'$'}template);
        """)
        assertKeys(keys, "Movie/artist")
    }

    fun `test variable assigned a literal resolves in view field assignment`() {
        val keys = indexKeysOfController("""
            ${'$'}template = 'artist';
            ${'$'}this->view = ${'$'}template;
        """)
        assertKeys(keys, "Movie/artist")
    }

    fun `test assignments in both branches of an if else are collected`() {
        val keys = indexKeysOfController("""
            if (${'$'}this->request->is('ajax')) {
                ${'$'}template = 'one';
            } else {
                ${'$'}template = 'two';
            }
            ${'$'}this->viewBuilder()->setTemplate(${'$'}template);
        """)
        assertKeys(keys, "Movie/one", "Movie/two")
    }

    fun `test ternary of two variables resolves both`() {
        val keys = indexKeysOfController("""
            ${'$'}one = 'one';
            ${'$'}two = 'two';
            ${'$'}this->viewBuilder()->setTemplate(${'$'}this->request->is('ajax') ? ${'$'}one : ${'$'}two);
        """)
        assertKeys(keys, "Movie/one", "Movie/two")
    }

    fun `test variable assigned a ternary resolves both branches`() {
        val keys = indexKeysOfController("""
            ${'$'}template = ${'$'}this->request->is('ajax') ? 'one' : 'two';
            ${'$'}this->render(${'$'}template);
        """)
        assertKeys(keys, "Movie/one", "Movie/two")
    }

    fun `test variable assigned another variable resolves through the chain`() {
        val keys = indexKeysOfController("""
            ${'$'}first = 'artist';
            ${'$'}second = ${'$'}first;
            ${'$'}this->viewBuilder()->setTemplate(${'$'}second);
        """)
        assertKeys(keys, "Movie/artist")
    }

    fun `test assignment after the use is ignored`() {
        val keys = indexKeysOfController("""
            ${'$'}template = 'one';
            ${'$'}this->viewBuilder()->setTemplate(${'$'}template);
            ${'$'}template = 'two';
        """)
        assertKeys(keys, "Movie/one")
        assertNoKeys(keys, "Movie/two")
    }

    fun `test variable resolves in setTemplatePath`() {
        val keys = indexKeysOfController("""
            ${'$'}path = 'Movie/Nested';
            ${'$'}this->viewBuilder()->setTemplatePath(${'$'}path);
            ${'$'}this->viewBuilder()->setTemplate('custom');
        """)
        assertKeys(keys, "Movie/Nested/custom")
    }

    fun `test assignment inside a closure is ignored`() {
        val keys = indexKeysOfController("""
            ${'$'}template = 'outer';
            ${'$'}callback = function () use (&${'$'}template) {
                ${'$'}template = 'inner';
            };
            ${'$'}this->viewBuilder()->setTemplate(${'$'}template);
        """)
        assertKeys(keys, "Movie/outer")
        assertNoKeys(keys, "Movie/inner")
    }

    fun `test method parameter yields no template`() {
        val keys = indexKeysOfController("""
            ${'$'}this->viewBuilder()->setTemplate(${'$'}template);
        """, methodSignature = "public function index(${'$'}template)")
        // Only the implicit view for the action itself is indexed
        assertEquals(setOf("Movie/index"), keys)
    }

    fun `test self referencing assignment terminates`() {
        val keys = indexKeysOfController("""
            ${'$'}template = ${'$'}template ?: 'fallback';
            ${'$'}this->viewBuilder()->setTemplate(${'$'}template);
        """)
        assertKeys(keys, "Movie/fallback")
    }

    fun `test mutually referencing assignments terminate`() {
        val keys = indexKeysOfController("""
            ${'$'}a = 'artist';
            ${'$'}b = ${'$'}a;
            ${'$'}a = ${'$'}b;
            ${'$'}this->viewBuilder()->setTemplate(${'$'}a);
        """)
        assertKeys(keys, "Movie/artist")
    }

    fun `test compound assignment is ignored`() {
        val keys = indexKeysOfController("""
            ${'$'}template = 'one';
            ${'$'}template .= '_more';
            ${'$'}this->viewBuilder()->setTemplate(${'$'}template);
        """)
        assertKeys(keys, "Movie/one")
        assertNoKeys(keys, "Movie/_more", "Movie/one_more")
    }

    fun `test this is never resolved as a variable`() {
        val keys = indexKeysOfController("""
            ${'$'}this->viewBuilder()->setTemplate(${'$'}this);
        """)
        assertEquals(setOf("Movie/index"), keys)
    }

    fun `test variable resolves in element call at view file scope`() {
        val viewCode = """
            <?php
            ${'$'}name = 'sidebar';
            echo ${'$'}this->element(${'$'}name);
        """.trimIndent()
        // The element directory must exist for the element path prefix to resolve
        myFixture.copyFileToProject("cake5/templates/element/breadcrumb.php")
        val viewFile = myFixture.addFileToProject("cake5/templates/Movie/index.php", viewCode)
        val fileContent = FileContentImpl.createByFile(viewFile.virtualFile, project)
        val keys = ViewFileDataIndexer.map(fileContent).keys
        assertKeys(keys, "element/sidebar")
    }
}
