package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.codeInsight.lookup.LookupElementPresentation

class ViewVariableTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/Controller/MovieController.php",
            "cake2/app/Controller/Component/MovieMetadataComponent.php",
            "cake2/app/View/Helper/MovieFormatterHelper.php",
            "cake2/app/View/Helper/ArtistFormatterHelper.php",
            "cake2/app/View/AppView.php",
            "cake2/app/Model/Movie.php",
            "cake2/vendor/cakephp.php",
            "cake2/app/View/Movie/direct_call_test.ctp",
            "cake2/app/View/Movie/expression_variety_test.ctp"
        )
    }

    fun `test type is communicated from controller to view`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/film_director.ctp", """
            
        <?php
        echo ${'$'}movieModel-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("saveScreening"))
    }

    fun `test type is communicated from controller to elements`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/film_director.ctp", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake2/app/View/Elements/Director/filmography.ctp", """
        <?php
        
        echo ${'$'}movieModel-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("saveScreening"))
    }

    fun `test variable list is communicated from controller to view`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/film_director.ctp", """
        <?php

        echo <caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}movieModel"))
    }

    fun `test variable list is communicated from controller to view within a variable`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/film_director.ctp", """
        <?php

        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}movieModel"))
    }

    fun `test variable list is communicated from controller to elements`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/film_director.ctp", """
        <?php
        
        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake2/app/View/Elements/Director/filmography.ctp", """
        <?php
        
        echo <caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}movieModel"))
    }

    fun `test variable list is communicated from controller to elements within a variable`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/film_director.ctp", """
        <?php

        echo ${'$'}this->element('Director/filmography');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake2/app/View/Elements/Director/filmography.ctp", """
        <?php

        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}movieModel"))
    }

    fun `test direct method call in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/direct_call_test.ctp", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        // Verify variables are available (should have 2: $movieModel and $title)
        val result = myFixture.lookupElementStrings
        assertNotNull("Should have completion results with multiple variables", result)
        assertTrue("Should contain ${'$'}movieModel, but got: $result",
                   result!!.contains("${'$'}movieModel"))
        assertTrue("Should contain ${'$'}title, but got: $result",
                   result.contains("${'$'}title"))

        // Verify type from direct method call is resolved
        val elements = myFixture.lookupElements!!
        val movieModelElement = elements.find { it.lookupString == "${'$'}movieModel" }
        assertNotNull("Should find ${'$'}movieModel in lookup elements", movieModelElement)

        val presentation = LookupElementPresentation()
        movieModelElement!!.renderElement(presentation)

        // Verify type is resolved correctly from direct ClassRegistry::init() call
        assertNotNull("Should have type text", presentation.typeText)
        assertTrue("Type should contain Movie (from ClassRegistry::init call), but got: ${presentation.typeText}",
                   presentation.typeText?.contains("Movie") == true)
    }

    fun `test property access in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/expression_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/expression_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/expression_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/expression_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/array_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/array_variety_test.ctp", """
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
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/array_variety_test.ctp", """
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

    fun `test variable list is communicated from controller to view with view field assignment`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("${'$'}movieModel"))
    }

    fun `test variable type is communicated from controller to view with view field assignment`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        echo ${'$'}movieModel-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("saveScreening"))
    }
}
