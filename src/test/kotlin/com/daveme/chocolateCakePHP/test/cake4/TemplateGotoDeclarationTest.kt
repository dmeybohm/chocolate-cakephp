package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.TemplateGotoDeclarationHandler

class TemplateGotoDeclarationTest : Cake4BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake4/src4/Controller/AppController.php",
            "cake4/src4/Controller/MovieController.php",
            "cake4/src4/View/AppView.php",
            "cake4/templates/Movie/artist.php",
            "cake4/templates/Movie/film_director.php",
            "cake4/vendor/cakephp.php",
        )
    }

    fun `test TemplateGotoDeclarationHandler can go to render calls`() {
        myFixture.configureByFilePathAndText("cake4/src4/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function artist() {
                ${'$'}this->render('<caret>film_director');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "film_director.php")
    }


    fun `test TemplateGotoDeclarationHandler does not go to render calls on other objects`() {

        myFixture.configureByFilePathAndText("cake4/src4/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function artist() {
                ${'$'}someOtherObject = new SomeOtherObject();
                ${'$'}someOtherObject->render('<caret>film_director');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
       assertTrue(elements!!.isEmpty())
    }
}