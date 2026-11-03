package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.ResolvedRouteTarget
import com.daveme.chocolateCakePHP.cake.RouteContextController
import com.daveme.chocolateCakePHP.cake.RouteControllerName
import com.daveme.chocolateCakePHP.cake.RouteKey
import com.daveme.chocolateCakePHP.cake.RouteScope
import com.daveme.chocolateCakePHP.cake.RouteTarget
import com.daveme.chocolateCakePHP.cake.RouteValue
import com.daveme.chocolateCakePHP.cake.controllerClassFqn
import com.daveme.chocolateCakePHP.cake.resolveInContext
import com.daveme.chocolateCakePHP.cake.resolveInScope
import com.daveme.chocolateCakePHP.cake.routeActions
import com.daveme.chocolateCakePHP.cake.routeContextOf
import com.daveme.chocolateCakePHP.cake.routeControllerNameFromClass
import com.daveme.chocolateCakePHP.cake.routeScopeOf
import com.daveme.chocolateCakePHP.cake.routeTargetControllers
import com.daveme.chocolateCakePHP.cake.routeTargetFromArray
import com.daveme.chocolateCakePHP.cake.routeTargetFromString
import com.daveme.chocolateCakePHP.getControllerSubclasses
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression

class RouteTargetModelTest : Cake5BaseTestCase() {

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

    private val settings: Settings get() = Settings.getInstance(myFixture.project)
    private val phpIndex: PhpIndex get() = PhpIndex.getInstance(myFixture.project)

    private fun elementAtCaret(): PsiElement = myFixture.file.findElementAt(myFixture.caretOffset)!!

    private fun arrayAtCaret(): ArrayCreationExpression =
        PsiTreeUtil.getParentOfType(elementAtCaret(), ArrayCreationExpression::class.java)!!

    private fun configureRoutes(body: String) {
        myFixture.configureByFilePathAndText("cake5/config/routes.php", """
        <?php
        use Cake\Routing\RouteBuilder;
        use Cake\Routing\Router;
        return static function (RouteBuilder ${'$'}routes): void {
        $body
        };
        """.trimIndent())
    }

    fun `test array keys are parsed and non-literals and unknown keys are left out`() {
        configureRoutes("""
            ${'$'}routes->connect('/x', ['plugin' => 'Probe', 'prefix' => 'Admin', 'controller' => 'Reports', 'action' => 'in<caret>dex', '_name' => 'x', 'extra' => ${'$'}var]);
        """)
        assertEquals(
            RouteTarget(RouteKey.Named("Probe"), RouteKey.Named("Admin"), "Reports", "index"),
            routeTargetFromArray(arrayAtCaret())
        )
    }

    fun `test null and false plugin and prefix keys are told apart`() {
        configureRoutes("""
            ${'$'}routes->connect('/x', ['plugin' => null, 'prefix' => false, 'controller' => 'Re<caret>ports']);
        """)
        assertEquals(
            RouteTarget(RouteKey.None, RouteKey.False, "Reports", null),
            routeTargetFromArray(arrayAtCaret())
        )
    }

    fun `test string targets are parsed`() {
        assertEquals(RouteTarget(controller = "Reports", action = "index"), routeTargetFromString("Reports::index"))
        assertEquals(
            RouteTarget(RouteKey.Named("Acme/Vendored"), RouteKey.Named("Admin/Reports"), "Reports", "view_all"),
            routeTargetFromString("Acme/Vendored.Admin/Reports/Reports::view_all")
        )
        assertNull(routeTargetFromString("Reports"))
        assertNull(routeTargetFromString("/reports/index"))
    }

    fun `test nested prefix and plugin closures give the scope`() {
        configureRoutes("""
            ${'$'}routes->plugin('Probe', static function (RouteBuilder ${'$'}builder): void {
                ${'$'}builder->prefix('Admin', ['path' => '/manage'], static function (RouteBuilder ${'$'}builder): void {
                    ${'$'}builder->prefix('Reports', function (${'$'}builder) {
                        ${'$'}builder->connect('/x', ['controller' => 'Re<caret>ports']);
                    });
                });
            });
        """)
        assertEquals(RouteScope("Probe", "Admin/Reports"), routeScopeOf(elementAtCaret()))
    }

    fun `test scope params and the static Router form give the scope`() {
        configureRoutes("""
            Router::scope('/manage', ['prefix' => 'Admin', 'plugin' => 'Probe'], function (${'$'}routes) {
                ${'$'}routes->connect('/x', ['controller' => 'Re<caret>ports']);
            });
        """)
        assertEquals(RouteScope("Probe", "Admin"), routeScopeOf(elementAtCaret()))
    }

