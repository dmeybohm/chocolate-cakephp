package com.daveme.chocolateCakePHP.test

import org.junit.Test

class CustomFinderTest : BaseTestCase() {

    private fun prepareTest() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Model/Table/MoviesTable.php",
            "cake3/vendor/cakephp.php"
        )
    }

    @Test
    fun `test custom finder from Table class is generated when doing a find`() {
        prepareTest()

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
        prepareTest()

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
        prepareTest()

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
        val allInstances = result!!.count({ it.equals("all", ignoreCase=true) });
        assertEquals(1, allInstances)
    }

    @Test
    fun `test nested custom finder is generated when doing a find`() {
        prepareTest()

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
        prepareTest()

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
        prepareTest()

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
        prepareTest()

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

}