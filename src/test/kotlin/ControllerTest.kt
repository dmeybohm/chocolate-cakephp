package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Settings

class ControllerTest : BaseTestCase() {

    fun `test completing a component inside a controller`() {
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

    fun `test completing component methods inside a controller`() {
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

    fun `test completing a cake2 model inside a controller`() {
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

    fun `test completing cake2 model methods inside a controller`() {
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

}