package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.routing.RouteTargetCallKind
import com.daveme.chocolateCakePHP.routing.RouteTargetPosition
import com.daveme.chocolateCakePHP.routing.routeTargetPositionOf
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class RouteTargetPatternsTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Controller/MovieController.php",
            "cake5/templates/Movie/artist.php",
        )
    }

    private fun positionAtCaret(): RouteTargetPosition? {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val literal = PsiTreeUtil.getParentOfType(element, StringLiteralExpression::class.java, false)!!
        return routeTargetPositionOf(literal)
    }

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

    private fun configureController(body: String) {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
        <?php
        namespace App\Controller;
        use Cake\Controller\Controller;
        class MovieController extends Controller
        {
            public function artist()
            {
                $body
            }
        }
        """.trimIndent())
    }

    private fun configureTemplate(body: String) {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        $body
        """.trimIndent())
    }

    private fun assertArrayKey(key: String, kind: RouteTargetCallKind, method: String) {
        val position = positionAtCaret()
        assertTrue("Expected an array key position, got $position", position is RouteTargetPosition.ArrayKey)
        position as RouteTargetPosition.ArrayKey
        assertEquals(key, position.key)
        assertEquals(kind, position.kind)
        assertEquals(method, position.call.name)
    }

    fun `test connect on a typed route builder`() {
        configureRoutes("""${'$'}routes->connect('/x', ['controller' => 'Mov<caret>ie', 'action' => 'artist']);""")
        assertArrayKey("controller", RouteTargetCallKind.ROUTE_BUILDER, "connect")
    }

    fun `test verb methods and redirect on an untyped scope closure parameter`() {
        configureRoutes("""
            ${'$'}routes->prefix('Admin', function (${'$'}builder) {
                ${'$'}builder->get('/x', ['controller' => 'Movie', 'action' => 'art<caret>ist']);
            });
        """)
        assertArrayKey("action", RouteTargetCallKind.ROUTE_BUILDER, "get")

        configureRoutes("""
            ${'$'}routes->scope('/', function (${'$'}builder) {
                ${'$'}builder->redirect('/old', ['plugin' => 'Tes<caret>tPlugin', 'controller' => 'Widgets']);
            });
        """)
        assertArrayKey("plugin", RouteTargetCallKind.ROUTE_BUILDER, "redirect")
    }

    fun `test static Router connect and url`() {
        configureRoutes("""Router::connect('/x', ['prefix' => 'Ad<caret>min', 'controller' => 'Reports']);""")
        assertArrayKey("prefix", RouteTargetCallKind.ROUTE_BUILDER, "connect")

        configureRoutes("""${'$'}url = Router::url(['controller' => 'Mov<caret>ie', 'action' => 'artist']);""")
        assertArrayKey("controller", RouteTargetCallKind.URL_BUILDER, "url")
    }

    fun `test a closure of another call is not a route builder`() {
        configureRoutes("""
            array_map(function (${'$'}builder) {
                ${'$'}builder->connect('/x', ['controller' => 'Mov<caret>ie']);
            }, []);
        """)
        assertNull(positionAtCaret())
    }

    fun `test string targets of connect and resources`() {
        configureRoutes("""${'$'}routes->connect('/x', 'Movie::art<caret>ist');""")
        assertTrue(positionAtCaret() is RouteTargetPosition.StringTarget)

        configureRoutes("""${'$'}routes->resources('Mov<caret>ie');""")
        assertTrue(positionAtCaret() is RouteTargetPosition.Resources)

        configureRoutes("""${'$'}routes->connect('/mov<caret>ie', ['controller' => 'Movie']);""")
        assertNull(positionAtCaret())
    }

    fun `test helper calls in a template`() {
        configureTemplate("""echo ${'$'}this->Html->link('x', ['action' => 'art<caret>ist']);""")
        assertArrayKey("action", RouteTargetCallKind.URL_BUILDER, "link")

        configureTemplate("""echo ${'$'}this->Form->postLink('x', ['controller' => 'Mov<caret>ie']);""")
        assertArrayKey("controller", RouteTargetCallKind.URL_BUILDER, "postLink")

        configureTemplate("""echo ${'$'}this->Url->build(['controller' => 'Mov<caret>ie']);""")
        assertArrayKey("controller", RouteTargetCallKind.URL_BUILDER, "build")

        configureTemplate("""echo ${'$'}this->Form->create(${'$'}movie, ['url' => ['action' => 'ar<caret>tist']]);""")
        assertArrayKey("action", RouteTargetCallKind.URL_BUILDER, "create")
    }

    fun `test redirect in a controller is a url builder`() {
        configureController("""return ${'$'}this->redirect(['action' => 'me<caret>ta']);""")
        assertArrayKey("action", RouteTargetCallKind.URL_BUILDER, "redirect")
    }

    fun `test arrays of other calls and other keys are left alone`() {
        configureTemplate("""echo ${'$'}this->Form->button('x', ['action' => 'del<caret>ete']);""")
        assertNull(positionAtCaret())

        configureTemplate("""echo ${'$'}this->Html->link('x', ['_name' => 'ho<caret>me']);""")
        assertNull(positionAtCaret())

        configureTemplate("""echo ${'$'}this->Html->link('x', ['action' => 'artist'], ['class' => 'b<caret>tn']);""")
        assertNull(positionAtCaret())

        configureTemplate("""echo ${'$'}this->Form->create(${'$'}movie, ['action' => 'ar<caret>tist']);""")
        assertNull(positionAtCaret())
    }

    fun `test a key literal is not a position`() {
        configureTemplate("""echo ${'$'}this->Html->link('x', ['act<caret>ion' => 'artist']);""")
        assertNull(positionAtCaret())
    }
}
