package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class NestedAppDirectoryTest : BaseTestCase() {

    override fun setUp() {
        super.setUp()

        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = Settings.getDefaults(myFixture.project).state.copy()
        newState.cake2Enabled = true
        newState.cake3Enabled = false
        newState.cake2AppDirectory = "src/app"  // Nested path
        originalSettings.loadState(newState)

        setUpTestFiles()
    }

    private fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake2_nested/src/app/Controller/AppController.php",
            "cake2_nested/src/app/Model/Movie.php",
            "cake2_nested/src/app/View/Movie/index.ctp",
            "cake2_nested/vendor/cakephp.php"
        )
    }

    fun `test completing a cake2 model with nested app directory`() {
        myFixture.configureByFilePathAndText("cake2_nested/src/app/Controller/MovieController.php", """
        <?php
        App::uses('Controller', 'Controller');

        class MovieController extends AppController {
            public ${'$'}uses = ['Movie'];
            public function index() {
                ${'$'}this-><caret>
            }
        }
        """.trimIndent())

        assertTrue(Settings.getInstance(myFixture.project).cake2Enabled)
        myFixture.completeBasic()

        val strings = myFixture.lookupElementStrings
        assertNotNull("Completions should not be null", strings)
        assertTrue("Should contain Movie model", strings!!.contains("Movie"))
    }

    fun `test view variable completion with nested app directory`() {
        myFixture.configureByFilePathAndText("cake2_nested/src/app/Controller/MovieController.php", """
        <?php
        App::uses('Controller', 'Controller');

        class MovieController extends AppController {
            public function index() {
                ${'$'}this->set('movie', 'test');
                ${'$'}this->set('otherVar', 'value');
            }
        }
        """.trimIndent())

        myFixture.configureByFilePathAndText("cake2_nested/src/app/View/Movie/index.ctp", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())

        myFixture.completeBasic()

        val strings = myFixture.lookupElementStrings
        assertNotNull("Completions should not be null", strings)
        assertTrue("Should contain movie variable", strings!!.contains("\$movie"))
    }
}
