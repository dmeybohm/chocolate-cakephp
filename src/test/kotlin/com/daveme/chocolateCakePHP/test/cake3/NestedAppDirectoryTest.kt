package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class NestedAppDirectoryTest : BaseTestCase() {

    override fun setUp() {
        super.setUp()

        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = Settings.getDefaults(myFixture.project).state.copy()
        newState.cake3Enabled = true
        newState.cake3ForceEnabled = true  // Force enable to bypass auto-detection
        newState.cake2Enabled = false
        newState.appDirectory = "app/src"  // Nested path
        originalSettings.loadState(newState)

        setUpTestFiles()
    }

    private fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3_nested/app/src/Controller/AppController.php",
            "cake3_nested/app/src/Model/Table/MoviesTable.php",
            "cake3_nested/app/src/Template/Movie/index.ctp",
            "cake3_nested/vendor/cakephp.php"
        )
    }

    fun `test completing a cake3 table with nested app directory`() {
        myFixture.configureByFilePathAndText("cake3_nested/app/src/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        class MovieController extends AppController {
            public function index() {
                ${'$'}this-><caret>
            }
        }
        """.trimIndent())

        assertTrue(Settings.getInstance(myFixture.project).cake3Enabled)
        myFixture.completeBasic()

        val strings = myFixture.lookupElementStrings
        assertNotNull("Completions should not be null", strings)
        assertTrue("Should contain Movies table", strings!!.contains("Movies"))
    }

    fun `test view variable completion with nested app directory`() {
        myFixture.configureByFilePathAndText("cake3_nested/app/src/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        class MovieController extends AppController {
            public function index() {
                ${'$'}this->set('movie', 'test');
                ${'$'}this->set('otherVar', 'value');
            }
        }
        """.trimIndent())

        myFixture.configureByFilePathAndText("cake3_nested/app/src/Template/Movie/index.ctp", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())

        myFixture.completeBasic()

        val strings = myFixture.lookupElementStrings
        assertNotNull("Completions should not be null", strings)
        assertTrue("Should contain movie variable", strings!!.contains("\$movie"))
    }
}
