package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.view.ElementToUsagesGotoRelatedProvider

class ElementToUsagesGotoRelatedTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
    }

    fun `test element file navigates to view that uses it`() {
        // breadcrumb element is used by index_with_elements view
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/vendor/cakephp.php",
            "cake5/templates/element/breadcrumb.php",
            "cake5/templates/Movie/index_with_elements.php",
        )

        // Open the element file
        val elementFile = myFixture.configureByFile("cake5/templates/element/breadcrumb.php")

        // Get any element in the element file
        val element = elementFile.firstChild
        assertNotNull("Element file should have content", element)

        // Get related items from the provider
        val provider = ElementToUsagesGotoRelatedProvider()
        val items = provider.getItems(element!!)

        // Should find at least one related view
        assertTrue("Should find at least one related view, found ${items.size}", items.isNotEmpty())

        // Verify the view file is in the results
        val fileNames = items.map { it.element?.containingFile?.name }
        assertTrue("Should contain index_with_elements.php, found: $fileNames",
                   fileNames.contains("index_with_elements.php"))
    }

    fun `test element file navigates to another element that uses it`() {
        // breadcrumb element is used by layout_header element
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/vendor/cakephp.php",
            "cake5/templates/element/breadcrumb.php",
            "cake5/templates/element/layout_header.php",
        )

        val elementFile = myFixture.configureByFile("cake5/templates/element/breadcrumb.php")
        val element = elementFile.firstChild
        assertNotNull(element)

        val provider = ElementToUsagesGotoRelatedProvider()
        val items = provider.getItems(element!!)

        assertTrue("Should find related element", items.isNotEmpty())

        val fileNames = items.map { it.element?.containingFile?.name }
        assertTrue("Should contain layout_header.php, found: $fileNames",
                   fileNames.contains("layout_header.php"))
    }

    fun `test element used by both views and elements shows both`() {
        // breadcrumb is used by both index_with_elements.php (view) and layout_header.php (element)
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/vendor/cakephp.php",
            "cake5/templates/element/breadcrumb.php",
            "cake5/templates/element/layout_header.php",
            "cake5/templates/Movie/index_with_elements.php",
        )

        val elementFile = myFixture.configureByFile("cake5/templates/element/breadcrumb.php")
        val element = elementFile.firstChild
        assertNotNull(element)

        val provider = ElementToUsagesGotoRelatedProvider()
        val items = provider.getItems(element!!)

        // Should find both the view and the element
        assertTrue("Should find at least 2 references", items.size >= 2)

        val fileNames = items.map { it.element?.containingFile?.name }
        assertTrue("Should contain both view and element references, found: $fileNames",
                   fileNames.contains("index_with_elements.php") &&
                   fileNames.contains("layout_header.php"))
    }

    fun `test related items are grouped correctly`() {
        // Test that views are grouped under "Views" and elements under "Elements"
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/vendor/cakephp.php",
            "cake5/templates/element/breadcrumb.php",
            "cake5/templates/element/layout_header.php",
            "cake5/templates/Movie/index_with_elements.php",
        )

        val elementFile = myFixture.configureByFile("cake5/templates/element/breadcrumb.php")
        val element = elementFile.firstChild
        assertNotNull(element)

        val provider = ElementToUsagesGotoRelatedProvider()
        val items = provider.getItems(element!!)

        assertTrue("Should have items", items.isNotEmpty())

        // Verify grouping
        val viewItems = items.filter { it.group == "Views" }
        val elementItems = items.filter { it.group == "Elements" }

        assertTrue("Should have items in Views group", viewItems.isNotEmpty())
        assertTrue("Should have items in Elements group", elementItems.isNotEmpty())

        // Verify correct files are in correct groups
        val viewFileNames = viewItems.mapNotNull { it.element?.containingFile?.name }
        val elementFileNames = elementItems.mapNotNull { it.element?.containingFile?.name }

        assertTrue("Views group should contain index_with_elements.php",
                   viewFileNames.contains("index_with_elements.php"))
        assertTrue("Elements group should contain layout_header.php",
                   elementFileNames.contains("layout_header.php"))
    }

    fun `test nested element navigation`() {
        // Test Director/filmography element navigation
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/vendor/cakephp.php",
            "cake5/templates/element/Director/filmography.php",
            "cake5/templates/Movie/element_with_params.php",
        )

        val elementFile = myFixture.configureByFile("cake5/templates/element/Director/filmography.php")
        val element = elementFile.firstChild
        assertNotNull(element)

        val provider = ElementToUsagesGotoRelatedProvider()
        val items = provider.getItems(element!!)

        assertTrue("Should find related view", items.isNotEmpty())

        val fileNames = items.map { it.element?.containingFile?.name }
        assertTrue("Should contain element_with_params.php, found: $fileNames",
                   fileNames.contains("element_with_params.php"))
    }

    fun `test non-element file returns empty`() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/vendor/cakephp.php",
            "cake5/src5/Controller/MovieController.php",
        )

        // Open a controller file (not an element)
        val controllerFile = myFixture.configureByFile("cake5/src5/Controller/MovieController.php")
        val element = controllerFile.firstChild
        assertNotNull(element)

        val provider = ElementToUsagesGotoRelatedProvider()
        val items = provider.getItems(element!!)

        assertTrue("Should return empty for non-element file", items.isEmpty())
    }

    fun `test non-element view file returns empty`() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/vendor/cakephp.php",
            "cake5/templates/Movie/artist.php",
        )

        // Open a regular view file (not an element)
        val viewFile = myFixture.configureByFile("cake5/templates/Movie/artist.php")
        val element = viewFile.firstChild
        assertNotNull(element)

        val provider = ElementToUsagesGotoRelatedProvider()
        val items = provider.getItems(element!!)

        assertTrue("Should return empty for non-element view file", items.isEmpty())
    }
}
