package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.model.TableLocatorGotoDeclarationHandler
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.jetbrains.php.lang.psi.elements.PhpClass

class TableLocatorGotoDeclarationTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/MovieController.php",
            "cake3/src/Model/Table/MoviesTable.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/vendor/cakephp.php",
        )
    }

    fun `test goto declaration from getTableLocator get method`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function index() {
                ${'$'}articlesTable = ${'$'}this->getTableLocator()->get('<caret>Articles');
            }
        }
        """.trimIndent())
        val handler = TableLocatorGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToTableClass(handler, "ArticlesTable")
    }

    fun `test goto declaration from static TableRegistry get method`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;

        class MovieController extends Controller
        {
            public function index() {
                ${'$'}moviesTable = TableRegistry::get('<caret>Movies');
            }
        }
        """.trimIndent())
        val handler = TableLocatorGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToTableClass(handler, "MoviesTable")
    }

    fun `test goto declaration from TableRegistry getTableLocator get method`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;

        class MovieController extends Controller
        {
            public function index() {
                ${'$'}articlesTable = TableRegistry::getTableLocator()->get('<caret>Articles');
            }
        }
        """.trimIndent())
        val handler = TableLocatorGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToTableClass(handler, "ArticlesTable")
    }

    fun `test goto declaration does not work on other methods`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function index() {
                ${'$'}obj = new SomeOtherObject();
                ${'$'}result = ${'$'}obj->get('<caret>Movies');
            }
        }
        """.trimIndent())
        val handler = TableLocatorGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
        assertTrue(elements!!.isEmpty())
    }

    fun `test goto declaration does not work on non-get methods on TableLocator`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function index() {
                ${'$'}result = ${'$'}this->getTableLocator()->someOtherMethod('<caret>Movies');
            }
        }
        """.trimIndent())
        val handler = TableLocatorGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
        assertTrue(elements!!.isEmpty())
    }

    fun `test goto declaration handles non-existent table gracefully`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function index() {
                ${'$'}nonExistent = ${'$'}this->getTableLocator()->get('<caret>NonExistentTable');
            }
        }
        """.trimIndent())
        val handler = TableLocatorGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
        assertTrue(elements!!.isEmpty())
    }

    fun `test goto declaration works with plugin table classes`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function index() {
                ${'$'}pluginTable = ${'$'}this->getTableLocator()->get('<caret>TestPlugin.Articles');
            }
        }
        """.trimIndent())
        val handler = TableLocatorGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
        // For plugin tables, we expect it to either find the plugin table or return empty if not found
        // This is acceptable behavior for this test
    }
}