package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.model.ContainGotoDeclarationHandler
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class ContainGotoDeclarationTest : Cake4BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake4/src4/Controller/ArticleController.php",
            "cake4/src4/Model/Table/ArticlesTable.php",
            "cake4/src4/Model/Table/AuthorsTable.php",
            "cake4/src4/Model/Table/CommentsTable.php",
            "cake4/src4/Model/Table/MoviesTable.php",
            "cake4/vendor/cakephp.php"
        )
    }

    fun `test can navigate to table from contain string parameter`() {
        myFixture.configureByFilePathAndText("cake4/src4/Controller/ArticleController.php", """
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
        myFixture.configureByFilePathAndText("cake4/src4/Controller/ArticleController.php", """
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
        myFixture.configureByFilePathAndText("cake4/src4/Controller/ArticleController.php", """
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
        myFixture.configureByFilePathAndText("cake4/src4/Controller/ArticleController.php", """
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
        myFixture.configureByFilePathAndText("cake4/src4/Controller/ArticleController.php", """
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
        myFixture.configureByFilePathAndText("cake4/src4/Controller/ArticleController.php", """
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
        myFixture.configureByFilePathAndText("cake4/src4/Controller/ArticleController.php", """
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

    fun `test navigation works in custom finder method`() {
        myFixture.configureByFilePathAndText("cake4/src4/Model/Table/ArticlesTable.php", """
        <?php
        namespace App\Model\Table;

        use Cake\ORM\Query;
        use Cake\ORM\Table;

        class ArticlesTable extends Table
        {
            public function findTopRated(Query ${'$'}query): Query
            {
                return ${'$'}query->contain('<caret>Authors');
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "AuthorsTable.php")
    }

    fun `test navigation works in custom finder method with array`() {
        myFixture.configureByFilePathAndText("cake4/src4/Model/Table/ArticlesTable.php", """
        <?php
        namespace App\Model\Table;

        use Cake\ORM\Query;
        use Cake\ORM\Table;

        class ArticlesTable extends Table
        {
            public function findTopRated(Query ${'$'}query): Query
            {
                return ${'$'}query->contain(['<caret>Comments', 'Authors']);
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "CommentsTable.php")
    }
}
