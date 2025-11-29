package com.daveme.chocolateCakePHP.test.cake5

class LocatorAwareTraitTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Model/Table/ArticlesTable.php",
            "cake5/src5/Model/Entity/Article.php",
            "cake5/src5/Model/Entity/Movie.php",
            "cake5/vendor/cakephp.php"
        )
    }

    fun `test get returns entity`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}movie = ${'$'}this->Movies->get(${'$'}id);
                ${'$'}movie-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someMovieMethod"))
    }

    fun `test get returns entity from associated table`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}article = ${'$'}this->Movies->Articles->get(${'$'}id);
                ${'$'}article-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
    }
}
