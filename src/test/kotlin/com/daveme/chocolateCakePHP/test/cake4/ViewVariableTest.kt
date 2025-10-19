package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.codeInsight.lookup.LookupElementPresentation

class ViewVariableTest : Cake4BaseTestCase() {

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
            "cake4/vendor/cakephp.php",
            "cake4/templates/Movie/direct_call_test.php"
        )
    }

    fun `test type is communicated from controller to view`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/film_director.php", """
            
        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test type is communicated from controller to elements`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/film_director.php", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())

        myFixture.configureByFilePathAndText("cake4/templates/element/Director/filmography.php", """
        <?php
        
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test variable list is communicated from controller to view`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/film_director.php", """

        <?php
        echo <caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))

        // Verify that type from fetchTable() is correctly resolved via LOCAL variable
        val elements = myFixture.lookupElements!!
        val moviesTableElement = elements.find { it.lookupString == "${'$'}moviesTable" }
        assertNotNull("Should find ${'$'}moviesTable", moviesTableElement)

        val presentation = LookupElementPresentation()
        moviesTableElement!!.renderElement(presentation)

        // Should have MoviesTable type (from fetchTable type provider via PSI)
        assertNotNull("Should have type text", presentation.typeText)
        assertTrue("Type should contain MoviesTable, but got: ${presentation.typeText}",
                   presentation.typeText?.contains("MoviesTable") == true)
    }

    fun `test variable list is communicated from nested controller to view`() {
        myFixture.configureByFilePathAndText("cake4/templates/Nested/MyNested/some_nested_action.php", """
            
        <?php
        echo <caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from controller to view within a variable`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/film_director.php", """
            
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from nested controller to view within a variable`() {
        myFixture.configureByFilePathAndText("cake4/templates/Nested/MyNested/some_nested_action.php", """
            
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from controller to elements`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/film_director.php", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake4/templates/element/Director/filmography.php", """
        <?php
        
        echo <caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from nested controller to elements`() {
        myFixture.configureByFilePathAndText("cake4/templates/Nested/MyNested/some_nested_action.php", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake4/templates/element/Director/filmography.php", """
        <?php
        
        echo <caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from controller to elements within a variable`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/film_director.php", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake4/templates/element/Director/filmography.php", """
        <?php
        
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from nested controller to elements within a variable`() {
        myFixture.configureByFilePathAndText("cake4/templates/Nested/MyNested/some_nested_action.php", """
        <?php

        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake4/templates/element/Director/filmography.php", """
        <?php

        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from controller to json view`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/json/film_director.php", """

        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable type is communicated from controller to json view`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/json/film_director.php", """

        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test variable list is communicated from controller to xml view`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/xml/film_director.php", """

        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test direct method call in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/direct_call_test.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        // Verify variables are available (should have 2: $moviesTable and $title)
        val result = myFixture.lookupElementStrings
        assertNotNull("Should have completion results with multiple variables", result)
        assertTrue("Should contain ${'$'}moviesTable, but got: $result",
                   result!!.contains("${'$'}moviesTable"))
        assertTrue("Should contain ${'$'}title, but got: $result",
                   result.contains("${'$'}title"))

        // Verify type from direct method call is resolved
        val elements = myFixture.lookupElements!!
        val moviesTableElement = elements.find { it.lookupString == "${'$'}moviesTable" }
        assertNotNull("Should find ${'$'}moviesTable in lookup elements", moviesTableElement)

        val presentation = LookupElementPresentation()
        moviesTableElement!!.renderElement(presentation)

        // Verify type is resolved correctly from direct method call
        assertNotNull("Should have type text", presentation.typeText)
        assertTrue("Type should contain MoviesTable (from direct fetchTable call), but got: ${presentation.typeText}",
                   presentation.typeText?.contains("MoviesTable") == true)
    }

}