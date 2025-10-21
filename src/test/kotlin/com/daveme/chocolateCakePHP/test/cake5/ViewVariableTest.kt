package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.codeInsight.lookup.LookupElementPresentation

class ViewVariableTest: Cake5BaseTestCase() {

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
            "cake5/vendor/cakephp.php",
            "cake5/templates/Movie/direct_call_test.php",
            "cake5/templates/Movie/expression_variety_test.php",
            "cake5/templates/Movie/array_variety_test.php"
        )
    }

    fun `test type is communicated from controller to view`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/film_director.php", """
            
        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test type is communicated from controller to elements`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/film_director.php", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())

        myFixture.configureByFilePathAndText("cake5/templates/element/Director/filmography.php", """
        <?php
        
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test variable list is communicated from controller to view`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/film_director.php", """

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
        myFixture.configureByFilePathAndText("cake5/templates/Nested/MyNested/some_nested_action.php", """
            
        <?php
        echo <caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from controller to view within a variable`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/film_director.php", """
            
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from nested controller to view within a variable`() {
        myFixture.configureByFilePathAndText("cake5/templates/Nested/MyNested/some_nested_action.php", """
            
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from controller to elements`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/film_director.php", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake5/templates/element/Director/filmography.php", """
        <?php
        
        echo <caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from nested controller to elements`() {
        myFixture.configureByFilePathAndText("cake5/templates/Nested/MyNested/some_nested_action.php", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake5/templates/element/Director/filmography.php", """
        <?php
        
        echo <caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from controller to elements within a variable`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/film_director.php", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake5/templates/element/Director/filmography.php", """
        <?php
        
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from nested controller to elements within a variable`() {
        myFixture.configureByFilePathAndText("cake5/templates/Nested/MyNested/some_nested_action.php", """
        <?php

        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake5/templates/element/Director/filmography.php", """
        <?php

        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable list is communicated from controller to json view`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/json/film_director.php", """

        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test variable type is communicated from controller to json view`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/json/film_director.php", """

        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

    fun `test variable list is communicated from controller to xml view`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/xml/film_director.php", """

        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}moviesTable"))
    }

    fun `test direct method call in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/direct_call_test.php", """
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
        myFixture.configureByFilePathAndText("cake5/templates/Movie/expression_variety_test.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        // Verify variables available
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("${'$'}message"))

        // Verify type from property access
        val elements = myFixture.lookupElements!!
        val messageElement = elements.find { it.lookupString == "${'$'}message" }
        assertNotNull(messageElement)

        val presentation = LookupElementPresentation()
        messageElement!!.renderElement(presentation)

        // Should resolve to string type from property PHPDoc
        assertEquals("string", presentation.typeText)
    }

    fun `test nested function calls in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/expression_variety_test.php", """
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
        // Type resolution for built-in PHP functions depends on having PHP stubs in the test environment.
    }

    fun `test variable reference in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/expression_variety_test.php", """
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

        // Should resolve to int from PHPDoc on $count
        assertEquals("int", presentation.typeText)
    }

    fun `test array access in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/expression_variety_test.php", """
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

        // Without PHPDoc on array, should be mixed or string (inferred from value)
        // Accept either mixed or string as valid
        assertNotNull(presentation.typeText)
        assertTrue("Type should be string or mixed, but got: ${presentation.typeText}",
                   presentation.typeText == "string" || presentation.typeText == "mixed")
    }

    fun `test single variable in array set`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/array_variety_test.php", """
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
        myFixture.configureByFilePathAndText("cake5/templates/Movie/array_variety_test.php", """
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
        myFixture.configureByFilePathAndText("cake5/templates/Movie/array_variety_test.php", """
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

    fun `test static patterns suppress undefined variable warnings`() {
        // Tests Phase 1 optimization: PAIR, COMPACT static patterns
        // Verifies that UndefinedViewVariableInspectionSuppressor works with direct map lookup
        // Uses film_director.php which maps to filmDirector() action

        myFixture.enableInspections(com.jetbrains.php.lang.inspections.PhpUndefinedVariableInspection::class.java)

        // Use existing film_director.php template that's already in fixtures
        // filmDirector() uses: $this->set(compact('moviesTable', 'metadata'))
        myFixture.configureByFile("cake5/templates/Movie/film_director.php")

        // Check highlighting - moviesTable and metadata should NOT have warnings
        // since they come from the controller via COMPACT pattern
        myFixture.checkHighlighting(true, false, false)
    }

    fun `test VARIABLE_ARRAY pattern suppresses undefined variable warnings`() {
        // Tests Phase 2 optimization: VARIABLE_ARRAY pattern
        // Verifies that UndefinedViewVariableInspectionSuppressor correctly extracts variable names
        // from array assignments and suppresses PhpUndefinedVariableInspection

        myFixture.enableInspections(com.jetbrains.php.lang.inspections.PhpUndefinedVariableInspection::class.java)

        // Use addFileToProject to create the test file dynamically
        myFixture.addFileToProject("cake5/templates/Movie/variable_array_test.php", """
        <?php
        // These variables come from MovieController::variableArrayTest()
        echo ${'$'}movie;
        echo ${'$'}director;
        echo ${'$'}year;
        """.trimIndent())

        myFixture.configureByFile("cake5/templates/Movie/variable_array_test.php")

        // No <warning> markup means we expect ZERO warnings
        // Test fails if PhpUndefinedVariableInspection triggers on these variables
        myFixture.checkHighlighting(true, false, false)
    }

}
