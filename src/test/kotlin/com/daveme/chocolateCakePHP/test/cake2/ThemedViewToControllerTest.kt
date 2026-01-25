package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

/**
 * Tests that themed views in CakePHP 2 correctly resolve to their controllers.
 *
 * The fix ensures that templatesDirectoryOfViewFile() returns the theme directory
 * (e.g., "MyTheme") rather than the "Themed" directory, allowing proper view-to-controller
 * resolution for themed views.
 */
class ThemedViewToControllerTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake2/vendor/cakephp.php",
            "cake2/app/Controller/MovieController.php",
            "cake2/app/View/Themed/MyTheme/Movie/themed_index.ctp",
        )
    }

    fun `test themed view receives variables from controller`() {
        // The controller's themed_index() method sets 'title' and 'subtitle' variables
        // If the themed view correctly resolves to its controller, these variables should be available
        myFixture.configureByFilePathAndText("cake2/app/View/Themed/MyTheme/Movie/themed_index.ctp", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull("Completion results should not be null - themed view should resolve to controller", result)
        assertTrue("Should contain \$title variable from controller, but got: $result",
                   result!!.contains("\$title"))
        assertTrue("Should contain \$subtitle variable from controller, but got: $result",
                   result.contains("\$subtitle"))
    }

    fun `test themed view variable has correct type`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Themed/MyTheme/Movie/themed_index.ctp", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val elements = myFixture.lookupElements
        assertNotNull("Lookup elements should not be null", elements)

        val titleElement = elements!!.find { it.lookupString == "\$title" }
        assertNotNull("Should find \$title in lookup elements", titleElement)

        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        titleElement!!.renderElement(presentation)

        assertEquals("string", presentation.typeText)
    }
}
