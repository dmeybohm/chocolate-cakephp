package com.daveme.chocolateCakePHP.test.cake3

class TableLocatorTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/src/Model/Entity/Article.php",
            "cake3/src/Model/Entity/Movie.php",
            "cake3/src/Model/Entity/TablelessEntity.php",
            "cake3/vendor/cakephp.php"
        )
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
        use Cake\ORM\TableRegistry;
        
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

    fun `test get returns entity through dynamic prop with tableless entity`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        
        class MovieController extends Controller
        {
            public function view(${'$'}id) {
                ${'$'}entity = ${'$'}this->TablelessEntities->get(${'$'}id);
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
}