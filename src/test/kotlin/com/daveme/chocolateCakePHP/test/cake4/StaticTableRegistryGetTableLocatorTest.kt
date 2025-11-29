package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.model.TableLocatorGotoDeclarationHandler
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class StaticTableRegistryGetTableLocatorTest : Cake4BaseTestCase() {

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

    fun `test static TableLocator get returns methods from the users custom namespace`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;

        class MovieController extends Controller
        {
            public function artist() {
                TableRegistry::getTableLocator()->get('Articles')-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("myCustomArticleMethod"))
    }

    fun `test TableRegistry static method argument can be autocompleted with quotes`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;

        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}result = TableRegistry::getTableLocator()->get('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("Articles"))
    }

    fun `test get returns entity from associated table through TableRegistry`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;

        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}article = TableRegistry::getTableLocator()
                    ->get('Movies')
                    ->Articles
                    ->get(${'$'}id);
                ${'$'}article-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
    }

    fun `test get returns entity from associated table through TableRegistry and intermediate var1`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;

        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}movies = TableRegistry::getTableLocator()->get('Movies');
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

    fun `test get returns entity from associated table through TableRegistry and intermediate var2`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;

        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}locator = TableRegistry::getTableLocator();
                ${'$'}movies = ${'$'}locator->get('Movies')
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

    fun `test goto declaration from TableRegistry getTableLocator get method`() {
        myFixture.configureByFilePathAndText("cake4/src4/Controller/MovieController.php", """
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
}
