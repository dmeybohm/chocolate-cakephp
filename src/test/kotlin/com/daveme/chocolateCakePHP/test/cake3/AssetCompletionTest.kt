package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class AssetCompletionTest : Cake3BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/View/AppView.php",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/webroot/css/movie.css",
            "cake3/webroot/js/movie.js",
            "cake3/webroot/img/pluginIcon.svg",
            "cake3/vendor/cakephp.php",
        )
    }

    fun `test completing css assets`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->css('<caret>');
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("movie"))
    }

    fun `test completing js assets`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->script('<caret>');
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("movie"))
    }

    fun `test completing image assets`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->image('<caret>');
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("pluginIcon.svg"))
    }

    fun `test other methods are not affected`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->notCss('<caret>');
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertFalse(result!!.contains("movie"))
    }
}
