package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class AssetCompletionTest : Cake4BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake4/src4/Controller/AppController.php",
            "cake4/src4/View/AppView.php",
            "cake4/templates/Movie/artist.php",
            "cake4/webroot/css/movie.css",
            "cake4/webroot/js/movie.js",
            "cake4/webroot/img/pluginIcon.svg",
            "cake4/vendor/cakephp.php",
        )
    }

    fun `test completing css assets`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->css('<caret>');
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("movie"))
    }

    fun `test completing js assets`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->script('<caret>');
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("movie"))
    }

    fun `test completing image assets`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->image('<caret>');
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("pluginIcon.svg"))
    }

    fun `test other methods are not affected`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->notCss('<caret>');
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertFalse(result!!.contains("movie"))
    }

    fun `test completing css assets in array`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->css(['<caret>']);
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("movie"))
    }

    fun `test completing multiple css assets in array`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->css(['movie', '<caret>']);
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("movie"))
    }

    fun `test completing js assets in array`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->script(['<caret>']);
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("movie"))
    }

    fun `test completing image assets in array`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->image(['<caret>']);
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotEmpty(result)
        assertTrue(result!!.contains("pluginIcon.svg"))
    }

    fun `test no completion in second parameter options array`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->css(['movie'], ['block' => '<caret>']);
        """.trimIndent())

        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        // Should NOT contain asset files
        assertFalse(result!!.contains("movie"))
    }
}
