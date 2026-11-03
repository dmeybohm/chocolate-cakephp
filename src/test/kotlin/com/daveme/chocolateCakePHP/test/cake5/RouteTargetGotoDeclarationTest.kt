package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.routing.RouteTargetGotoDeclarationHandler
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.psi.PsiDirectory
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass

class RouteTargetGotoDeclarationTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Controller/MovieController.php",
            "cake5/src5/Controller/ArticleController.php",
            "cake5/src5/Controller/Admin/ReportsController.php",
            "cake5/vendor/test/test_plugin/src/Controller/WidgetsController.php",
            "cake5/templates/Movie/artist.php",
        )
    }

    private fun targets(): List<Any> =
        gotoDeclarationHandlerTargets(RouteTargetGotoDeclarationHandler())?.toList() ?: emptyList()

    private fun configureRoutes(body: String) {
        myFixture.configureByFilePathAndText("cake5/config/routes.php", """
        <?php
        use Cake\Routing\RouteBuilder;
        return static function (RouteBuilder ${'$'}routes): void {
        $body
        };
        """.trimIndent())
    }

    private fun assertGoesToClasses(vararg fqns: String) {
        assertEquals(fqns.toList(), targets().filterIsInstance<PhpClass>().map { it.fqn })
    }

    private fun assertGoesToMethod(classFqn: String, methodName: String) {
        val methods = targets().filterIsInstance<Method>()
        assertEquals(listOf(classFqn to methodName), methods.map { it.containingClass?.fqn to it.name })
    }

    fun `test controller key goes to the controller class`() {
        configureRoutes("""${'$'}routes->connect('/x', ['controller' => 'Mov<caret>ie', 'action' => 'artist']);""")
        assertGoesToClasses("\\App\\Controller\\MovieController")
    }

    fun `test action key goes to the action method`() {
        configureRoutes("""${'$'}routes->connect('/x', ['controller' => 'Movie', 'action' => 'film<caret>Director']);""")
        assertGoesToMethod("\\App\\Controller\\MovieController", "filmDirector")
    }

    fun `test a framework method is not an action target`() {
        configureRoutes("""${'$'}routes->connect('/x', ['controller' => 'Movie', 'action' => 'redi<caret>rect']);""")
        assertEmpty(targets())
    }

    fun `test controller key inside a prefix scope goes to the prefixed class`() {
        configureRoutes("""
            ${'$'}routes->prefix('Admin', function (${'$'}builder) {
                ${'$'}builder->connect('/x', ['controller' => 'Rep<caret>orts']);
            });
        """)
        assertGoesToClasses("\\App\\Controller\\Admin\\ReportsController")
    }

    fun `test plugin key in the array selects the plugin's class`() {
        configureRoutes("""${'$'}routes->connect('/x', ['plugin' => 'TestPlugin', 'controller' => 'Wid<caret>gets']);""")
        assertGoesToClasses("\\TestPlugin\\Controller\\WidgetsController")
    }

    fun `test prefix key goes to the prefix directory`() {
        configureRoutes("""${'$'}routes->connect('/x', ['prefix' => 'Ad<caret>min', 'controller' => 'Reports']);""")
        assertEquals(listOf("Admin"), targets().filterIsInstance<PsiDirectory>().map { it.name })
    }

    fun `test string target goes to the class before the separator and the method after it`() {
        configureRoutes("""${'$'}routes->connect('/x', 'Admin/Rep<caret>orts::monthly');""")
        assertGoesToClasses("\\App\\Controller\\Admin\\ReportsController")

        configureRoutes("""${'$'}routes->connect('/x', 'Admin/Reports::mon<caret>thly');""")
        assertGoesToMethod("\\App\\Controller\\Admin\\ReportsController", "monthly")
    }

    fun `test resources goes to the controller class`() {
        configureRoutes("""${'$'}routes->resources('Art<caret>icle');""")
        assertGoesToClasses("\\App\\Controller\\ArticleController")
    }

    fun `test action key in a template resolves against the rendering controller`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->Html->link('x', ['action' => 'me<caret>ta']);
        """.trimIndent())
        assertGoesToMethod("\\App\\Controller\\MovieController", "meta")
    }

    fun `test controller key in a template without a plugin key stays in the app`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->Url->build(['controller' => 'Wid<caret>gets']);
        """.trimIndent())
        assertEmpty(targets())
    }

    fun `test a controller key outside any context matches by name across plugins`() {
        myFixture.configureByText("helpers.php", """
        <?php
        use Cake\Routing\Router;
        function widgetUrl() {
            return Router::url(['controller' => 'Wid<caret>gets']);
        }
        """.trimIndent())
        assertGoesToClasses("\\TestPlugin\\Controller\\WidgetsController")
    }

    fun `test an unrelated method's array has no targets`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->Form->button('x', ['action' => 'me<caret>ta']);
        """.trimIndent())
        assertEmpty(targets())
    }
}
