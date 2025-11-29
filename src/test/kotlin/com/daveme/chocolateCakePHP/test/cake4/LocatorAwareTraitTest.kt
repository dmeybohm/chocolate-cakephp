package com.daveme.chocolateCakePHP.test.cake4

class LocatorAwareTraitTest : Cake4BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake4/src4/Controller/AppController.php",
            "cake4/src4/Model/Table/ArticlesTable.php",
            "cake4/src4/Model/Entity/Article.php",
            "cake4/src4/Model/Entity/Movie.php",
            "cake4/vendor/cakephp.php"
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
