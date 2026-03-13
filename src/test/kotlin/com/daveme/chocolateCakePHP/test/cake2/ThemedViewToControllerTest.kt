package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.ThemeConfig
import com.daveme.chocolateCakePHP.controller.ControllerMethodLineMarker
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.ViewToControllerGotoRelatedProvider
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method

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

    fun `test themed view navigates to controller via GotoRelated`() {
        val viewFile = myFixture.configureByFile("cake2/app/View/Themed/MyTheme/Movie/themed_index.ctp")

        val element = viewFile.firstChild
        assertNotNull("View file should have content", element)

        val provider = ViewToControllerGotoRelatedProvider()
        val items = provider.getItems(element!!)

        assertTrue("Should find at least one related controller method", items.isNotEmpty())

        val methods = items.mapNotNull { (it.element as? Method)?.name }
        assertTrue("Should contain 'themed_index' method, found: $methods",
                   methods.contains("themed_index"))
    }

    fun `test controller line marker navigates to themed view`() {
        // Add a ThemeConfig so controller → view navigation can find the themed view
        val settings = Settings.getInstance(myFixture.project)
        val newState = settings.state.copy()
        newState.themeConfigs = listOf(ThemeConfig(
            pluginPath = "View/Themed/MyTheme"
        ))
        settings.loadState(newState)

        val files = myFixture.configureByFiles(
            "cake2/vendor/cakephp.php",
            "cake2/app/View/Themed/MyTheme/Movie/themed_index.ctp",
            "cake2/app/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        class MovieController extends AppController
        {
            public function themed_index() {
                ${'$'}this->set('title', 'Themed Index');
                ${'$'}this->set('subtitle', 'My Theme');
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        val method = PsiTreeUtil.findChildOfType(myFixture.file, Method::class.java)
        assertNotNull(method)

        val markers = calculateLineMarkers(method!!.nameIdentifier!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertTrue("Should find at least one related view", items.size >= 1)

        val infos = getRelatedItemInfos(items)
        assertTrue("Should navigate to themed_index.ctp in Movie dir, got: $infos",
                   infos.contains(RelatedItemInfo(filename = "themed_index.ctp", containingDir = "Movie")))
    }
}
