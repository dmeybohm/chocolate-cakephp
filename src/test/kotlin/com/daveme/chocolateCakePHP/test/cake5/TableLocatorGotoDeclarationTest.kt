package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.model.TableLocatorGotoDeclarationHandler
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.jetbrains.php.lang.psi.elements.PhpClass

class TableLocatorGotoDeclarationTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Controller/MovieController.php",
            "cake5/src5/Model/Table/MoviesTable.php",
            "cake5/src5/Model/Table/ArticlesTable.php",
            "cake5/vendor/cakephp.php",
        )
    }

    fun `test goto declaration from fetchTable in controller`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function filmDirector() {
                ${'$'}moviesTable = ${'$'}this->fetchTable('<caret>Movies');
                ${'$'}this->set(compact('moviesTable'));
            }
        }
        """.trimIndent())
        val handler = TableLocatorGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToTableClass(handler, "MoviesTable")
    }

    fun `test goto declaration from getTableLocator get method`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function index() {
                ${'$'}obj = new SomeOtherObject();
                ${'$'}result = ${'$'}obj->fetchTable('<caret>Movies');
            }
        }
        """.trimIndent())
        val handler = TableLocatorGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
        assertTrue(elements!!.isEmpty())
    }

    fun `test goto declaration does not work on non-get methods on TableLocator`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function index() {
                ${'$'}nonExistent = ${'$'}this->fetchTable('<caret>NonExistentTable');
            }
        }
        """.trimIndent())
        val handler = TableLocatorGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
        assertTrue(elements!!.isEmpty())
    }
}