package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class AssetGotoDeclarationTest : Cake4BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake4/src4/Controller/AppController.php",
            "cake4/src4/Controller/MovieController.php",
            "cake4/src4/Controller/Nested/MyNestedController.php",
            "cake4/src4/Controller/Component/MovieMetadataComponent.php",
            "cake4/src4/View/Helper/MovieFormatterHelper.php",
            "cake4/src4/View/Helper/ArtistFormatterHelper.php",
            "cake4/src4/View/AppView.php",
            "cake4/src4/Model/Table/MoviesTable.php",
            "cake4/templates/Movie/artist.php",
            "cake4/webroot/css/movie.css",
            "cake4/webroot/js/movie.js",
            "cake4/webroot/img/pluginIcon.svg",
            "cake4/vendor/cakephp.php",
        )
    }

    fun `test can go to css assets`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->css('<caret>movie');
        """.trimIndent())
        assertCurrentCaretNavigatesToFilename("movie.css")
    }

    fun `test can go to js assets`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->script('<caret>movie');
        """.trimIndent())
        assertCurrentCaretNavigatesToFilename("movie.js")
    }

    fun `test can go to img assets`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->image('<caret>pluginIcon.svg');
        """.trimIndent())
        assertCurrentCaretNavigatesToFilename("pluginIcon.svg")
    }

}