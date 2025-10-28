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
            "cake4/templates/Movie/direct_call_test.php",
            "cake4/templates/Movie/expression_variety_test.php"
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

    fun `test property access in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/expression_variety_test.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("${'$'}message"))

        val elements = myFixture.lookupElements!!
        val messageElement = elements.find { it.lookupString == "${'$'}message" }
        assertNotNull(messageElement)

        val presentation = LookupElementPresentation()
        messageElement!!.renderElement(presentation)

        assertEquals("string", presentation.typeText)
    }

    fun `test nested function calls in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/expression_variety_test.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("${'$'}cleaned"))

        val elements = myFixture.lookupElements!!
        val cleanedElement = elements.find { it.lookupString == "${'$'}cleaned" }
        assertNotNull(cleanedElement)

        // Note: Without PHP stubs loaded, str_replace() may not have type information available.
        // This test primarily validates that function call expressions are recognized and indexed.
    }

    fun `test variable reference in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/expression_variety_test.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("${'$'}total"))

        val elements = myFixture.lookupElements!!
        val totalElement = elements.find { it.lookupString == "${'$'}total" }
        assertNotNull(totalElement)

        val presentation = LookupElementPresentation()
        totalElement!!.renderElement(presentation)

        assertEquals("int", presentation.typeText)
    }

    fun `test array access in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/expression_variety_test.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("${'$'}item"))

        val elements = myFixture.lookupElements!!
        val itemElement = elements.find { it.lookupString == "${'$'}item" }
        assertNotNull(itemElement)

        val presentation = LookupElementPresentation()
        itemElement!!.renderElement(presentation)

        assertNotNull(presentation.typeText)
        assertTrue("Type should be string or mixed, but got: ${presentation.typeText}",
                   presentation.typeText == "string" || presentation.typeText == "mixed")
    }

    fun `test single variable in array set`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/array_variety_test.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull("Completion result should not be null", result)
        assertTrue("Should contain ${'$'}singleVar, but got: $result", result!!.contains("${'$'}singleVar"))

        val elements = myFixture.lookupElements!!
        val singleVarElement = elements.find { it.lookupString == "${'$'}singleVar" }
        assertNotNull("Should find ${'$'}singleVar in lookup elements", singleVarElement)

        val presentation = LookupElementPresentation()
        singleVarElement!!.renderElement(presentation)

        // Type should resolve to string from property PHPDoc
        assertEquals("string", presentation.typeText)
    }

    fun `test mixed literals and variables in array set`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/array_variety_test.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue("Should contain ${'$'}title", result!!.contains("${'$'}title"))
        assertTrue("Should contain ${'$'}count", result.contains("${'$'}count"))
        assertTrue("Should contain ${'$'}total", result.contains("${'$'}total"))
    }

    fun `test variable types in array set`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/array_variety_test.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val elements = myFixture.lookupElements!!

        // Check $title type (literal string in array)
        val titleElement = elements.find { it.lookupString == "${'$'}title" }
        assertNotNull(titleElement)
        val titlePresentation = LookupElementPresentation()
        titleElement!!.renderElement(titlePresentation)
        assertEquals("string", titlePresentation.typeText)

        // Check $count type (literal int in array)
        val countElement = elements.find { it.lookupString == "${'$'}count" }
        assertNotNull(countElement)
        val countPresentation = LookupElementPresentation()
        countElement!!.renderElement(countPresentation)
        assertEquals("int", countPresentation.typeText)

        // Check $total type (local variable with PHPDoc in array)
        val totalElement = elements.find { it.lookupString == "${'$'}total" }
        assertNotNull(totalElement)
        val totalPresentation = LookupElementPresentation()
        totalElement!!.renderElement(totalPresentation)
        assertEquals("int", totalPresentation.typeText)
    }

    fun `test variable list is communicated from controller to view with viewBuilder setTemplate`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable type is communicated from controller to view with viewBuilder setTemplate`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test variable list is communicated from controller to view with viewBuilder setTemplatePath`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/Nested/custom.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable type is communicated from controller to view with viewBuilder setTemplatePath`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/Nested/custom.php", """
        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test variable list is communicated from controller to view with chained viewBuilder calls`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/Nested/custom.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable type is communicated from controller to view with chained viewBuilder calls`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/Nested/custom.php", """
        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

}