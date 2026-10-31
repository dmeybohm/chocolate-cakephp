package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.daveme.chocolateCakePHP.view.ViewHelperGotoDeclarationHandler
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpClass

class ControllerSideHelperTest : Cake5BaseTestCase() {

    private val movieHelper = "\\App\\View\\Helper\\MovieFormatterHelper"
    private val artistHelper = "\\App\\View\\Helper\\ArtistFormatterHelper"
    private val conventionHelper = "\\App\\View\\Helper\\FmtHelper"

    override fun setUpTestFiles() {
        configureByFiles(
            "cake5/src5/View/Helper/MovieFormatterHelper.php",
            "cake5/src5/View/Helper/ArtistFormatterHelper.php",
            "cake5/vendor/cakephp.php",
        )
        // A file's element references are only indexed once the element directory exists:
        myFixture.addFileToProject("cake5/templates/element/placeholder.php", "<?php echo 1;")
        myFixture.addFileToProject("cake5/plugins/Blog/templates/element/placeholder.php", "<?php echo 1;")
    }

    private fun appView(initialize: String = "") {
        myFixture.addFileToProject("cake5/src5/View/AppView.php", """
            <?php
            namespace App\View;
            class AppView extends \Cake\View\View {
                public function initialize(): void { $initialize }
            }
        """.trimIndent())
    }

    private fun controller(name: String, body: String, parent: String = "\\Cake\\Controller\\Controller") {
        myFixture.addFileToProject("cake5/src5/Controller/${name}Controller.php", """
            <?php
            namespace App\Controller;
            class ${name}Controller extends $parent {
                $body
            }
        """.trimIndent())
    }

    private fun pluginController(name: String, body: String) {
        myFixture.addFileToProject("cake5/plugins/Blog/src/Controller/${name}Controller.php", """
            <?php
            namespace Blog\Controller;
            class ${name}Controller extends \Cake\Controller\Controller {
                $body
            }
        """.trimIndent())
    }

    private fun fmtIn(helperClass: String, action: String = "index") =
        "public function $action() { \$this->viewBuilder()->addHelper('Fmt', ['className' => '$helperClass']); }"

    private fun fmtInInitialize(helperClass: String) =
        "public function initialize(): void { \$this->viewBuilder()->addHelper('Fmt', ['className' => '$helperClass']); }"

    private fun template(path: String, code: String = "echo 1;") =
        myFixture.addFileToProject("cake5/templates/$path.php", "<?php $code")

    private fun configureWithCaret(path: String, text: String) {
        myFixture.addFileToProject(path, text.replace("<caret>", ""))
        myFixture.configureFromTempProjectFile(path)
        myFixture.editor.caretModel.moveToOffset(text.indexOf("<caret>"))
    }

