package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberTypeResult

class ConfiguredMemberResolutionTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles("cake2/vendor/cakephp.php")
    }

    private fun addClasses(code: String) {
        myFixture.addFileToProject("app/Classes${code.hashCode()}.php", "<?php\n" + code.trimIndent())
    }

    private fun appController(body: String = "") =
        addClasses("class AppController extends Controller {\n$body\n}")

    private fun memberType(receiver: String, kind: MemberKind, alias: String): MemberTypeResult? =
        ConfiguredMemberService.memberType(project, listOf(receiver), setOf(kind), alias)

    private fun assertResolves(expected: String, result: MemberTypeResult?, authoritative: Boolean = true) {
        assertNotNull(result)
        assertEquals(setOf(expected), result!!.classFqns)
        assertEquals(authoritative, result.hasInitializeLoad)
    }

    private fun assertRuledOut(result: MemberTypeResult?) {
        assertNotNull("Expected the alias to be ruled out", result)
        assertEquals(emptySet<String>(), result!!.classFqns)
        assertTrue(result.hasInitializeLoad)
    }

    private fun loadedAliases(receiver: String, kind: MemberKind): Set<String> =
        ConfiguredMemberService.loadedMembers(project, listOf(receiver), setOf(kind)).members.keys

    fun `test aliased component resolves to its className only`() {
        appController()
        addClasses("""
            class MoviesController extends AppController {
                public ${'$'}components = array('Meta' => array('className' => 'MovieMetadata'), 'DebugKit.Toolbar');
            }
        """)
        assertResolves("\\MovieMetadataComponent", memberType("\\MoviesController", MemberKind.COMPONENT, "Meta"))
        assertResolves("\\ToolbarComponent", memberType("\\MoviesController", MemberKind.COMPONENT, "Toolbar"))
    }

    fun `test components merge with AppController and the child entry inherits its className`() {
        appController("public \$components = array('Auth' => array('className' => 'MyAuth'), 'Cookie');")
        addClasses("""
            class MoviesController extends AppController {
                public ${'$'}components = array('Auth' => array('authorize' => 'Controller'), 'Paginator');
            }
        """)
        assertResolves("\\MyAuthComponent", memberType("\\MoviesController", MemberKind.COMPONENT, "Auth"))
        assertEquals(
            setOf("Auth", "Cookie", "Paginator"),
            loadedAliases("\\MoviesController", MemberKind.COMPONENT)
        )
    }

    fun `test an unloaded component is ruled out when components are known`() {
        appController()
        addClasses("class MoviesController extends AppController { public \$components = array('Paginator'); }")
        assertRuledOut(memberType("\\MoviesController", MemberKind.COMPONENT, "Unloaded"))
    }

    fun `test an intermediate controller's components are lost when the child redeclares them`() {
        appController("public \$components = array('Cookie');")
        addClasses("""
            class AdminController extends AppController {
                public ${'$'}components = array('Acl');
            }
            class UsersController extends AdminController {
                public ${'$'}components = array();
            }
        """)
        assertEquals(setOf("Cookie"), loadedAliases("\\UsersController", MemberKind.COMPONENT))
        assertEquals(setOf("Acl", "Cookie"), loadedAliases("\\AdminController", MemberKind.COMPONENT))
    }

    fun `test the Controller default components apply when AppController does not declare any`() {
        appController()
        addClasses("""
            class AdminController extends AppController {
                public ${'$'}components = array('Acl');
            }
            class UsersController extends AdminController {
                public ${'$'}components = array();
            }
        """)
        assertEquals(setOf("Session", "Flash"), loadedAliases("\\UsersController", MemberKind.COMPONENT))
    }

    fun `test an incomplete components declaration keeps convention`() {
        appController()
        addClasses("class MoviesController extends AppController { public \$components = array(self::AUTH, 'Paginator'); }")
        assertResolves(
            "\\PaginatorComponent",
            memberType("\\MoviesController", MemberKind.COMPONENT, "Paginator"),
            authoritative = false
        )
        assertNull(memberType("\\MoviesController", MemberKind.COMPONENT, "Unloaded"))
        val loaded = ConfiguredMemberService.loadedMembers(project, listOf("\\MoviesController"), setOf(MemberKind.COMPONENT))
        assertFalse(MemberKind.COMPONENT in loaded.initializeKinds)
    }

    fun `test an unreadable className keeps convention for that alias`() {
        appController()
        addClasses("""
            class MoviesController extends AppController {
                public ${'$'}helpers = array('Html' => array('className' => SOME_CONST), 'Form');
            }
        """)
        assertResolves("\\HtmlHelper", memberType("\\MoviesController", MemberKind.HELPER, "Html"), authoritative = false)
        assertResolves("\\FormHelper", memberType("\\MoviesController", MemberKind.HELPER, "Form"))
    }

    fun `test uses lists the models a controller has`() {
        appController()
        addClasses("class MoviesController extends AppController { public \$uses = array('Artist'); }")
        assertResolves("\\Artist", memberType("\\MoviesController", MemberKind.TABLE, "Artist"))
        assertRuledOut(memberType("\\MoviesController", MemberKind.TABLE, "Movie"))
    }

    fun `test a controller without uses has its default model`() {
        appController()
        addClasses("class MoviesController extends AppController {}")
        assertEquals(setOf("Movie"), loadedAliases("\\MoviesController", MemberKind.TABLE))
    }

    fun `test uses false has no models`() {
        appController("public \$uses = array('User');")
        addClasses("class MoviesController extends AppController { public \$uses = false; }")
        assertEquals(emptySet<String>(), loadedAliases("\\MoviesController", MemberKind.TABLE))
        assertRuledOut(memberType("\\MoviesController", MemberKind.TABLE, "Movie"))
    }

    fun `test AppController uses are appended`() {
        appController("public \$uses = array('User');")
        addClasses("class MoviesController extends AppController { public \$uses = array('Artist'); }")
        assertEquals(setOf("Artist", "User"), loadedAliases("\\MoviesController", MemberKind.TABLE))
    }

    fun `test inherited AppController uses get the default model prepended`() {
        appController("public \$uses = array('User');")
        addClasses("class MoviesController extends AppController {}")
        assertEquals(setOf("Movie", "User"), loadedAliases("\\MoviesController", MemberKind.TABLE))
    }

    fun `test empty uses takes AppController's models only`() {
        appController("public \$uses = array('User');")
        addClasses("class MoviesController extends AppController { public \$uses = array(); }")
        assertEquals(setOf("User"), loadedAliases("\\MoviesController", MemberKind.TABLE))
    }

    fun `test empty uses with no AppController uses has only the default model`() {
        appController()
        addClasses("class MoviesController extends AppController { public \$uses = array(); }")
        assertEquals(setOf("Movie"), loadedAliases("\\MoviesController", MemberKind.TABLE))
    }

    fun `test loadModel adds a model uses does not list`() {
        appController()
        addClasses("""
            class MoviesController extends AppController {
                public ${'$'}uses = array('Artist');
                public function index() {
                    ${'$'}this->loadModel('Movie');
                }
            }
        """)
        assertResolves("\\Movie", memberType("\\MoviesController", MemberKind.TABLE, "Movie"))
    }

    fun `test associations come from each property's nearest declaration`() {
        addClasses("""
            class AppModel extends Model {}
            class Movie extends AppModel {
                public ${'$'}belongsTo = array('Director' => array('className' => 'Person'));
                public ${'$'}hasMany = 'Review, Rating';
            }
            class Remake extends Movie {
                public ${'$'}belongsTo = array();
            }
        """)
        assertResolves("\\Person", memberType("\\Movie", MemberKind.ASSOCIATION, "Director"))
        assertEquals(setOf("Director", "Review", "Rating"), loadedAliases("\\Movie", MemberKind.ASSOCIATION))
        assertEquals(setOf("Review", "Rating"), loadedAliases("\\Remake", MemberKind.ASSOCIATION))

        val loaded = ConfiguredMemberService.loadedMembers(project, listOf("\\Movie"), setOf(MemberKind.ASSOCIATION))
        assertTrue(MemberKind.ASSOCIATION in loaded.initializeKinds)
    }

    fun `test an unlisted association is not ruled out`() {
        addClasses("""
            class AppModel extends Model {}
            class Movie extends AppModel {
                public ${'$'}hasMany = array('Review');
            }
        """)
        assertNull(memberType("\\Movie", MemberKind.ASSOCIATION, "Studio"))
    }

    fun `test in-model bindModel adds an association without overriding`() {
        addClasses("""
            class AppModel extends Model {}
            class Movie extends AppModel {
                public ${'$'}hasMany = array('Review');
                public function withCritics() {
                    ${'$'}this->bindModel(array('hasMany' => array('Critic' => array('className' => 'User'))));
                }
            }
        """)
        assertResolves("\\User", memberType("\\Movie", MemberKind.ASSOCIATION, "Critic"), authoritative = false)
        assertResolves("\\Review", memberType("\\Movie", MemberKind.ASSOCIATION, "Review"))
    }

    fun `test a helper resolves its own helpers`() {
        addClasses("""
            class AppHelper extends Helper {}
            class ShareHelper extends AppHelper {
                public ${'$'}helpers = array('Html' => array('className' => 'BootstrapHtml'));
            }
        """)
        assertResolves("\\BootstrapHtmlHelper", memberType("\\ShareHelper", MemberKind.HELPER, "Html"))
        assertRuledOut(memberType("\\ShareHelper", MemberKind.HELPER, "Form"))
    }

    fun `test the view is not a helper receiver`() {
        addClasses("""
            class AppView extends View {
                public ${'$'}helpers = array('Html' => array('className' => 'BootstrapHtml'));
            }
        """)
        assertNull(memberType("\\AppView", MemberKind.HELPER, "Html"))
    }
}
