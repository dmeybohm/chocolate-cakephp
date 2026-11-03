package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class RouteTargetCompletionTest : Cake4BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake4/src4/Controller/AppController.php",
            "cake4/src4/Controller/MovieController.php",
            "cake4/src4/Controller/ArticleController.php",
            "cake4/src4/Controller/Admin/ReportsController.php",
            "cake4/vendor/test/test_plugin/src/Controller/WidgetsController.php",
            "cake4/templates/Movie/artist.php",
        )
    }

    private fun routes(body: String): String = """
        <?php
        use Cake\Routing\RouteBuilder;
        return static function (RouteBuilder ${'$'}routes): void {
        $body
        };
        """.trimIndent()

    private fun completeRoutes(body: String): List<String>? {
        myFixture.configureByFilePathAndText("cake4/config/routes.php", routes(body))
        myFixture.completeBasic()
        return myFixture.lookupElementStrings
    }

    private fun completeTemplate(body: String): List<String>? {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", "<?php\n$body")
        myFixture.completeBasic()
        return myFixture.lookupElementStrings
    }

    fun `test controller key outside any scope offers the app's unprefixed controllers`() {
        val result = completeRoutes("""${'$'}routes->connect('/x', ['controller' => '<caret>', 'action' => 'index']);""")
        assertNotNull(result)
        assertContainsElements(result!!, "Movie", "Article")
        assertDoesntContain(result, "Reports", "Widgets", "App")
    }

    fun `test controller key inside a prefix scope offers that prefix's controllers`() {
        val result = completeRoutes("""
            ${'$'}routes->prefix('Admin', function (${'$'}builder) {
                ${'$'}builder->connect('/x', ['controller' => '<caret>']);
            });
        """)
        assertEquals(listOf("Reports"), result)
    }

    fun `test prefix key in the array selects the prefix`() {
        val result = completeRoutes("""
            ${'$'}routes->connect('/x', ['prefix' => 'Admin', 'controller' => '<caret>']);
        """)
        assertEquals(listOf("Reports"), result)
    }

    fun `test controller key inside a plugin scope offers that plugin's controllers`() {
        val result = completeRoutes("""
            ${'$'}routes->plugin('TestPlugin', function (${'$'}builder) {
                ${'$'}builder->connect('/x', ['controller' => '<caret>']);
            });
        """)
        assertEquals(listOf("Widgets"), result)
    }

    fun `test null plugin key inside a plugin scope offers the app's controllers`() {
        val result = completeRoutes("""
            ${'$'}routes->plugin('TestPlugin', function (${'$'}builder) {
                ${'$'}builder->connect('/x', ['plugin' => null, 'controller' => '<caret>']);
            });
        """)
        assertNotNull(result)
        assertContainsElements(result!!, "Movie", "Article")
        assertDoesntContain(result, "Widgets")
    }

    fun `test another plugin inside a plugin scope offers nothing`() {
        val result = completeRoutes("""
            ${'$'}routes->plugin('TestPlugin', function (${'$'}builder) {
                ${'$'}builder->connect('/x', ['plugin' => 'Other', 'controller' => '<caret>']);
            });
        """)
        assertTrue("Expected no completions, got $result", result.isNullOrEmpty())
    }

    fun `test action key offers the actions of the controller key`() {
        val result = completeRoutes("""${'$'}routes->connect('/x', ['controller' => 'Movie', 'action' => '<caret>']);""")
        assertNotNull(result)
        assertContainsElements(result!!, "artist", "filmDirector", "meta")
        assertDoesntContain(result, "initialize", "redirect", "beforeFilter", "set")
    }

    fun `test action key inside a prefix scope resolves the controller in the prefix`() {
        val result = completeRoutes("""
            ${'$'}routes->prefix('Admin', function (${'$'}builder) {
                ${'$'}builder->get('/x', ['controller' => 'Reports', 'action' => '<caret>']);
            });
        """)
        assertNotNull(result)
        assertContainsElements(result!!, "index", "monthly")
    }

    fun `test action key with no controller key in a controller offers its own actions`() {
        myFixture.configureByFilePathAndText("cake4/src4/Controller/MovieController.php", """
        <?php
        namespace App\Controller;
        use Cake\Controller\Controller;
        class MovieController extends Controller
        {
            public function artist() {}
            public function meta() {}
            public function go()
            {
                return ${'$'}this->redirect(['action' => '<caret>']);
            }
        }
        """.trimIndent())
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "artist", "meta", "go")
        assertDoesntContain(result, "redirect")
    }

    fun `test keys in a template resolve against the rendering controller`() {
        val actions = completeTemplate("""echo ${'$'}this->Html->link('x', ['action' => '<caret>']);""")
        assertNotNull(actions)
        assertContainsElements(actions!!, "artist", "filmDirector", "meta")

        val controllers = completeTemplate("""echo ${'$'}this->Url->build(['controller' => '<caret>']);""")
        assertNotNull(controllers)
        assertContainsElements(controllers!!, "Movie", "Article")
        assertDoesntContain(controllers, "Widgets", "Reports")
    }

    fun `test plugin and prefix keys offer the known plugins and prefixes`() {
        val result = completeRoutes("""
            ${'$'}routes->connect('/x', ['plugin' => '<caret>', 'controller' => 'Widgets']);
        """)
        assertEquals(listOf("TestPlugin"), result)

        val prefixes = completeRoutes("""
            ${'$'}routes->connect('/x', ['prefix' => '<caret>', 'controller' => 'Reports']);
        """)
        assertEquals(listOf("Admin"), prefixes)
    }

    fun `test resources offers the controllers of the scope`() {
        val result = completeRoutes("""${'$'}routes->resources('<caret>');""")
        assertNotNull(result)
        assertContainsElements(result!!, "Movie", "Article")
        assertDoesntContain(result, "Reports", "Widgets")
    }

    fun `test string targets offer controllers with their plugin and prefix parts`() {
        val result = completeRoutes("""${'$'}routes->connect('/x', '<caret>');""")
        assertNotNull(result)
        assertContainsElements(result!!, "Movie", "Article", "Admin/Reports", "TestPlugin.Widgets")
    }

    fun `test string targets offer actions after the separator`() {
        val result = completeRoutes("""${'$'}routes->connect('/x', 'Movie::<caret>');""")
        assertNotNull(result)
        assertContainsElements(result!!, "Movie::artist", "Movie::meta")
        assertDoesntContain(result, "Movie::initialize")
    }

    fun `test string targets inside a prefix scope are relative to it`() {
        val result = completeRoutes("""
            ${'$'}routes->prefix('Admin', function (${'$'}builder) {
                ${'$'}builder->connect('/x', '<caret>');
            });
        """)
        // A plugin controller without the prefix is unreachable from inside the prefix scope
        assertEquals(listOf("Reports"), result)
    }

    fun `test an unrelated method's array offers nothing`() {
        val result = completeTemplate("""echo ${'$'}this->Form->button('x', ['action' => '<caret>', 'other' => 'y']);""")
        if (result != null) {
            assertDoesntContain(result, "artist", "meta")
        }
    }
}
