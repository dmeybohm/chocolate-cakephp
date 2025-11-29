package com.daveme.chocolateCakePHP.test.cake3

class LocatorAwareTraitTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/src/Model/Entity/Article.php",
            "cake3/src/Model/Entity/Movie.php",
            "cake3/vendor/cakephp.php"
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
