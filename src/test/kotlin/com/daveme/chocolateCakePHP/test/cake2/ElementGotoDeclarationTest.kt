package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.ElementGotoDeclarationHandler

class ElementGotoDeclarationTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/Controller/MovieController.php",
            "cake2/app/View/AppView.php",
            "cake2/app/View/Movie/artist.ctp",
            "cake2/app/View/Movie/film_director.ctp",
            "cake2/app/View/Elements/Director/filmography.ctp",
            "cake2/app/View/Elements/Flash/default.ctp",
            "cake2/vendor/cakephp.php",
        )
    }

    fun `test ElementGotoDeclarationHandler can go to render calls`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->element('<caret>Director/filmography');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "filmography.ctp")
    }

    fun `test ElementGotoDeclarationHandler does not go to render calls on other objects`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}obj = new SomeOtherObject();
        ${'$'}obj->element('<caret>Director/filmography');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
        assertTrue(elements!!.isEmpty())
    }

    fun `test goto declaration on true branch of ternary in element`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        echo ${'$'}this->element(${'$'}compact ? '<caret>Director/filmography' : 'Flash/default');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "filmography.ctp")
    }

    fun `test goto declaration on false branch of ternary in element`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        echo ${'$'}this->element(${'$'}compact ? 'Director/filmography' : '<caret>Flash/default');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "default.ctp")
    }

    fun `test goto declaration on ternary condition literal in element does not navigate`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        echo ${'$'}this->element((${'$'}mode === 'Director/<caret>filmography') ? 'Flash/default' : 'Flash/default');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
        assertTrue(elements!!.isEmpty())
    }

}
