package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.model.ContainGotoDeclarationHandler

class ContainGotoDeclarationTest : Cake5BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Model/Table/ArticlesTable.php",
            "cake5/src5/Model/Table/AuthorsTable.php",
            "cake5/src5/Model/Table/CommentsTable.php",
            "cake5/src5/Model/Table/MoviesTable.php",
            "cake5/vendor/cakephp.php"
        )
    }

    fun `test can navigate to table from contain string parameter`() {
        myFixture.configureByText("ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain('<caret>Authors');
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "AuthorsTable.php")
    }

    fun `test can navigate to table from contain array parameter`() {
        myFixture.configureByText("ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain(['<caret>Comments']);
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "CommentsTable.php")
    }

    fun `test can navigate from second element in array`() {
        myFixture.configureByText("ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain(['Authors', '<caret>Comments']);
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "CommentsTable.php")
    }

    fun `test does not navigate on empty string`() {
        myFixture.configureByText("ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain('<caret>');
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        val targets = gotoDeclarationHandlerTargets(handler)
        assertNotNull(targets)
        assertEmpty(targets!!)
    }

    fun `test navigates to first part of nested association`() {
        myFixture.configureByText("ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain('<caret>Authors.Addresses');
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "AuthorsTable.php")
    }

    fun `test navigation works from query find method`() {
        myFixture.configureByText("ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}articles->find()->contain('<caret>Movies');
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "MoviesTable.php")
    }

    fun `test navigation works on table object directly`() {
        myFixture.configureByText("ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}articles->contain('<caret>Authors');
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "AuthorsTable.php")
    }
}
