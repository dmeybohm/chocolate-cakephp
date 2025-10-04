package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.AssetGotoDeclarationHandler

class AssetGotoDeclarationTest : Cake2BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/Controller/MovieController.php",
            "cake2/app/Controller/Component/MovieMetadataComponent.php",
            "cake2/app/View/Helper/MovieFormatterHelper.php",
            "cake2/app/View/Helper/ArtistFormatterHelper.php",
            "cake2/app/View/AppView.php",
            "cake2/app/View/Movie/artist.ctp",
            "cake2/app/webroot/css/movie.css",
            "cake2/app/webroot/js/movie.js",
            "cake2/app/webroot/img/pluginIcon.svg",
            "cake2/vendor/cakephp.php",
        )
    }

    fun `test can go to css assets`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->css('<caret>movie');
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "movie.css")
    }

    fun `test other methods are not affected`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->notCss('<caret>movie');
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        val targets = gotoDeclarationHandlerTargets(handler)
        assertNotNull(targets)
        assertEmpty(targets!!)
    }

    fun `test can go to js assets`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->script('<caret>movie');
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "movie.js")
    }

    fun `test can go to img assets`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->image('<caret>pluginIcon.svg');
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "pluginIcon.svg")
    }

    fun `test can go to css assets in array`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->css(['<caret>movie']);
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "movie.css")
    }

    fun `test can go to js assets in array`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->script(['<caret>movie']);
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "movie.js")
    }

    fun `test can go to image assets in array`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->image(['<caret>pluginIcon.svg']);
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "pluginIcon.svg")
    }

    fun `test can go to second element in array`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->css(['forms', '<caret>movie']);
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "movie.css")
    }

    fun `test does not navigate on empty string in array`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->css(['<caret>']);
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        val targets = gotoDeclarationHandlerTargets(handler)
        assertNotNull(targets)
        assertEmpty(targets!!)
    }

}