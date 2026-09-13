package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.openapi.project.guessProjectDir

/**
 * Proves that auto-detection from composer.json, rather than the forced
 * setting every other cake5 test uses, is what turns a real feature on.
 */
class AutoDetectionGatingTest : BaseTestCase() {

    override fun setUp() {
        super.setUp()

        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = Settings.getDefaults(myFixture.project).state.copy()
        newState.cake3Enabled = true
        newState.cake3ForceEnabled = false  // Rely on detection
        newState.cake2Enabled = false
        // appDirectory left at its default; the nested path must come from composer.json.
        originalSettings.loadState(newState)

        myFixture.configureByFiles(
            "cake5_nested/app/src5/Controller/AppController.php",
            "cake5_nested/app/src5/Model/Table/MoviesTable.php",
            "cake5_nested/templates/Movie/index.php",
            "cake5_nested/vendor/cakephp.php"
        )
    }

    private fun configureController() {
        myFixture.configureByFilePathAndText("cake5_nested/app/src5/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        class MovieController extends AppController {
            public function index() {
                ${'$'}this-><caret>
            }
        }
        """.trimIndent())
    }

    fun `test table completion is enabled by composer json`() {
        val composerJson = myFixture.addFileToProject("composer.json", """
            {
                "require": {
                    "cakephp/cakephp": "^5.0"
                },
                "autoload": {
                    "psr-4": {
                        "App\\": "app/src5/"
                    }
                }
            }
        """.trimIndent())

        // Guard: the detector reads composer.json from guessProjectDir(), which
        // must be the directory the light fixture writes into.
        assertEquals(
            myFixture.project.guessProjectDir()?.path,
            composerJson.virtualFile.parent.path
        )

        configureController()

        val settings = Settings.getInstance(myFixture.project)
        assertTrue("CakePHP should be detected from composer.json", settings.cake3Enabled)
        assertEquals("app/src5", settings.appDirectory)

        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertNotNull("Completions should not be null", strings)
        assertTrue("Should contain Movies table", strings!!.contains("Movies"))
    }

    fun `test table completion is disabled without composer json`() {
        configureController()

        val settings = Settings.getInstance(myFixture.project)
        assertFalse("CakePHP should not be detected without composer.json", settings.cake3Enabled)

        myFixture.completeBasic()
        // A null list means a single completion was auto-inserted without a
        // popup, which would hide a wrongly offered Movies table, so require
        // the popup. PHP's own member completions keep it non-empty.
        val strings = myFixture.lookupElementStrings
        assertNotNull("Completions should not be null", strings)
        assertFalse("Should not contain Movies table", strings!!.contains("Movies"))
    }

    fun `test fixture composer json without psr-4 falls back to default app directory`() {
        myFixture.copyFileToProject("composer.json", "composer.json")

        val settings = Settings.getInstance(myFixture.project)
        assertTrue("CakePHP should be detected from the fixture composer.json", settings.cake3Enabled)
        assertEquals("src", settings.appDirectory)
        assertEquals("\\App", settings.appNamespace)
    }
}
