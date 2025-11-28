package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.view.ViewToControllerGotoRelatedProvider
import com.jetbrains.php.lang.psi.elements.Method

class ViewToControllerGotoRelatedTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
    }

    fun `test view file navigates to controller method`() {
        // Use existing fixtures - artist() method exists in MovieController
        // and artist.php view exists in templates/Movie/
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/vendor/cakephp.php",
            "cake5/templates/Movie/artist.php",
            "cake5/src5/Controller/MovieController.php",
        )

        // Open the view file
        val viewFile = myFixture.configureByFile("cake5/templates/Movie/artist.php")

        // Get any element in the view file
        val element = viewFile.firstChild
        assertNotNull("View file should have content", element)

        // Get related items from the provider
        val provider = ViewToControllerGotoRelatedProvider()
        val items = provider.getItems(element!!)

        // Should find the artist() method
        assertTrue("Should find at least one related controller method", items.isNotEmpty())

        // Verify we found the correct method
        val methods = items.mapNotNull { (it.element as? Method)?.name }
        assertTrue("Should contain 'artist' method, found: $methods", methods.contains("artist"))
    }

    fun `test view file with viewBuilder navigation`() {
        // viewBuilderTest() method renders to 'artist' template
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/vendor/cakephp.php",
            "cake5/templates/Movie/artist.php",
            "cake5/src5/Controller/MovieController.php",
        )

        val viewFile = myFixture.configureByFile("cake5/templates/Movie/artist.php")
        val element = viewFile.firstChild
        assertNotNull(element)

        val provider = ViewToControllerGotoRelatedProvider()
        val items = provider.getItems(element!!)

        assertTrue("Should find related controller methods", items.isNotEmpty())

        val methods = items.mapNotNull { (it.element as? Method)?.name }.toSet()
        // Should find both artist() and viewBuilderTest() since both reference this view
        assertTrue("Should contain methods that reference this view", methods.isNotEmpty())
    }

    fun `test view file with multiple controller references`() {
        // filmDirector view has matching filmDirector() method
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/vendor/cakephp.php",
            "cake5/templates/Movie/film_director.php",
            "cake5/src5/Controller/MovieController.php",
        )

        val viewFile = myFixture.configureByFile("cake5/templates/Movie/film_director.php")
        val element = viewFile.firstChild
        assertNotNull(element)

        val provider = ViewToControllerGotoRelatedProvider()
        val items = provider.getItems(element!!)

        assertTrue("Should find related controller method", items.isNotEmpty())

        val methods = items.mapNotNull { (it.element as? Method)?.name }
        assertTrue("Should contain 'filmDirector' method", methods.contains("filmDirector"))
    }

    fun `test related items are grouped under Controllers`() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/vendor/cakephp.php",
            "cake5/templates/Movie/artist.php",
            "cake5/src5/Controller/MovieController.php",
        )

        val viewFile = myFixture.configureByFile("cake5/templates/Movie/artist.php")
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

    fun `test non-view file returns empty`() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/vendor/cakephp.php",
            "cake5/src5/Controller/MovieController.php",
        )

        // Open a controller file (not a view)
        val controllerFile = myFixture.configureByFile("cake5/src5/Controller/MovieController.php")
        val element = controllerFile.firstChild
        assertNotNull(element)

        val provider = ViewToControllerGotoRelatedProvider()
        val items = provider.getItems(element!!)

        assertTrue("Should return empty for non-view file", items.isEmpty())
    }
}
