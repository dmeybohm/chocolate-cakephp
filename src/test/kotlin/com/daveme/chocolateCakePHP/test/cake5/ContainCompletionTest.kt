package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class ContainCompletionTest : Cake5BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/ArticleController.php",
            "cake5/src5/Model/Table/ArticlesTable.php",
            "cake5/src5/Model/Table/AuthorsTable.php",
            "cake5/src5/Model/Table/CommentsTable.php",
            "cake5/src5/Model/Table/MoviesTable.php",
            "cake5/vendor/cakephp.php"
        )
    }

    fun `test completing table names in contain string parameter`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
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

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("Articles"))
        assertTrue(result.contains("Authors"))
        assertTrue(result.contains("Comments"))
        assertTrue(result.contains("Movies"))
    }

    fun `test completing table names in contain array parameter`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain(['<caret>']);
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("Articles"))
        assertTrue(result.contains("Authors"))
        assertTrue(result.contains("Comments"))
    }

    fun `test completing second element in array`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain(['Authors', '<caret>']);
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("Articles"))
        assertTrue(result.contains("Comments"))
    }

    fun `test contain completion from query find method`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}articles->find()->contain('<caret>');
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("Authors"))
        assertTrue(result.contains("Comments"))
    }

    fun `test contain completion works on table object directly`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}articles->contain('<caret>');
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("Authors"))
        assertTrue(result.contains("Comments"))
    }
}
