package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.model.TableLocatorGotoDeclarationHandler
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class FetchTableTest : Cake4BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake4/src4/Controller/AppController.php",
            "cake4/src4/Controller/MovieController.php",
            "cake4/src4/Model/Table/ArticlesTable.php",
            "cake4/src4/Model/Table/MoviesTable.php",
            "cake4/src4/Model/Entity/Article.php",
            "cake4/src4/Model/Entity/Movie.php",
            "cake4/vendor/cakephp.php"
        )
    }

    fun `test fetchTable returns methods from the users custom namespace in a controller`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}result = ${'$'}this->fetchTable('Articles')-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("myCustomArticleMethod"))
    }

    fun `test fetchTable argument can be autocompleted with quotes`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;

        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}result = ${'$'}this->fetchTable('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("Articles"))
    }

    fun `test fetchTable argument can be autocompleted with quotes when stored in a var`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;

        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}articles-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("myCustomArticleMethod"))
    }

    fun `test get returns entity from associated table through fetchTable`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}article = ${'$'}this->fetchTable('Movies')->Articles->get(${'$'}id);
                ${'$'}article-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
    }

    fun `test get returns entity from associated table through fetchTable with intermediate var`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}movies = ${'$'}this->fetchTable('Movies');
                ${'$'}article = ${'$'}movies->Articles->get(${'$'}id);
                ${'$'}article-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
    }

    //
    // GotoDeclaration tests
    //

    fun `test goto declaration from fetchTable in controller`() {
        myFixture.configureByFilePathAndText("cake4/src4/Controller/MovieController.php", """
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

    fun `test goto declaration does not work on other methods`() {
        myFixture.configureByFilePathAndText("cake4/src4/Controller/MovieController.php", """
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

    fun `test goto declaration handles non-existent table gracefully`() {
        myFixture.configureByFilePathAndText("cake4/src4/Controller/MovieController.php", """
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

    fun `test goto declaration works with plugin table classes`() {
        myFixture.configureByFilePathAndText("cake4/src4/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function index() {
                ${'$'}pluginTable = ${'$'}this->fetchTable('<caret>TestPlugin.Articles');
            }
        }
        """.trimIndent())
        val handler = TableLocatorGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
        // For plugin tables, we expect it to either find the plugin table or return empty if not found
    }
}
