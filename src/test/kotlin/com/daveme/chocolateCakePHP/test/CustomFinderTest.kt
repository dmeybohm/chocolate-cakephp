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
    fun `test custom finder from parent is generated when doing a find`() {

    }

    @Test
    fun `test custom finder filters out findOrCreate`() {

    }

}