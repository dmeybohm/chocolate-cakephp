package com.daveme.chocolateCakePHP.test.cake5

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
        newState.appDirectory = "app/src5"  // Nested path
        originalSettings.loadState(newState)

        setUpTestFiles()
    }

    private fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5_nested/app/src5/Controller/AppController.php",
            "cake5_nested/app/src5/Model/Table/MoviesTable.php",
            "cake5_nested/templates/Movie/index.php",
            "cake5_nested/vendor/cakephp.php"
        )
    }

    fun `test completing a cake5 table with nested app directory`() {
        myFixture.configureByFilePathAndText("cake5_nested/app/src5/Controller/MovieController.php", """
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
        myFixture.configureByFilePathAndText("cake5_nested/app/src5/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        class MovieController extends AppController {
            public function index() {
                ${'$'}this->set('movie', 'test');
                ${'$'}this->set('otherVar', 'value');
            }
        }
        """.trimIndent())

        myFixture.configureByFilePathAndText("cake5_nested/templates/Movie/index.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())

        myFixture.completeBasic()

        val strings = myFixture.lookupElementStrings
        assertNotNull("Completions should not be null", strings)
        assertTrue("Should contain movie variable", strings!!.contains("\$movie"))
    }
}
