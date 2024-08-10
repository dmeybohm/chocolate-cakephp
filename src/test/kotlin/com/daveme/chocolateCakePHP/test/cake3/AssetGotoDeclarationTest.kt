package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class AssetGotoDeclarationTest : Cake3BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/MovieController.php",
            "cake3/src/Controller/Nested/MyNestedController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/src/View/Helper/MovieFormatterHelper.php",
            "cake3/src/View/Helper/ArtistFormatterHelper.php",
            "cake3/src/View/AppView.php",
            "cake3/src/Model/Table/MoviesTable.php",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/webroot/css/movie.css",
            "cake3/webroot/js/movie.js",
            "cake3/webroot/img/movie.jpg",
            "cake3/vendor/cakephp.php",
        )
    }

    fun `test can go to css assets`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->css('<caret>movie');
        """.trimIndent())
        assertCurrentCaretNavigatesToFilename("movie.css")
    }

    fun `test can go to js assets`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->script('<caret>movie');
        """.trimIndent())
        assertCurrentCaretNavigatesToFilename("movie.js")
    }

    fun `test can go to img assets`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->image('<caret>movie.jpg');
        """.trimIndent())
        assertCurrentCaretNavigatesToFilename("movie.jpg")
    }

}