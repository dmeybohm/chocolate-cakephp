package com.daveme.chocolateCakePHP.test.cake4

class CustomFinderTest : Cake4BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake4/src4/Controller/AppController.php",
            "cake4/src4/Model/Table/MoviesTable.php",
            "cake4/vendor/cakephp.php"
        )
    }

    fun `test custom finder class is generated when doing a find from a dynamic property`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use Cake\ORM\TableRegistry;
        
        class MovieController extends Controller
        {
            public function ownedBy() {
                ${'$'}this->Movies->find('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("ownedBy"))
    }

    fun `test custom finder does not generate an empty completion`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function ownedBy() {
                ${'$'}moviesTable = ${'$'}this->fetchTable('Movies');
                ${'$'}moviesTable->find('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertFalse(result!!.contains(""))
    }

    fun `test custom finder does not generate two 'all' completions`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function ownedBy() {
                ${'$'}moviesTable = ${'$'}this->fetchTable('Movies');
                ${'$'}moviesTable->find('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        val allInstances = result!!.count({ it.equals("all", ignoreCase=true) })
        assertEquals(1, allInstances)
    }

    fun `test nested custom finder is generated when doing a find`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function ownedBy() {
                ${'$'}moviesTable = ${'$'}this->fetchTable('Movies');
                ${'$'}moviesTable->find('ownedBy')->find('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("ownedBy"))
    }

    fun `test nested custom finder is generated when doing a find three levels deep`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function ownedBy() {
                ${'$'}moviesTable = ${'$'}this->fetchTable('Movies');
                ${'$'}moviesTable->find('ownedBy')->find('list')->find('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("ownedBy"))
    }

    fun `test nested custom finder is generated when doing a find three levels deep with intermediate vars`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function ownedBy() {
                ${'$'}moviesTable = ${'$'}this->fetchTable('Movies');
                ${'$'}foo = ${'$'}moviesTable->find('ownedBy')->find('list');
                ${'$'}foo->find('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("ownedBy"))
    }

    fun `test nested custom finder is generated when doing a find three levels deep with other calls inbetween`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function ownedBy() {
                ${'$'}moviesTable = ${'$'}this->fetchTable('Movies');
                ${'$'}foo = ${'$'}moviesTable->find('ownedBy')->find('list')->where('foo', 'bar');
                ${'$'}foo->find('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("ownedBy"))
    }

    fun `test nested custom finder does not continue autocompleting for non-query methods`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function ownedBy() {
                ${'$'}moviesTable = ${'$'}this->fetchTable('Movies');
                ${'$'}foo = ${'$'}moviesTable->find('ownedBy')->toArray();
                ${'$'}foo->find('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertFalse(result!!.contains("ownedBy"))
    }
}