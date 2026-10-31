package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.model.ContainGotoDeclarationHandler
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class ContainGotoDeclarationTest : Cake3BaseTestCase() {
    override fun setUpTestFiles() {
        configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/ArticleController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/src/Model/Table/AuthorsTable.php",
            "cake3/src/Model/Table/CommentsTable.php",
            "cake3/src/Model/Table/MoviesTable.php",
            "cake3/src/Model/Table/ProfilesTable.php",
            "cake3/src/Model/Table/ReviewsTable.php",
            "cake3/src/Model/Table/UsersTable.php",
            "cake3/vendor/cakephp.php",
            "cake3/vendor/test/test_plugin/src/Model/Table/PluginItemsTable.php",
            "cake3/vendor/test/test_plugin/src/Model/Table/RatingsTable.php"
        )
    }

    fun `test can navigate to table from contain string parameter`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->getTableLocator()->get('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain('<caret>Authors');
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "AuthorsTable.php")
    }

    fun `test can navigate to table from contain array parameter`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->getTableLocator()->get('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain(['<caret>Comments']);
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "CommentsTable.php")
    }

    fun `test can navigate from second element in array`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->getTableLocator()->get('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain(['Authors', '<caret>Comments']);
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "CommentsTable.php")
    }

    fun `test does not navigate on empty string`() {
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

        val handler = ContainGotoDeclarationHandler()
        val targets = gotoDeclarationHandlerTargets(handler)
        assertNotNull(targets)
        assertEmpty(targets!!)
    }

    fun `test navigates to first part of nested association`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->getTableLocator()->get('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain('<caret>Authors.Addresses');
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "AuthorsTable.php")
    }

    fun `test navigation works from query find method`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->getTableLocator()->get('Articles');
                ${'$'}articles->find()->contain('<caret>Movies');
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "MoviesTable.php")
    }

    fun `test navigation works in custom finder method`() {
        myFixture.configureByFilePathAndText("cake3/src/Model/Table/ArticlesTable.php", """
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

    fun `test can navigate to plugin table from contain`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->getTableLocator()->get('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain('<caret>PluginItems');
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "PluginItemsTable.php")
    }

    fun `test navigation works in custom finder method with array`() {
        myFixture.configureByFilePathAndText("cake3/src/Model/Table/ArticlesTable.php", """
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

    private fun assertReviewsQueryGoesTo(call: String, filename: String) {
        myFixture.configureByFilePathAndText("cake3/src/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}this->getTableLocator()->get('Reviews')->find()->$call;
            }
        }
        """.trimIndent())
        assertGotoDeclarationHandlerGoesToFilename(ContainGotoDeclarationHandler(), filename)
    }

    fun `test alias navigates to the table it resolves to`() {
        assertReviewsQueryGoesTo("contain('<caret>Authors')", "UsersTable.php")
    }

    fun `test addAssociations alias navigates to the table it resolves to`() {
        assertReviewsQueryGoesTo("contain('<caret>Editors')", "UsersTable.php")
    }

    fun `test plugin alias navigates to the plugin table`() {
        assertReviewsQueryGoesTo("contain('<caret>Ratings')", "RatingsTable.php")
    }

    fun `test caret in the first segment navigates to the first hop`() {
        assertReviewsQueryGoesTo("contain('Auth<caret>ors.Profiles')", "UsersTable.php")
    }

    fun `test caret in the second segment navigates by the second hop`() {
        assertReviewsQueryGoesTo("contain('Authors.Prof<caret>iles')", "ProfilesTable.php")
    }

    fun `test caret at the start of the second segment navigates by the second hop`() {
        assertReviewsQueryGoesTo("contain('Authors.<caret>Profiles')", "ProfilesTable.php")
    }

    fun `test matching navigates like contain`() {
        assertReviewsQueryGoesTo("matching('<caret>Authors')", "UsersTable.php")
    }

    fun `test array key navigates to the table it resolves to`() {
        assertReviewsQueryGoesTo("contain(['<caret>Authors' => ['Profiles']])", "UsersTable.php")
    }

    fun `test array under a key navigates by the key`() {
        assertReviewsQueryGoesTo("contain(['Authors' => ['<caret>Profiles']])", "ProfilesTable.php")
    }

    fun `test dotted segment under a key navigates by the key`() {
        assertReviewsQueryGoesTo("contain(['Authors' => ['Reviews.<caret>Editors']])", "UsersTable.php")
    }
}
