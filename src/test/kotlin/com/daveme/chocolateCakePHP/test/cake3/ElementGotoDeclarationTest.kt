package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.ElementGotoDeclarationHandler

class ElementGotoDeclarationTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/MovieController.php",
            "cake3/src/View/AppView.php",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Template/Movie/film_director.ctp",
            "cake3/src/Template/Element/Director/filmography.ctp",
            "cake3/vendor/cakephp.php",
        )
    }

    fun `test ElementGotoDeclarationHandler can go to render calls`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        ${'$'}this->element('<caret>Director/filmography');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "filmography.ctp")
    }

    fun `test ElementGotoDeclarationHandler does not go to render calls on other objects`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        ${'$'}obj = new SomeOtherObject();
        ${'$'}obj->element('<caret>Director/filmography');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
        assertTrue(elements!!.isEmpty())
    }

}