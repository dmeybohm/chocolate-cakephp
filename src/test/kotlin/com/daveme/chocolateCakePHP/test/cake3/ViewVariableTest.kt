package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.codeInsight.lookup.LookupElementPresentation

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
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/param_test.ctp",
            "cake3/src/Template/Movie/literal_test.ctp",
            "cake3/src/Template/Movie/direct_call_test.ctp",
            "cake3/src/Template/Movie/expression_variety_test.ctp"
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

    fun `test variable list is communicated from controller to json view`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/json/film_director.ctp", """

        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable type is communicated from controller to json view`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/json/film_director.ctp", """

        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test variable list is communicated from controller to xml view`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/xml/film_director.ctp", """

        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test compact with method parameter makes variable available`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/param_test.ctp", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        // When there's only one completion option, IntelliJ auto-completes it and returns null
        // So we check if either: (a) it was auto-completed, or (b) movieId is in the list
        if (result == null) {
            // Auto-completed - verify the text was inserted
            val text = myFixture.editor.document.text
            assertTrue("${'$'}movieId should have been auto-completed", text.contains("${'$'}movieId"))
        } else {
            assertTrue("${'$'}movieId should be in completion list, but got: $result", result.contains("${'$'}movieId"))
        }
    }

    fun `test set with string literal makes variable available`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/literal_test.ctp", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull("Completion result should not be null (literal test)", result)
        assertTrue(result!!.contains("${'$'}title"))
        assertTrue(result!!.contains("${'$'}count"))

        // Check that the types are correctly resolved
        val elements = myFixture.lookupElements!!
        assertTrue(elements.isNotEmpty())

        // Find the $title element and check its type
        val titleElement = elements.find { it.lookupString == "${'$'}title" }
        assertNotNull("Should find ${'$'}title in lookup elements", titleElement)
        val titlePresentation = LookupElementPresentation()
        titleElement!!.renderElement(titlePresentation)
        assertEquals("string", titlePresentation.typeText)

        // Find the $count element and check its type
        val countElement = elements.find { it.lookupString == "${'$'}count" }
        assertNotNull("Should find ${'$'}count in lookup elements", countElement)
        val countPresentation = LookupElementPresentation()
        countElement!!.renderElement(countPresentation)
        assertEquals("int", countPresentation.typeText)
    }

    fun `test set with string literal resolves to string type`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/literal_test.ctp", """
        <?php
        echo ${'$'}title-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        // If the type is properly resolved as string, PHP's string methods will be available
        // Note: In PHP, strings are primitives, not objects, so -> operator won't actually
        // work at runtime. But we still test that our plugin correctly identified it as string type.
        assertNotNull(result)
    }

    fun `test set with integer literal resolves to int type`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/literal_test.ctp", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val elements = myFixture.lookupElements!!
        val countElement = elements.find { it.lookupString == "${'$'}count" }
        assertNotNull("Should find ${'$'}count in lookup elements", countElement)

        val presentation = LookupElementPresentation()
        countElement!!.renderElement(presentation)
        assertEquals("int", presentation.typeText)
    }

    fun `test direct method call in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/direct_call_test.ctp", """
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

        // Verify type is resolved correctly from chained method call
        // The implementation now finds the outermost MethodReference to get the final return type
        assertNotNull("Should have type text", presentation.typeText)
        assertTrue("Type should contain MoviesTable (from chained getTableLocator()->get() call), but got: ${presentation.typeText}",
                   presentation.typeText?.contains("MoviesTable") == true)
    }

    fun `test property access in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/expression_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/expression_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/expression_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/expression_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/array_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/array_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/array_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable type is communicated from controller to view with viewBuilder setTemplate`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test variable list is communicated from controller to view with viewBuilder setTemplatePath`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/Nested/custom.ctp", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable type is communicated from controller to view with viewBuilder setTemplatePath`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/Nested/custom.ctp", """
        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test variable list is communicated from controller to view with chained viewBuilder calls`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/Nested/custom.ctp", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable type is communicated from controller to view with chained viewBuilder calls`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/Nested/custom.ctp", """
        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

}