    private fun fmtTypesIn(path: String, templates: String = "cake5/templates", field: String = "Fmt"): Set<String> {
        val code = "<?php\n\$this->$field;\n"
        myFixture.addFileToProject("$templates/$path.php", code)
        myFixture.configureFromTempProjectFile("$templates/$path.php")
        myFixture.editor.caretModel.moveToOffset(code.indexOf(field) + 1)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    fun `test a helper added in an action resolves in its controller's template`() {
        appView()
        controller("Movie", fmtIn("MovieFormatter"))
        val types = fmtTypesIn("Movie/index")
        assertTrue("$types", types.contains(movieHelper))
        assertFalse("$types", types.contains(artistHelper))
    }

    fun `test a helper added in an action keeps its convention type`() {
        appView()
        controller("Movie", fmtIn("MovieFormatter"))
        val types = fmtTypesIn("Movie/index")
        assertTrue("$types", types.containsAll(listOf(movieHelper, conventionHelper)))
    }

    fun `test a helper added in initialize determines the type`() {
        appView()
        controller("Movie", fmtInInitialize("MovieFormatter") + "\npublic function index() {}")
        assertEquals(setOf(movieHelper), fmtTypesIn("Movie/index"))
    }

    fun `test the array forms and a chained call resolve`() {
        appView()
        controller("Movie", """
            public function index() {
                ${'$'}this->viewBuilder()->setLayout('x')->addHelpers(['Fmt' => ['className' => 'MovieFormatter']]);
            }
            public function other() {
                ${'$'}this->viewBuilder()->setHelpers(['Fmt' => ['className' => 'ArtistFormatter']]);
            }
        """)
        val types = fmtTypesIn("Movie/index")
        assertTrue("$types", types.containsAll(listOf(movieHelper, artistHelper, conventionHelper)))
    }

    fun `test an element only one template includes leaves out other controllers`() {
        appView()
        controller("Movie", fmtIn("MovieFormatter"))
        controller("Artist", fmtIn("ArtistFormatter"))
        template("Movie/index", "echo \$this->element('movie_only');")
        val types = fmtTypesIn("element/movie_only")
        assertTrue("$types", types.contains(movieHelper))
        assertFalse("$types", types.contains(artistHelper))
    }

    fun `test a layout gets the union of all controllers`() {
        appView()
        controller("Movie", fmtIn("MovieFormatter"))
        controller("Artist", fmtIn("ArtistFormatter"))
        val types = fmtTypesIn("layout/default")
        assertTrue("$types", types.containsAll(listOf(movieHelper, artistHelper)))
    }

    fun `test an element in a layout gets the union of all controllers`() {
        appView()
        controller("Movie", fmtIn("MovieFormatter"))
        controller("Artist", fmtIn("ArtistFormatter"))
        template("layout/default", "echo \$this->element('nav');")
        val types = fmtTypesIn("element/nav")
        assertTrue("$types", types.containsAll(listOf(movieHelper, artistHelper)))
    }

    fun `test a plugin controller bypassing AppController counts in a layout`() {
        appView()
        controller("App", "")
        pluginController("Posts", fmtIn("ArtistFormatter"))
        assertTrue(fmtTypesIn("layout/default").contains(artistHelper))
    }

    fun `test controllers that only reach the framework through AppController count in a layout`() {
        appView()
        controller("App", "")
        controller("Movie", fmtIn("MovieFormatter"), parent = "AppController")
        assertTrue(fmtTypesIn("layout/default").contains(movieHelper))
    }

    fun `test a cell template inherits the helpers of the view calling it`() {
        appView()
        controller("Movie", fmtIn("MovieFormatter"))
        controller("Artist", fmtIn("ArtistFormatter") + "\npublic function other() {}")
        template("Movie/index", "echo \$this->cell('Widget');")
        val types = fmtTypesIn("cell/Widget/display")
        assertTrue("$types", types.contains(movieHelper))
        assertFalse("$types", types.contains(artistHelper))
    }

    fun `test an element in a cell template inherits the helpers of the view calling the cell`() {
        appView()
        controller("Movie", fmtIn("MovieFormatter"))
        template("Movie/index", "echo \$this->cell('Widget');")
        template("cell/Widget/display", "echo \$this->element('in_cell');")
        assertTrue(fmtTypesIn("element/in_cell").contains(movieHelper))
    }

    fun `test a view class load wins over the controller's`() {
        appView("\$this->loadHelper('Fmt', ['className' => 'ArtistFormatter']);")
        controller("Movie", fmtInInitialize("MovieFormatter") + "\npublic function index() {}")
        assertEquals(setOf(artistHelper), fmtTypesIn("Movie/index"))
    }

    fun `test a view class helper resolves whatever renders the template`() {
        appView("\$this->loadHelper('Fmt', ['className' => 'ArtistFormatter']);")
        controller("Movie", "public function index() {}")
        assertEquals(setOf(artistHelper), fmtTypesIn("Movie/index"))
        assertEquals(setOf(artistHelper), fmtTypesIn("Unrendered/index"))
        assertEquals(setOf(artistHelper), fmtTypesIn("layout/default"))
    }

    fun `test goto declaration finds the controller's helper`() {
        appView()
        controller("Movie", fmtInInitialize("MovieFormatter") + "\npublic function index() {}")
        fmtTypesIn("Movie/index")
        val targets = gotoDeclarationHandlerTargets(ViewHelperGotoDeclarationHandler())
        assertEquals(listOf(movieHelper), targets!!.map { (it as PhpClass).fqn })
    }

    fun `test a template completes the helper its controller adds`() {
        appView()
        controller("Movie", fmtIn("MovieFormatter"))
        configureWithCaret("cake5/templates/Movie/index.php", "<?php\n\$this-><caret>\n")
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue("$result", result!!.contains("Fmt"))
    }

    fun `test a non-literal class name resolves by convention without determining the type`() {
        myFixture.addFileToProject("cake5/src5/View/AppView.php", """
            <?php
            namespace App\View;
            class AppView extends \Cake\View\View {
                public function initialize(): void {
                    ${'$'}this->addHelper('MovieFormatter', ['className' => ${'$'}this->x]);
                }
            }
        """.trimIndent())
        val result = ConfiguredMemberService.memberType(
            project, listOf("\\App\\View\\AppView"), setOf(MemberKind.HELPER), "MovieFormatter"
        )
        assertNotNull(result)
        assertEquals(setOf(movieHelper), result!!.classFqns)
        assertFalse(result.hasInitializeLoad)
    }

    fun `test an incomplete helpers array keeps convention completion in a helper`() {
        configureWithCaret("cake5/src5/View/Helper/IncompleteHelper.php", """
            <?php
            namespace App\View\Helper;
            class IncompleteHelper extends \Cake\View\Helper {
                const X = 'Foo';
                protected array ${'$'}helpers = ['ArtistFormatter', self::X];
                public function probe() { return ${'$'}this-><caret>; }
            }
        """.trimIndent())
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue("$result", result!!.contains("MovieFormatter"))
    }

    fun `test an incomplete helpers array does not determine its loads`() {
        myFixture.addFileToProject("cake5/src5/View/Helper/IncompleteHelper.php", """
            <?php
            namespace App\View\Helper;
            class IncompleteHelper extends \Cake\View\Helper {
                const X = 'Foo';
                protected array ${'$'}helpers = ['ArtistFormatter', self::X];
            }
        """.trimIndent())
        val result = ConfiguredMemberService.memberType(
            project, listOf("\\App\\View\\Helper\\IncompleteHelper"), setOf(MemberKind.HELPER), "ArtistFormatter"
        )
        assertNotNull(result)
        assertFalse(result!!.hasInitializeLoad)
    }

    fun `test helper methods off the view builder are not read as loads`() {
        appView()
        controller("Movie", """
            public function index() {
                ${'$'}this->setHelpers(['Fmt' => ['className' => 'MovieFormatter']]);
                ${'$'}this->other()->addHelper('Fmt', ['className' => 'ArtistFormatter']);
            }
        """)
        val types = fmtTypesIn("Movie/index")
        assertFalse("$types", types.contains(movieHelper))
        assertFalse("$types", types.contains(artistHelper))
    }

    fun `test an element in a plugin's cell reaches the app controller rendering the caller`() {
        val blogTemplates = "cake5/plugins/Blog/templates"
        appView()
        controller("Movie", fmtIn("MovieFormatter"))
        template("Movie/index", "echo \$this->cell('Blog.Widget');")
        myFixture.addFileToProject("$blogTemplates/cell/Widget/display.php", "<?php echo \$this->element('in_cell');")
        assertTrue(fmtTypesIn("element/in_cell", blogTemplates).contains(movieHelper))
    }

    private fun cell(body: String) {
        myFixture.addFileToProject("cake5/src5/View/Cell/WidgetCell.php", """
            <?php
            namespace App\View\Cell;
            class WidgetCell extends \Cake\View\Cell {
                $body
            }
        """.trimIndent())
    }

    private fun completionsIn(path: String): List<String> {
        configureWithCaret("cake5/templates/$path.php", "<?php\n\$this-><caret>\n")
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    fun `test a helper a cell action adds resolves in the cell's template`() {
        appView()
        cell("public function display() { \$this->viewBuilder()->addHelper('Fmt', ['className' => 'MovieFormatter']); }")
        val types = fmtTypesIn("cell/Widget/display")
        assertTrue("$types", types.containsAll(listOf(movieHelper, conventionHelper)))
    }

    fun `test a helper a cell's initialize adds settles the type in its template`() {
        appView()
        controller("Movie", "public function index() {}")
        template("Movie/index", "echo \$this->cell('Widget');")
        cell("""
            public function initialize(): void { ${'$'}this->viewBuilder()->addHelper('Fmt', ['className' => 'MovieFormatter']); }
            public function display() {}
        """)
        assertEquals(setOf(movieHelper), fmtTypesIn("cell/Widget/display"))
    }

    fun `test a helper a cell's initialize adds does not settle the type in a shared element`() {
        appView()
        controller("Movie", "public function index() {}")
        template("Movie/index", "echo \$this->element('shared');")
        template("cell/Widget/display", "echo \$this->element('shared');")
        cell("""
            public function initialize(): void { ${'$'}this->viewBuilder()->addHelper('Fmt', ['className' => 'MovieFormatter']); }
            public function display() {}
        """)
        val types = fmtTypesIn("element/shared")
        assertTrue("$types", types.containsAll(listOf(movieHelper, conventionHelper)))
    }

    fun `test a cell's helper stays out of other templates`() {
        appView()
        controller("Movie", "public function index() {}")
        cell("public function display() { \$this->viewBuilder()->addHelper('Fmt', ['className' => 'MovieFormatter']); }")
        assertFalse(fmtTypesIn("Movie/index").contains(movieHelper))
    }

    fun `test a helpers property on the view class is ignored`() {
        myFixture.addFileToProject("cake5/src5/View/AppView.php", """
            <?php
            namespace App\View;
            class AppView extends \Cake\View\View {
                protected array ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];
            }
        """.trimIndent())
        controller("Movie", "public function index() {}")
        assertFalse(fmtTypesIn("Movie/index").contains(movieHelper))
        assertFalse(completionsIn("Movie/other").contains("Fmt"))
    }

    fun `test a helpers property on a controller is ignored`() {
        appView()
        controller("Movie", """
            public ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];
            public function index() {}
            public function other() {}
        """)
        assertFalse(fmtTypesIn("Movie/index").contains(movieHelper))
        assertFalse(completionsIn("Movie/other").contains("Fmt"))
    }

    fun `test a helpers property on a cell is ignored`() {
        appView()
        cell("""
            public ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];
            public function display() {}
        """)
        assertFalse(fmtTypesIn("cell/Widget/display").contains(movieHelper))
    }

    fun `test a controller's alias of an existing helper's name settles the type`() {
        appView()
        controller("Movie", """
            public function initialize(): void {
                ${'$'}this->viewBuilder()->addHelper('ArtistFormatter', ['className' => 'MovieFormatter']);
            }
            public function index() {}
        """)
        assertEquals(setOf(movieHelper), fmtTypesIn("Movie/index", field = "ArtistFormatter"))
    }

    fun `test a helper only an action adds has no magic get type`() {
        appView()
        controller("Movie", fmtIn("MovieFormatter"))
        val types = fmtTypesIn("Movie/index")
        assertFalse("$types", types.contains("\\null"))
        assertFalse("$types", types.contains("\\Cake\\View\\Helper"))
    }

    fun `test a helper nothing configures keeps its convention type`() {
        appView()
        controller("Movie", "public function index() {}")
        assertTrue(fmtTypesIn("Movie/index", field = "ArtistFormatter").contains(artistHelper))
    }

    private val addFmtToArtistInInitialize =
        "public function initialize(): void { \$this->viewBuilder()->addHelper('Fmt', ['className' => 'ArtistFormatter']); }"

    private fun calledFromMovieIndex() = template("Movie/index", "echo \$this->cell('Widget');")

    fun `test a caller's helper wins over the helper of a cell's initialize`() {
        appView()
        controller("Movie", fmtInInitialize("MovieFormatter") + "\npublic function index() {}")
        calledFromMovieIndex()
        cell(addFmtToArtistInInitialize + "\npublic function display() {}")
        assertEquals(setOf(movieHelper), fmtTypesIn("cell/Widget/display"))
    }

    fun `test a cell's initialize helper stays when a caller does not configure the alias`() {
        appView()
        controller("Movie", fmtInInitialize("MovieFormatter") + "\npublic function index() {}")
        controller("Other", "public function index() {}")
        calledFromMovieIndex()
        template("Other/index", "echo \$this->cell('Widget');")
        cell(addFmtToArtistInInitialize + "\npublic function display() {}")
        assertEquals(setOf(movieHelper, artistHelper), fmtTypesIn("cell/Widget/display"))
    }

    fun `test a cell's initialize helper stays when a caller only adds the alias in an action`() {
        appView()
        controller("Movie", fmtIn("MovieFormatter"))
        calledFromMovieIndex()
        cell(addFmtToArtistInInitialize + "\npublic function display() {}")
        assertEquals(setOf(movieHelper, artistHelper), fmtTypesIn("cell/Widget/display"))
    }

    fun `test a cell action's helper stays alongside a caller's`() {
        appView()
        controller("Movie", fmtInInitialize("MovieFormatter") + "\npublic function index() {}")
        calledFromMovieIndex()
        cell("public function display() { \$this->viewBuilder()->addHelper('Fmt', ['className' => 'ArtistFormatter']); }")
        assertEquals(setOf(movieHelper, artistHelper), fmtTypesIn("cell/Widget/display"))
    }

    fun `test a caller's helper settles the type in a cell that does not configure it`() {
        appView()
        controller("Movie", fmtInInitialize("MovieFormatter") + "\npublic function index() {}")
        calledFromMovieIndex()
        cell("public function display() {}")
        assertEquals(setOf(movieHelper), fmtTypesIn("cell/Widget/display"))
    }

    fun `test an element in a cell template keeps the union`() {
        appView()
        controller("Movie", fmtInInitialize("MovieFormatter") + "\npublic function index() {}")
        calledFromMovieIndex()
        template("cell/Widget/display", "echo \$this->element('in_cell');")
        cell(addFmtToArtistInInitialize + "\npublic function display() {}")
        assertTrue(fmtTypesIn("element/in_cell").containsAll(listOf(movieHelper, artistHelper)))
    }
}
