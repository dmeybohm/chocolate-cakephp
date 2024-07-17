package com.daveme.chocolateCakePHP.test.cake5

class TableLocatorTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Model/Table/ArticlesTable.php",
            "cake5/src5/Model/Table/NoEntitiesTable.php",
            "cake5/src5/Model/Entity/Article.php",
            "cake5/src5/Model/Entity/Movie.php",
            "cake5/src5/Model/Entity/TablelessEntity.php",
            "cake5/vendor/cakephp.php"
        )
    }

    fun `test fetchTable returns methods from the users custom namespace in a controller`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}result = ${'$'}this->fetchTable('Articles')-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("myCustomArticleMethod"))
    }

    fun `test TableLocator get returns methods from the users custom namespace`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\Locator\LocatorAwareTrait;
        
        class LocatorTester {
            use LocatorAwareTrait;
        }
        
        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}locator = new LocatorTester();
                ${'$'}locator->getTableLocator()->get('Articles')-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("myCustomArticleMethod"))
    }

    fun `test TableLocator get returns methods from the users custom namespace when stored in a variable`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\Locator\LocatorAwareTrait;
        
        class LocatorTester {
            use LocatorAwareTrait;
        }
        
        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}tester = new LocatorTester();
                ${'$'}locator = ${'$'}tester->getTableLocator();
                ${'$'}locator->get('Articles')-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("myCustomArticleMethod"))
    }

    fun `test TableLocator get returns Table methods when stored in a variable with a nonexistent model`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\Locator\LocatorAwareTrait;
        
        class LocatorTester {
            use LocatorAwareTrait;
        }
        
        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}tester = new LocatorTester();
                ${'$'}locator = ${'$'}tester->getTableLocator();
                ${'$'}locator->get('Nonexistent')-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("find"))
    }

    fun `test static TableLocator get returns methods from the users custom namespace`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;
        
        class MovieController extends Controller
        {
            public function artist() {
                TableRegistry::getTableLocator()->get('Articles')-><caret>     
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("myCustomArticleMethod"))
    }

    fun `test fetchTable argument can be autocompleted with quotes`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;
        
        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}result = ${'$'}this->fetchTable('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("Articles"))
    }

    fun `test fetchTable argument can be autocompleted with quotes when stored in a var`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;
        
        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}articles = ${'$'}this->fetchTable('Articles');
                ${'$'}articles-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("myCustomArticleMethod"))
    }

    fun `test TableRegistry static method argument can be autocompleted with quotes`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;
        
        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}result = TableRegistry::getTableLocator()->get('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("Articles"))
    }

    fun `test TableRegistry from getTableLocator method can be autocompleted with quotes and saved in a variable`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;
        
        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}locator = ${'$'}this->getTableLocator();
                ${'$'}result = ${'$'}locator->get('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("Articles"))
    }

    fun `test types from TableRegistry from getTableLocator method can be determined when saved in a variable`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;
        
        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}locator = ${'$'}this->getTableLocator();
                ${'$'}result = ${'$'}locator->get('Articles')-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("myCustomArticleMethod"))
    }

    fun `test TableRegistry from getTableLocator method can be autocompleted with quotes inline`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}locator = ${'$'}this->getTableLocator()->get('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("Articles"))
    }

    fun `test types from getTableLocator method can be autocompleted when inline`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;
        
        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}locator = ${'$'}this->getTableLocator()->get('Articles')-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("myCustomArticleMethod"))
    }

    fun `test entities are returned from finders`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;
        
        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}articles = ${'$'}this->getTableLocator()->get('Articles')->find()->all();
                foreach (${'$'}articles as ${'$'}article) {
                    echo ${'$'}article-><caret>
                }
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
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

    fun `test get returns entity from associated table through fetchTable`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}article = ${'$'}this->fetchTable('Movies')->Articles->get(${'$'}id);
                ${'$'}article-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
    }

    fun `test get returns entity from associated table through fetchTable with intermediate var`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}movies = ${'$'}this->fetchTable('Movies');
                ${'$'}article = ${'$'}movies->Articles->get(${'$'}id);
                ${'$'}article-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
    }

    fun `test get returns entity from associated table through fetchTable with intermediate var and nonexistent model`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}movies = ${'$'}this->fetchTable('NonExistent');
                ${'$'}article = ${'$'}movies->Articles->get(${'$'}id);
                ${'$'}article-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
    }

    fun `test get returns entity from associated table through TableRegistry`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}article = TableRegistry::getTableLocator()
                    ->get('Movies')
                    ->Articles
                    ->get(${'$'}id);
                ${'$'}article-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
    }

    fun `test get returns entity from associated table through TableRegistry and intermediate var1`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}movies = TableRegistry::getTableLocator()->get('Movies');
                ${'$'}article = ${'$'}movies->Articles->get(${'$'}id);
                ${'$'}article-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
    }

    fun `test get returns entity from associated table through TableRegistry and intermediate var2`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}locator = TableRegistry::getTableLocator();
                ${'$'}movies = ${'$'}locator->get('Movies')
                ${'$'}article = ${'$'}movies->Articles->get(${'$'}id);
                ${'$'}article-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
    }

    fun `test get returns entity from associated table through getTableLocator`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}article = ${'$'}this->getTableLocator()->get('Movies')->Articles->get(${'$'}id);
                ${'$'}article-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
    }

    fun `test get returns entity from associated table through getTableLocator with intermediate var`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}movies = ${'$'}this->getTableLocator()->get('Movies');
                ${'$'}article = ${'$'}movies->Articles->get(${'$'}id);
                ${'$'}article-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someArticleMethod"))
    }

    fun `test get returns entity from associated table through getTableLocator with tableless entity and intermediate var`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}movies = ${'$'}this->getTableLocator()->get('Movies');
                ${'$'}entity = ${'$'}movies->TablelessEntities->get(${'$'}id);
                ${'$'}entity-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someTablelessEntityMethod"))
        assertTrue(result.contains("getAccessible")) // \Cake\Datasource\EntityTrait method
    }

    fun `test get returns entity through getTableLocator with tableless entity and intermediate var`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}entities = ${'$'}this->getTableLocator()->get('TablelessEntities');
                ${'$'}entity = ${'$'}entities->get(${'$'}id);
                ${'$'}entity-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someTablelessEntityMethod"))
        assertTrue(result.contains("getAccessible")) // \Cake\Datasource\EntityTrait method
    }

    fun `test get returns entity through fetchTable with tableless entity and intermediate var`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}entities = ${'$'}this->fetchTable('TablelessEntities');
                ${'$'}entity = ${'$'}entities->get(${'$'}id);
                ${'$'}entity-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someTablelessEntityMethod"))
        assertTrue(result.contains("getAccessible")) // \Cake\Datasource\EntityTrait method
    }

    fun `test get returns entity through dynamic prop with tableless entity and intermediate var`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}entities = ${'$'}this->TablelessEntities;
                ${'$'}entity = ${'$'}entities->get(${'$'}id);
                ${'$'}entity-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someTablelessEntityMethod"))
        assertTrue(result.contains("getAccessible")) // \Cake\Datasource\EntityTrait method
    }

    fun `test get returns entity through dynamic prop with imaginary entity`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}entity = ${'$'}this->ImaginaryEntities->get(${'$'}id);
                ${'$'}entity-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("getAccessible")) // \Cake\Datasource\EntityTrait method
    }

    fun `test get returns entity through associated table via dynamic prop with tableless entity`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}entity = ${'$'}this->AnotherTablelessEntities->TablelessEntities->get(${'$'}id);
                ${'$'}entity-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("getAccessible")) // \Cake\Datasource\EntityTrait method
    }

    fun `test get returns entity through associated table via dynamic prop with imaginary entity`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}entity = ${'$'}this->FirstImaginaryEntities->SecondImaginaryEntities->get(${'$'}id);
                ${'$'}entity-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someTablelessEntityMethod"))
        assertTrue(result.contains("getAccessible")) // \Cake\Datasource\EntityTrait method
    }

    fun `test get returns entity through associated table via dynamic prop through intermediate var with tableless entity`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}entities = ${'$'}this->AnotherTablelessEntities;
                ${'$'}entity = ${'$'}entities->TablelessEntities->get(${'$'}id);
                ${'$'}entity-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("someTablelessEntityMethod"))
        assertTrue(result.contains("getAccessible")) // \Cake\Datasource\EntityTrait method
    }

    fun `test get returns entity through associated table via dynamic prop through intermediate var with imaginary entities`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}entities = ${'$'}this->FirstImaginaryEntities;
                ${'$'}entity = ${'$'}entities->SecondImaginaryEntities->get(${'$'}id);
                ${'$'}entity-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("getAccessible")) // \Cake\Datasource\EntityTrait method

    }

    fun `test get returns entity through noentities associated table via dynamic prop through intermediate var with imaginary entities`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}imaginaryEntities = ${'$'}this->ImaginaryEntities;
                ${'$'}entity = ${'$'}imaginaryEntities->NoEntities->get(${'$'}id);
                ${'$'}entity-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("getAccessible")) // \Cake\Datasource\EntityTrait method
    }
}