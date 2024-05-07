package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import org.junit.Test

public class ControllerTest : Cake3BaseTestCase() {

    override fun prepareTest() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/plugins/Controller/Component/InsidePluginComponent.php",
            "cake3/src/View/AppView.php",
            "cake3/vendor/cakephp.php"
        )
    }

    @Test
    public fun `test completing a component inside a controller`() {
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
    public fun `test methods on a component are not magically auto-completed for ViewBuilder`() {

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

    public fun `test nested model completion contributor in cake3 only if parent is a model`() {
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
        assertFalse(result!!.contains("Director"))

        // TODO
        // assertFalse(result.contains("MovieMetadata"))
    }

}