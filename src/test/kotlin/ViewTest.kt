package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Settings

class ViewTest : BaseTestCase() {

    fun `test completing view helper inside a view`() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/src/View/Helper/MovieFormatterHelper.php",
            "cake3/src/View/AppView.php",
            "cake3/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php            
        ${'$'}this-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("MovieFormatter"))
    }

    fun `test completing view helper methods inside a view`() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/src/View/Helper/MovieFormatterHelper.php",
            "cake3/src/View/AppView.php",
            "cake3/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php            
        ${'$'}this->MovieFormatter-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("format"))
    }

    fun `test completing child view helper methods inside a view helper`() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/View/AppView.php",
            "cake3/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake3/src/View/Helper/MovieFormatterHelper.php", """
        <?php
        namespace App\View\Helper;

        class MovieFormatterHelper extends \Cake\View\Helper
        {
            public function format(array ${'$'}movies): string {
                return ${'$'}this->MovieFormatter-><caret>;
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("format"))
    }

    fun `test completing from plugin in view helper`() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/plugins/TestPlugin/src/View/Helper/TestPluginHelper.php",
            "cake3/src/View/AppView.php",
            "cake3/vendor/cakephp.php"
        )

        // Add plugin namespace to state.
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = originalSettings.state!!.copy()
        newState.pluginNamespaces = listOf("\\TestPlugin")
        originalSettings.loadState(newState)

        myFixture.configureByFilePathAndText("cake3/src/View/Helper/MovieFormatterHelper.php", """
        <?php
        namespace App\View\Helper;

        class MovieFormatterHelper extends \Cake\View\Helper
        {
            public function format(array ${'$'}movies): string {
                return ${'$'}this->TestPlugin-><caret>;
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("helpWithSomething"))
    }

}