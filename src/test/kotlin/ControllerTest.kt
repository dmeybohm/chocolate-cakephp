package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Settings

class ControllerTest : PluginTestCase() {

//    fun `test completing cake2 model`() {
//
//    }

    fun `test completing component`() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/vendor/cakephp.php"
        )

        myFixture.configureByText("MovieController.php",
        """
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

        myFixture.completeBasic();
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("generateMetadata"));
    }

}