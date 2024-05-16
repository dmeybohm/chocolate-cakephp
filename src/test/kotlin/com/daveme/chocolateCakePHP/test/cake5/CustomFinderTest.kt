package com.daveme.chocolateCakePHP.test.cake5

import org.junit.Test

class CustomFinderTest : Cake5BaseTestCase() {

    override fun prepareTest() {
        myFixture.configureByFiles(
            "cake5/src3/Controller/AppController.php",
            "cake5/src3/Model/Table/MoviesTable.php",
            "cake5/vendor/cakephp.php"
        )
    }

    @Test
    fun `test custom finder from Table class is generated when doing a find`() {
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
        assertTrue(result!!.contains("ownedBy"))
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
    fun `test custom finder from custom Table class name is generated when doing a find`() {
        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;
        use App\Model\Table\MoviesTable;
        
        class MovieController extends Controller
        {
            public function ownedBy() {
                ${'$'}myMovies = ${'$'}this->fetchTable('MyMovies', [
                    'className' => MoviesTable::class,
                ]);
                ${'$'}myMovies->find('<caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("ownedBy"))
    }
}