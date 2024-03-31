package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Settings
import org.junit.Test


public class AssociatedTableTest : BaseTestCase() {

    private fun prepareTest() {
        // change app directory:
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = originalSettings.state.copy()
        newState.appDirectory = "src3"
        originalSettings.loadState(newState)

        myFixture.configureByFiles(
                "cake5/src3/Controller/AppController.php",
                "cake5/src3/Model/Table/MoviesTable.php",
                "cake5/src3/Model/Table/ArticlesTable.php",
                "cake5/vendor/cakephp.php"
        )
    }

    @Test
    fun `test associated table methods are completed`() {
        prepareTest()

        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function ownedBy() {
                ${'$'}moviesTable = ${'$'}this->fetchTable('Movies');
                ${'$'}moviesTable->Articles-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("myCustomArticleMethod"))
    }
}