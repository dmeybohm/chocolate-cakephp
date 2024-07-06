package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class ControllerTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/plugins/Controller/Component/InsidePluginComponent.php",
            "cake3/src/View/AppView.php",
            "cake3/vendor/cakephp.php"
        )
    }

    fun `test completing a component inside a controller`() {
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

    fun `test completing a component from a plugin`() {
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


    fun `test methods on a component are not magically auto-completed for ViewBuilder`() {

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

    fun `test nested model completion contributor in cake3 only if parent is a model`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php
        namespace App\Controller;
        
        class MovieController extends \Cake\Controller\Controller
        {
            public function testNestedCake3Model() 
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