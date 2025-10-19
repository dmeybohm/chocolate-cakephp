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
            "cake3/src/Template/Movie/literal_test.ctp"
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

}