    fun `test closures of other calls do not give a scope`() {
        configureRoutes("""
            ${'$'}routes->scope('/', function (${'$'}routes) {
                array_map(function (${'$'}x) use (${'$'}routes) {
                    ${'$'}routes->connect('/x', ['controller' => 'Re<caret>ports']);
                }, []);
            });
        """)
        assertEquals(RouteScope(), routeScopeOf(elementAtCaret()))
    }

    fun `test scope fills in what the target leaves out and an explicit none wins`() {
        val scope = RouteScope("Probe", "Admin")
        assertEquals(
            ResolvedRouteTarget(RouteValue.Known("Probe"), RouteValue.Known("Admin"), "Reports", "index"),
            resolveInScope(settings, RouteTarget(controller = "Reports", action = "index"), scope)
        )
        assertEquals(
            ResolvedRouteTarget(RouteValue.Known(null), RouteValue.Known("Admin/Reports"), null, null),
            resolveInScope(settings, RouteTarget(plugin = RouteKey.None, prefix = RouteKey.Named("Admin/Reports")), scope)
        )
    }

    fun `test a false plugin or prefix key is rejected by the route builder`() {
        val scope = RouteScope("Probe", "Admin")
        assertNull(resolveInScope(settings, RouteTarget(plugin = RouteKey.False, controller = "Reports"), scope))
        assertNull(resolveInScope(settings, RouteTarget(prefix = RouteKey.False, controller = "Reports"), scope))
    }

    fun `test a false plugin or prefix key in a url array is an explicit none`() {
        val candidate = RouteContextController("Probe", "Admin", "Reports", "index")
        assertEquals(
            listOf(ResolvedRouteTarget(RouteValue.Known(null), RouteValue.Known(null), "Reports", "index")),
            resolveInContext(RouteTarget(plugin = RouteKey.False, prefix = RouteKey.False, controller = "Reports"), listOf(candidate))
        )
    }

