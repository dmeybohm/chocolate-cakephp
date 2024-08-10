package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.AssetGotoDeclarationHandler

class AssetGotoDeclarationTest : Cake5BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Controller/MovieController.php",
            "cake5/src5/Controller/Nested/MyNestedController.php",
            "cake5/src5/Controller/Component/MovieMetadataComponent.php",
            "cake5/src5/View/Helper/MovieFormatterHelper.php",
            "cake5/src5/View/Helper/ArtistFormatterHelper.php",
            "cake5/src5/View/AppView.php",
            "cake5/src5/Model/Table/MoviesTable.php",
            "cake5/templates/Movie/artist.php",
            "cake5/webroot/css/movie.css",
            "cake5/webroot/js/movie.js",
            "cake5/webroot/img/pluginIcon.svg",
            "cake5/vendor/cakephp.php",
        )
    }

    fun `test can go to css assets`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->css('<caret>movie');
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "movie.css")
    }

    fun `test can go to js assets`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->script('<caret>movie');
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "movie.js")
    }

    fun `test can go to img assets`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->image('<caret>pluginIcon.svg');
        """.trimIndent())
        assertGotoDeclarationHandlerGoesToFilename(AssetGotoDeclarationHandler(), "pluginIcon.svg")
    }

}