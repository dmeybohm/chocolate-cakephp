package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class ViewVariableTest : Cake3BaseTestCase() {

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
            "cake3/vendor/cakephp.php"
        )
    }

    fun `test type is communicated from controller to view`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/film_director.ctp", """
            
        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test type is communicated from controller to elements`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/film_director.ctp", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake3/src/Template/Element/Director/filmography.ctp", """
        <?php
        
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test variable list is communicated from controller to view`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/film_director.ctp", """
            
        <?php
        echo <caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from nested controller to view`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Nested/MyNested/some_nested_action.ctp", """
            
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from controller to view within a variable`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/film_director.ctp", """
            
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from nested controller to view within a variable`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Nested/MyNested/some_nested_action.ctp", """
            
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from controller to elements`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/film_director.ctp", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake3/src/Template/Element/Director/filmography.ctp", """
        <?php
        
        echo <caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from nested controller to elements`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Nested/MyNested/some_nested_action.ctp", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake3/src/Template/Element/Director/filmography.ctp", """
        <?php
        
        echo <caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from controller to elements within a variable`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/film_director.ctp", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake3/src/Template/Element/Director/filmography.ctp", """
        <?php
        
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from nested controller to elements within a variable`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Nested/MyNested/some_nested_action.ctp", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake3/src/Template/Element/Director/filmography.ctp", """
        <?php
        
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

}