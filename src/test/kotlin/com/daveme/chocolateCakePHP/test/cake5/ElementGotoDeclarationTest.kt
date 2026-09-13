package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.ElementGotoDeclarationHandler

class ElementGotoDeclarationTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Controller/MovieController.php",
            "cake5/src5/View/AppView.php",
            "cake5/templates/Movie/artist.php",
            "cake5/templates/Movie/film_director.php",
            "cake5/templates/element/Director/filmography.php",
            "cake5/templates/element/breadcrumb.php",
            "cake5/vendor/cakephp.php",
        )
    }

    fun `test ElementGotoDeclarationHandler can go to render calls`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}this->element('<caret>Director/filmography');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "filmography.php")
    }

    fun `test ElementGotoDeclarationHandler does not go to render calls on other objects`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
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
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->element(${'$'}compact ? '<caret>Director/filmography' : 'breadcrumb');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "filmography.php")
    }

    fun `test goto declaration on false branch of ternary in element`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->element(${'$'}compact ? 'Director/filmography' : '<caret>breadcrumb');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "breadcrumb.php")
    }

    fun `test goto declaration on match arm in element`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->element(match (${'$'}mode) {
            'full' => '<caret>Director/filmography',
            default => 'breadcrumb',
        });
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "filmography.php")
    }

    fun `test goto declaration on ternary condition literal in element does not navigate`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->element((${'$'}mode === 'Director/<caret>filmography') ? 'breadcrumb' : 'breadcrumb');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
        assertTrue(elements!!.isEmpty())
    }

}