    fun `test context inside a controller action is that controller and action`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/Admin/ReportsController.php", """
        <?php
        namespace App\Controller\Admin;
        use App\Controller\AppController;
        class ReportsController extends AppController
        {
            public function index()
            {
                return ${'$'}this->redirect(['action' => 'mon<caret>thly']);
            }
        }
        """.trimIndent())
        assertEquals(
            listOf(RouteContextController(null, "Admin", "Reports", "index")),
            routeContextOf(settings, elementAtCaret())
        )
    }

    fun `test context inside a plugin controller carries the plugin`() {
        myFixture.configureByFilePathAndText("cake5/vendor/test/test_plugin/src/Controller/WidgetsController.php", """
        <?php
        namespace TestPlugin\Controller;
        use Cake\Controller\Controller;
        class WidgetsController extends Controller
        {
            public function index()
            {
                ${'$'}url = ['action' => 'con<caret>figure'];
            }
        }
        """.trimIndent())
        assertEquals(
            listOf(RouteContextController("TestPlugin", null, "Widgets", "index")),
            routeContextOf(settings, elementAtCaret())
        )
    }

    fun `test context in a view file is its rendering controllers with an unknown action`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->Html->link('x', ['action' => 'me<caret>ta']);
        """.trimIndent())
        assertEquals(
            listOf(RouteContextController(null, null, "Movie", null)),
            routeContextOf(settings, elementAtCaret())
        )
    }

    fun `test context outside a controller or view is empty`() {
        configureRoutes("""
            ${'$'}url = ['action' => 'in<caret>dex'];
        """)
        assertEquals(emptyList<RouteContextController>(), routeContextOf(settings, elementAtCaret()))
    }

    fun `test candidates fill in the controller and copy their action for the same controller`() {
        val candidate = RouteContextController("Probe", "Admin", "Reports", "index")
        assertEquals(
            listOf(ResolvedRouteTarget(RouteValue.Known("Probe"), RouteValue.Known("Admin"), "Reports", "index")),
            resolveInContext(RouteTarget(), listOf(candidate))
        )
        assertEquals(
            listOf(ResolvedRouteTarget(RouteValue.Known(null), RouteValue.Known("Admin"), "Reports", "index")),
            resolveInContext(RouteTarget(plugin = RouteKey.None, controller = "Reports"), listOf(candidate))
        )
        assertEquals(
            listOf(ResolvedRouteTarget(RouteValue.Known("Probe"), RouteValue.Known("Admin"), "Other", "index")),
            resolveInContext(RouteTarget(controller = "Other"), listOf(candidate))
        )
    }

    fun `test an unknown candidate action stays unknown`() {
        val candidate = RouteContextController(null, null, "Movie", null)
        assertEquals(
            listOf(ResolvedRouteTarget(RouteValue.Known(null), RouteValue.Known(null), "Movie", null)),
            resolveInContext(RouteTarget(), listOf(candidate))
        )
    }

    fun `test no candidates leave the plugin and prefix unknown`() {
        assertEquals(
            listOf(ResolvedRouteTarget(RouteValue.Unknown, RouteValue.Unknown, "Reports", null)),
            resolveInContext(RouteTarget(controller = "Reports"), emptyList())
        )
        assertEquals(
            listOf(ResolvedRouteTarget(RouteValue.Known("Probe"), RouteValue.Unknown, null, null)),
            resolveInContext(RouteTarget(plugin = RouteKey.Named("Probe")), emptyList())
        )
    }

    fun `test controller subclasses leave out the app controller`() {
        val names = phpIndex.getControllerSubclasses(settings).map { it.fqn }.toSet()
        assertContainsElements(
            names,
            "\\App\\Controller\\MovieController",
            "\\App\\Controller\\ArticleController",
            "\\App\\Controller\\Admin\\ReportsController",
            "\\TestPlugin\\Controller\\WidgetsController",
        )
        assertDoesntContain(names, "\\App\\Controller\\AppController", "\\Cake\\Controller\\Controller")
    }

    fun `test controller classes are named as a route target names them`() {
        fun nameOf(fqn: String) = routeControllerNameFromClass(settings, phpIndex.getClassesByFQN(fqn).first())
        assertEquals(RouteControllerName(null, null, "Movie"), nameOf("\\App\\Controller\\MovieController"))
        assertEquals(RouteControllerName(null, "Admin", "Reports"), nameOf("\\App\\Controller\\Admin\\ReportsController"))
        assertEquals(RouteControllerName("TestPlugin", null, "Widgets"), nameOf("\\TestPlugin\\Controller\\WidgetsController"))
    }

    fun `test a fully known target names one class`() {
        assertEquals(
            "\\App\\Controller\\Admin\\Reports\\MonthlyController",
            controllerClassFqn(settings, ResolvedRouteTarget(RouteValue.Known(null), RouteValue.Known("Admin/Reports"), "Monthly", null))
        )
        assertEquals(
            "\\TestPlugin\\Controller\\WidgetsController",
            controllerClassFqn(settings, ResolvedRouteTarget(RouteValue.Known("TestPlugin"), RouteValue.Known(null), "Widgets", null))
        )
        assertEquals(
            "\\Acme\\Vendored\\Controller\\Admin\\Reports\\ThingsController",
            controllerClassFqn(settings, ResolvedRouteTarget(RouteValue.Known("Acme/Vendored"), RouteValue.Known("Admin/Reports"), "Things", null))
        )
        // CakePHP 5 reads the prefix as written, so a lower case one names a class that does not exist
        assertEquals(
            "\\App\\Controller\\admin\\ReportsController",
            controllerClassFqn(settings, ResolvedRouteTarget(RouteValue.Known(null), RouteValue.Known("admin"), "Reports", null))
        )
        assertNull(controllerClassFqn(settings, ResolvedRouteTarget(RouteValue.Unknown, RouteValue.Known(null), "Movie", null)))
    }

    fun `test a target with unknown parts matches controllers by name across them`() {
        val fqns = routeTargetControllers(
            phpIndex, settings, ResolvedRouteTarget(RouteValue.Unknown, RouteValue.Unknown, "Widgets", null)
        ).map { it.fqn }
        assertEquals(listOf("\\TestPlugin\\Controller\\WidgetsController"), fqns)

        val known = routeTargetControllers(
            phpIndex, settings, ResolvedRouteTarget(RouteValue.Known(null), RouteValue.Known("Admin"), "Reports", null)
        ).map { it.fqn }
        assertEquals(listOf("\\App\\Controller\\Admin\\ReportsController"), known)

        val lowerCase = routeTargetControllers(
            phpIndex, settings, ResolvedRouteTarget(RouteValue.Known(null), RouteValue.Known("admin"), "Reports", null)
        )
        assertEmpty(lowerCase)

        val none = routeTargetControllers(
            phpIndex, settings, ResolvedRouteTarget(RouteValue.Known(null), RouteValue.Known(null), "Widgets", null)
        )
        assertEmpty(none)
    }

    fun `test actions are the public methods the framework base does not declare`() {
        val movie = phpIndex.getClassesByFQN("\\App\\Controller\\MovieController").first()
        val actions = routeActions(phpIndex, movie).map { it.name }
        assertContainsElements(actions, "artist", "filmDirector", "meta")
        assertDoesntContain(actions, "initialize", "beforeFilter", "redirect", "render", "set", "fetchTable")
    }

    fun `test a lower case controller key names no class`() {
        val found = routeTargetControllers(
            phpIndex, settings, ResolvedRouteTarget(RouteValue.Known(null), RouteValue.Known(null), "movie", null)
        )
        assertEmpty(found)
        val unknown = routeTargetControllers(
            phpIndex, settings, ResolvedRouteTarget(RouteValue.Unknown, RouteValue.Unknown, "widgets", null)
        )
        assertEmpty(unknown)
    }
}
