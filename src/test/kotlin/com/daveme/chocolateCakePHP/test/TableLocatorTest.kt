package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Settings
import org.junit.Test

class TableLocatorTest : BaseTestCase() {

    private fun prepareTest() {
        // change app directory:
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = originalSettings.state.copy()
        newState.appDirectory = "src"
        originalSettings.loadState(newState)
    }

    @Test
    fun `test fetchTable returns methods from the users custom namespace in a controller`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/vendor/cakephp.php"
        )

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

    @Test
    fun `test TableLocator get returns methods from the users custom namespace`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/vendor/cakephp.php"
        )

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

    @Test
    fun `test TableLocator get returns methods from the users custom namespace when stored in a variable`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/vendor/cakephp.php"
        )

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
                ${'$'}locator->getTableLocator()->get('Articles')-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("myCustomArticleMethod"))
    }

    @Test
    fun `test static TableLocator get returns methods from the users custom namespace`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/vendor/cakephp.php"
        )

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

    @Test
    fun `test fetchTable argument can be autocompleted with quotes`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/vendor/cakephp.php"
        )

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

    @Test
    fun `test TableRegistry static method argument can be autocompleted with quotes`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/vendor/cakephp.php"
        )

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

    @Test
    fun `test TableRegistry from getTableLocator method can be autocompleted with quotes and saved in a variable`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/vendor/cakephp.php"
        )

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

    @Test
    fun `test types from TableRegistry from getTableLocator method can be determined when saved in a variable`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/vendor/cakephp.php"
        )

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

    @Test
    fun `test TableRegistry from getTableLocator method can be autocompleted with quotes inline`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/vendor/cakephp.php"
        )

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


    @Test
    fun `test types from getTableLocator method can be autocompleted when inline`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/vendor/cakephp.php"
        )

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
}