package com.daveme.chocolateCakePHP.test.cake4

import org.junit.Test

class TableLocatorTest : Cake4BaseTestCase() {

    override fun prepareTest() {
        myFixture.configureByFiles(
            "cake4/src2/Controller/AppController.php",
            "cake4/src2/Model/Table/ArticlesTable.php",
            "cake4/vendor/cakephp.php"
        )
    }

    @Test
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

    @Test
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

    @Test
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