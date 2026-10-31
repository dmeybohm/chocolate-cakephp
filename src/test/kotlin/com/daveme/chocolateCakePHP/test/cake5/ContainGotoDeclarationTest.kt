package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.model.ContainGotoDeclarationHandler
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class ContainGotoDeclarationTest : Cake5BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/ArticleController.php",
            "cake5/src5/Model/Table/ArticlesTable.php",
            "cake5/src5/Model/Table/AuthorsTable.php",
            "cake5/src5/Model/Table/CommentsTable.php",
            "cake5/src5/Model/Table/MoviesTable.php",
            "cake5/src5/Model/Table/ProfilesTable.php",
            "cake5/src5/Model/Table/ReviewsTable.php",
            "cake5/src5/Model/Table/UsersTable.php",
            "cake5/vendor/test/test_plugin/src/Model/Table/PluginItemsTable.php",
            "cake5/vendor/test/test_plugin/src/Model/Table/RatingsTable.php"
        )
    }

    fun `test can navigate to table from contain string parameter`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
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
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
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
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
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

        val handler = ContainGotoDeclarationHandler()
        val targets = gotoDeclarationHandlerTargets(handler)
        assertNotNull(targets)
        assertEmpty(targets!!)
    }

    fun `test navigates to first part of nested association`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
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
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
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
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
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
        myFixture.configureByFilePathAndText("cake5/src5/Model/Table/ArticlesTable.php", """
        <?php
        namespace App\Model\Table;

        use Cake\ORM\Query\SelectQuery;
        use Cake\ORM\Table;

        class ArticlesTable extends Table
        {
            public function findTopRated(SelectQuery ${'$'}query): SelectQuery
            {
                return ${'$'}query->contain('<caret>Authors');
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "AuthorsTable.php")
    }

    fun `test can navigate to plugin table from contain`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}query = ${'$'}articles->find();
                ${'$'}query->contain('<caret>PluginItems');
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "PluginItemsTable.php")
    }

    fun `test navigation works in custom finder method with array`() {
        myFixture.configureByFilePathAndText("cake5/src5/Model/Table/ArticlesTable.php", """
        <?php
        namespace App\Model\Table;

        use Cake\ORM\Query\SelectQuery;
        use Cake\ORM\Table;

        class ArticlesTable extends Table
        {
            public function findTopRated(SelectQuery ${'$'}query): SelectQuery
            {
                return ${'$'}query->contain(['<caret>Comments', 'Authors']);
            }
        }
        """.trimIndent())

        val handler = ContainGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "CommentsTable.php")
    }

    private fun assertReviewsQueryGoesTo(call: String, filename: String) {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}this->fetchTable('Reviews')->find()->$call;
            }
        }
        """.trimIndent())
        assertGotoDeclarationHandlerGoesToFilename(ContainGotoDeclarationHandler(), filename)
    }

    private fun assertReviewsQueryGoesNowhere(call: String) {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}this->fetchTable('Reviews')->find()->$call;
            }
        }
        """.trimIndent())
        assertEmpty(gotoDeclarationHandlerTargets(ContainGotoDeclarationHandler()).orEmpty().toList())
    }

    fun `test undeclared association on a configured table navigates nowhere`() {
        assertReviewsQueryGoesNowhere("contain('<caret>Articles')")
    }

    fun `test segment after an undeclared association navigates nowhere`() {
        assertReviewsQueryGoesNowhere("contain('Articles.<caret>Authors')")
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
