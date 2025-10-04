package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class ContainCompletionTest : Cake3BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/ArticleController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/src/Model/Table/AuthorsTable.php",
            "cake3/src/Model/Table/CommentsTable.php",
            "cake3/src/Model/Table/MoviesTable.php",
            "cake3/vendor/cakephp.php"
        )
    }

    fun `test completing table names in contain string parameter`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->getTableLocator()->get('Articles');
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
        myFixture.configureByFilePathAndText("cake3/src/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->getTableLocator()->get('Articles');
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
        myFixture.configureByFilePathAndText("cake3/src/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->getTableLocator()->get('Articles');
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
        myFixture.configureByFilePathAndText("cake3/src/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->getTableLocator()->get('Articles');
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

}
