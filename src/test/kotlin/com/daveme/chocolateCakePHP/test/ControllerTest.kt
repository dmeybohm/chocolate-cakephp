package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Settings
import org.junit.Test

public class ControllerTest : BaseTestCase() {

    @Test
    public fun `test completing a component inside a controller`() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/vendor/cakephp.php"
        )

        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}metadata = ${'$'}this-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("MovieMetadata"))
    }

    @Test
    public fun `test completing component methods inside a controller`() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/vendor/cakephp.php"
        )

        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}metadata = ${'$'}this->MovieMetadata-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("generateMetadata"))
    }

    @Test
    public fun `test completing a component from a plugin`() {
        // Add plugin namespace to state.
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = originalSettings.state.copy()
        newState.pluginNamespaces = listOf("\\TestPlugin")
        originalSettings.loadState(newState)

        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/plugins/Controller/Component/InsidePluginComponent.php",
            "cake3/vendor/cakephp.php"
        )

        myFixture.configureByText("MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function artist() {
                ${'$'}metadata = ${'$'}this->InsidePlugin-><caret>
            }
        }
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("insidePluginComponentMethod"))
    }

    @Test
    public fun `test completing a cake2 model inside a controller`() {
        myFixture.configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/Controller/Component/MovieMetadataComponent.php",
            "cake2/app/Model/AppModel.php",
            "cake2/app/Model/Movie.php",
            "cake2/vendor/cakephp.php"
        )
        myFixture.configureByFilePathAndText("cake2/app/Controller/MovieController.php", """
        <?php
        App::uses('Controller', 'Controller');
        
        class MovieController extends AppController {
        	public ${'$'}uses = ['Movie'];
            public function artist(${'$'}artistId) {
                ${'$'}this-><caret>
            }
        }
        """.trimIndent())

        assertTrue(Settings.getInstance(myFixture.project).cake2Enabled)
        myFixture.completeBasic()

        val strings = myFixture.lookupElementStrings
        assertTrue(strings!!.contains("Movie"))
    }

    @Test
    public fun `test completing cake2 model methods inside a controller`() {
        myFixture.configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/Controller/Component/MovieMetadataComponent.php",
            "cake2/app/Model/AppModel.php",
            "cake2/app/Model/Movie.php",
            "cake2/vendor/cakephp.php"
        )
        myFixture.configureByFilePathAndText("cake2/app/Controller/MovieController.php", """
        <?php
        App::uses('Controller', 'Controller');
        
        class MovieController extends AppController {
        	public ${'$'}uses = ['Movie'];
            public function artist(${'$'}artistId) {
                ${'$'}this->Movie-><caret>
            }
        }
        """.trimIndent())
        assertTrue(Settings.getInstance(myFixture.project).cake2Enabled)
        myFixture.completeBasic()

        val strings = myFixture.lookupElementStrings
        assertTrue(strings!!.contains("findById"))
    }

    @Test
    public fun `test methods on a component are not magically auto-completed for ViewBuilder`() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/src/View/AppView.php",
            "cake3/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        class MovieController extends \Cake\Controller\Controller
        {
            public function testViewBuilder() 
            {
                ${'$'}this->viewBuilder()-><caret>
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertFalse(result!!.contains("MovieMetadata"))
    }

    @Test
    public fun `test nested model completion type provider in cake2`() {
        myFixture.configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/Controller/Component/AppComponent.php",
            "cake2/app/Controller/Component/MovieMetadataComponent.php",
            "cake2/app/Model/AppModel.php",
            "cake2/app/Model/Artist.php",
            "cake2/app/Model/Director.php",
            "cake2/app/Model/Movie.php",
            "cake2/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake2/app/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        class MovieController extends \Cake\Controller\Controller
        {
            public function testNestedCake2Model() 
            {
                ${'$'}this->Movie->Director-><caret>
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("releaseFilm"))
    }

    @Test
    public fun `test nested model completion contributor in cake2`() {
        myFixture.configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/Controller/Component/AppComponent.php",
            "cake2/app/Controller/Component/MovieMetadataComponent.php",
            "cake2/app/Model/AppModel.php",
            "cake2/app/Model/Artist.php",
            "cake2/app/Model/Director.php",
            "cake2/app/Model/Movie.php",
            "cake2/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake2/app/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        class MovieController extends \Cake\Controller\Controller
        {
            public function testNestedCake2Model() 
            {
                ${'$'}this->Movie-><caret>
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("Director"))
    }

    @Test
    public fun `test nested model completion contributor in cake2 only if parent is a model`() {
        myFixture.configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/Controller/Component/AppComponent.php",
            "cake2/app/Controller/Component/MovieMetadataComponent.php",
            "cake2/app/Model/AppModel.php",
            "cake2/app/Model/Artist.php",
            "cake2/app/Model/Director.php",
            "cake2/app/Model/Movie.php",
            "cake2/vendor/cakephp.php"
        )

        myFixture.configureByFilePathAndText("cake2/app/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        class MovieController extends \Cake\Controller\Controller
        {
            public function testNestedCake2Model() 
            {
                ${'$'}this->MovieMetadata-><caret>
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertFalse(result!!.contains("Director"))
    }
    
}