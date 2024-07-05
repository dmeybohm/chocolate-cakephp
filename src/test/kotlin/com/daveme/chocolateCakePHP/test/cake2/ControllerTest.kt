package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class ControllerTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/Controller/Component/AppComponent.php",
            "cake2/app/Controller/Component/MovieMetadataComponent.php",
            "cake2/app/Model/AppModel.php",
            "cake2/app/Model/Movie.php",
            "cake2/app/Model/Artist.php",
            "cake2/app/Model/Director.php",
            "cake2/app/Model/Movie.php",
            "cake2/vendor/cakephp.php"
        )
    }

    fun `test completing a cake2 model inside a controller`() {
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

    fun `test completing cake2 model methods inside a controller`() {
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

    fun `test nested model completion type provider in cake2`() {
        myFixture.configureByFilePathAndText("cake2/app/Controller/MovieController.php", """
        <?php
        App::uses('Controller', 'Controller');
        
        class MovieController extends Controller
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

    fun `test nested model completion contributor in cake2`() {
        myFixture.configureByFilePathAndText("cake2/app/Controller/MovieController.php", """
        <?php
        App::uses('Controller', 'Controller');

        class MovieController extends Controller
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

    fun `test nested model completion contributor in cake2 only if parent is a model`() {
        myFixture.configureByFilePathAndText("cake2/app/Controller/MovieController.php", """
        <?php
        App::uses('Controller', 'Controller');
        
        class MovieController extends Controller
        {
            public function testNestedCake2Model() 
            {
                ${'$'}this->MovieMetadata-><caret>
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertFalse(result!!.isEmpty())
        assertFalse(result.contains("Director"))
        assertFalse(result.contains("MovieMetadata"))
    }

}