package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.CakeTwoPluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.codeInsight.lookup.LookupElementPresentation

class ContainCompletionTest : Cake5BaseTestCase() {
    override fun setUpTestFiles() {
        configureByFiles(
            "cake5/src5/Controller/ArticleController.php",
            "cake5/src5/Model/Table/ArticlesTable.php",
            "cake5/src5/Model/Table/AuthorsTable.php",
            "cake5/src5/Model/Table/CommentsTable.php",
            "cake5/src5/Model/Table/MoviesTable.php",
            "cake5/src5/Model/Table/RatingsTable.php",
            "cake5/src5/Model/Table/ReviewsTable.php",
            "cake5/src5/Model/Table/UsersTable.php",
            "cake5/vendor/cakephp.php",
            "cake5/vendor/test/test_plugin/src/Model/Table/PluginItemsTable.php",
            "cake5/vendor/test/test_plugin/src/Model/Table/RatingsTable.php"
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

    fun `test completing plugin table names in contain`() {
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
        assertTrue(result!!.contains("PluginItems"))
    }

    fun `test cakephp 2 plugin does not widen contain completion`() {
        myFixture.addFileToProject("cake5/lib/Stray/StrayTable.php", """
        <?php
        namespace Vendor\Stray\Model\Table;

        class StrayTable extends \Cake\ORM\Table {}
        """.trimIndent())
        val settings = Settings.getInstance(myFixture.project)
        val state = settings.state.copy()
        state.pluginConfigs = state.pluginConfigs +
            CakeTwoPluginConfig(pluginName = "DebugKit", pluginPath = "app/Plugin/DebugKit")
        settings.loadState(state)

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
        assertTrue(result!!.contains("Articles"))
        assertFalse(result.contains("Stray"))
    }

    fun `test contain completion in custom finder method`() {
        myFixture.configureByFilePathAndText("cake5/src5/Model/Table/ArticlesTable.php", """
        <?php
        namespace App\Model\Table;

        use Cake\ORM\Query\SelectQuery;
        use Cake\ORM\Table;

        class ArticlesTable extends Table
        {
            public function findTopRated(SelectQuery ${'$'}query): SelectQuery
            {
                return ${'$'}query->contain('<caret>');
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

    private fun completeInController(body: String): List<String> {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/ArticleController.php", """
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                $body
            }
        }
        """.trimIndent())
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    private fun assertOffersOnlyReviewsAssociations(result: List<String>) {
        assertSameElements(result, "Movies", "Authors", "Ratings", "Editors", "Comments")
    }

    fun `test contain offers the configured aliases of the receiver`() {
        val result = completeInController("\$this->fetchTable('Reviews')->find()->contain('<caret>');")
        assertOffersOnlyReviewsAssociations(result)

        val authors = myFixture.lookupElements!!.first { it.lookupString == "Authors" }
        val presentation = LookupElementPresentation()
        authors.renderElement(presentation)
        assertEquals("UsersTable", presentation.typeText)
    }

    fun `test contain on a controller table property offers its aliases`() {
        assertOffersOnlyReviewsAssociations(completeInController("\$this->Reviews->find()->contain('<caret>');"))
    }

    fun `test contain on a query from an association chain offers its aliases`() {
        assertOffersOnlyReviewsAssociations(completeInController("\$this->Movies->Reviews->find()->contain('<caret>');"))
    }

    fun `test contain after another query method keeps the receiver`() {
        assertOffersOnlyReviewsAssociations(
            completeInController("\$this->fetchTable('Reviews')->find()->matching('Authors')->contain('<caret>');")
        )
    }

    fun `test each association path method offers the aliases`() {
        for (method in listOf("matching", "notMatching", "innerJoinWith", "leftJoinWith")) {
            assertOffersOnlyReviewsAssociations(
                completeInController("\$this->fetchTable('Reviews')->find()->$method('<caret>');")
            )
        }
    }

    fun `test dotted path offers the associations of the hop`() {
        val result = completeInController("\$this->fetchTable('Reviews')->find()->contain('Authors.<caret>');")
        assertSameElements(result, "Profiles", "Reviews")
    }

    fun `test dotted path matches and inserts only the last segment`() {
        completeInController("\$this->fetchTable('Reviews')->find()->contain('Authors.Pro<caret>');")
        myFixture.checkResult("""
        <?php
        namespace App\Controller;

        use Cake\Controller\Controller;

        class ArticleController extends Controller
        {
            public function index() {
                ${'$'}this->fetchTable('Reviews')->find()->contain('Authors.Profiles');
            }
        }
        """.trimIndent())
    }

    fun `test dotted path through an unconfigured table offers the convention list`() {
        val result = completeInController("\$this->fetchTable('Reviews')->find()->contain('Comments.<caret>');")
        assertTrue(result.contains("Articles"))
        assertTrue(result.contains("Users"))
    }

    fun `test dotted path through an unknown association offers nothing`() {
        val result = completeInController("\$this->fetchTable('Reviews')->find()->contain('Missing.<caret>');")
        assertEmpty(result)
    }

    fun `test array key offers the aliases`() {
        assertOffersOnlyReviewsAssociations(
            completeInController("\$this->fetchTable('Reviews')->find()->contain(['<caret>' => ['fields' => ['id']]]);")
        )
    }

    fun `test array under a key offers the associations of the key`() {
        val result = completeInController("\$this->fetchTable('Reviews')->find()->contain(['Authors' => ['<caret>']]);")
        assertSameElements(result, "Profiles", "Reviews")
    }

    fun `test key of an array under a key offers the associations of the key`() {
        val result = completeInController("\$this->fetchTable('Reviews')->find()->contain(['Authors' => ['<caret>' => []]]);")
        assertSameElements(result, "Profiles", "Reviews")
    }

    fun `test deeper nesting and dotted keys walk every segment`() {
        val result = completeInController(
            "\$this->fetchTable('Reviews')->find()->contain(['Authors.Reviews' => ['Authors' => ['<caret>']]]);"
        )
        assertSameElements(result, "Profiles", "Reviews")
    }

    fun `test option values under a key offer nothing`() {
        val result = completeInController(
            "\$this->fetchTable('Reviews')->find()->contain(['Authors' => ['fields' => ['<caret>']]]);"
        )
        assertEmpty(result)
    }

    fun `test second argument offers nothing`() {
        val result = completeInController("\$this->fetchTable('Reviews')->find()->contain('Authors', '<caret>');")
        assertFalse(result.contains("Movies"))
    }

    fun `test query typed only as SelectQuery keeps the all tables list`() {
        myFixture.configureByFilePathAndText("cake5/src5/Model/Table/ArticlesTable.php", """
        <?php
        namespace App\Model\Table;

        use Cake\ORM\Query\SelectQuery;
        use Cake\ORM\Table;

        class ArticlesTable extends Table
        {
            public function findTopRated(SelectQuery ${'$'}query): SelectQuery
            {
                return ${'$'}query->matching('<caret>');
            }
        }
        """.trimIndent())
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings.orEmpty()
        assertTrue(result.contains("Users"))
        assertTrue(result.contains("Movies"))
    }
}
