package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.view.ViewToControllerGotoRelatedProvider
import com.jetbrains.php.lang.psi.elements.Method

class ThemedViewToControllerTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake2/vendor/cakephp.php",
            "cake2/app/Controller/MovieController.php",
            "cake2/app/View/Themed/MyTheme/Movie/themed_index.ctp",
        )
    }

    fun `test themed view navigates to controller method`() {
        // Open the themed view file
        val viewFile = myFixture.configureByFile("cake2/app/View/Themed/MyTheme/Movie/themed_index.ctp")

        // Get any element in the view file
        val element = viewFile.firstChild
        assertNotNull("View file should have content", element)

        // Get related items from the provider
        val provider = ViewToControllerGotoRelatedProvider()
        val items = provider.getItems(element!!)

        // Should find the themed_index() method
        assertTrue("Should find at least one related controller method, but found: ${items.size}", items.isNotEmpty())

        // Verify we found the correct method
        val methods = items.mapNotNull { (it.element as? Method)?.name }
        assertTrue("Should contain 'themed_index' method, found: $methods", methods.contains("themed_index"))
    }

    fun `test themed view related items are grouped under Controllers`() {
        val viewFile = myFixture.configureByFile("cake2/app/View/Themed/MyTheme/Movie/themed_index.ctp")
        val element = viewFile.firstChild
        assertNotNull(element)

        val provider = ViewToControllerGotoRelatedProvider()
        val items = provider.getItems(element!!)

        assertTrue("Should have items", items.isNotEmpty())

        // Verify all items are in the "Controllers" group
        items.forEach { item ->
            assertEquals("Items should be in 'Controllers' group", "Controllers", item.group)
        }
    }
}
