package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Settings
import org.junit.Test

public class ViewTest() : BaseTestCase() {

    private fun prepareTest() {
        // change app directory:
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = originalSettings.state.copy()
        newState.appDirectory = "src"
        originalSettings.loadState(newState)
    }

    public override fun tearDown() {
        // Reset plugin settings:
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = Settings().state
        originalSettings.loadState(newState)

        super.tearDown()
    }

    @Test
    public fun `test completing view helper inside a view`() {
        prepareTest()

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

    @Test
    public fun `test completing view helper methods inside a view`() {
        prepareTest()

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

    @Test
    public fun `test completing child view helper methods inside a view helper`() {
        prepareTest()

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

    @Test
    public fun `test completing from plugin in view helper`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/plugins/TestPlugin/src/View/Helper/TestPluginHelper.php",
            "cake3/src/View/AppView.php",
            "cake3/vendor/cakephp.php"
        )

        // Add plugin namespace to state.
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = originalSettings.state.copy()
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

    @Test
    public fun `test completing view helper inside a view for cake4`() {
        // change app directory:
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = originalSettings.state.copy()
        newState.appDirectory = "src2"
        originalSettings.loadState(newState)

        myFixture.configureByFiles(
            "cake4/src2/Controller/AppController.php",
            "cake4/src2/Controller/Component/MovieMetadataComponent.php",
            "cake4/src2/View/Helper/MovieFormatterHelper.php",
            "cake4/src2/View/AppView.php",
            "cake4/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("MovieFormatter"))
    }

    @Test
    public fun `test completing view helper inside a view helper for cake4`() {
        // change app directory:
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = originalSettings.state.copy()
        newState.appDirectory = "src2"
        originalSettings.loadState(newState)

        myFixture.configureByFiles(
            "cake4/src2/Controller/AppController.php",
            "cake4/src2/Controller/Component/MovieMetadataComponent.php",
            "cake4/src2/View/Helper/MovieFormatterHelper.php",
            "cake4/src2/View/Helper/ArtistFormatterHelper.php",
            "cake4/src2/View/AppView.php",
            "cake4/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake4/src2/View/Helper/MovieFormatterHelper.php", """
        <?php
        namespace App\View\Helper;

        class MovieFormatterHelper extends \Cake\View\Helper
        {
            public function format(array ${'$'}movies): string {
                return ${'$'}this-><caret>;
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("ArtistFormatter"))
        assertFalse(result.contains("MovieFormatter"))
    }

    public fun `test completing view helper inside a view helper for cake3`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/src/View/Helper/MovieFormatterHelper.php",
            "cake3/src/View/Helper/ArtistFormatterHelper.php",
            "cake3/src/View/AppView.php",
            "cake3/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake3/src/View/Helper/MovieFormatterHelper.php", """
        <?php
        namespace App\View\Helper;

        class MovieFormatterHelper extends \Cake\View\Helper
        {
            public function format(array ${'$'}movies): string {
                return ${'$'}this-><caret>;
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("ArtistFormatter"))
        assertFalse(result.contains("MovieFormatter"))
    }

    @Test
    public fun `test does not complete view helper does not apply in irrelevant contexts for cake3`() {
        prepareTest()

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/src/View/Helper/MovieFormatterHelper.php",
            "cake3/src/View/Helper/ArtistFormatterHelper.php",
            "cake3/src/View/AppView.php",
            "cake3/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php

        namespace App\Controller;
        
        class MovieController extends AppController
        {
            public function index() {
                return ${'$'}this-><caret>;
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertFalse(result!!.contains("ArtistFormatter"))
        assertFalse(result.contains("MovieFormatter"))
    }


    @Test
    public fun `test completing view helper inside a view helper for cake2`() {
        myFixture.configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/Controller/Component/MovieMetadataComponent.php",
            "cake2/app/View/Helper/MovieFormatterHelper.php",
            "cake2/app/View/Helper/ArtistFormatterHelper.php",
            "cake2/app/View/AppView.php",
            "cake2/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake2/app/View/Helper/MovieFormatterHelper.php", """
        <?php

        class MovieFormatterHelper extends AppHelper
        {
            public function format(array ${'$'}movies): string {
                return ${'$'}this-><caret>;
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("ArtistFormatter"))
        assertFalse(result.contains("MovieFormatter"))
    }

    @Test
    public fun `test does not complete view helper does not apply in irrelevant contexts for cake2`() {
        myFixture.configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/Controller/Component/MovieMetadataComponent.php",
            "cake2/app/View/Helper/MovieFormatterHelper.php",
            "cake2/app/View/Helper/ArtistFormatterHelper.php",
            "cake2/app/View/AppView.php",
            "cake2/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake2/app/Controller/MovieController.php", """
        <?php

        class MovieController extends AppController
        {
            public function index() {
                return ${'$'}this-><caret>;
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertFalse(result!!.contains("ArtistFormatter"))
        assertFalse(result.contains("MovieFormatter"))
    }

}