package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

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
            "cake2/app/webroot/img/movie.jpg",
            "cake2/vendor/cakephp.php",
        )
    }

    fun `test can go to css assets`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->css('<caret>movie');
        """.trimIndent())
        assertCurrentCaretNavigatesToFilename("movie.css")
    }

    fun `test can go to js assets`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->script('<caret>movie');
        """.trimIndent())
        assertCurrentCaretNavigatesToFilename("movie.js")
    }

    fun `test can go to img assets`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->image('<caret>movie.jpg');
        """.trimIndent())
        assertCurrentCaretNavigatesToFilename("movie.jpg")
    }

}