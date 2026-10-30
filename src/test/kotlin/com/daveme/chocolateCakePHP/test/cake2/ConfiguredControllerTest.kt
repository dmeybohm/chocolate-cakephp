package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.controller.ControllerComponentsGotoDeclarationHandler
import com.daveme.chocolateCakePHP.controller.ControllerFieldGotoDeclarationHandler
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpClass

class ConfiguredControllerTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.addFileToProject("app/Classes.php", """
            <?php
            class AppComponent extends Component {}
            class MovieMetadataComponent extends AppComponent {
                public function generateMetadata() {}
            }
            class UnloadedComponent extends AppComponent {}
            class MyAuthComponent extends AppComponent {}
            class AppModel extends Model {}
            class Movie extends AppModel {}
            class Artist extends AppModel {}
            class User extends AppModel {}
        """.trimIndent())
    }

    private fun withFramework() {
        configureByFiles("cake2/vendor/cakephp.php")
    }

    private fun appController(body: String = "") {
        myFixture.addFileToProject("app/Controller/AppController.php", "<?php\nclass AppController extends Controller {\n$body\n}\n")
    }

    private fun configureController(body: String, action: String) {
        myFixture.configureByText("MoviesController.php", """
            <?php
            class MoviesController extends AppController {
                $body
                public function index() {
                    $action
                }
            }
        """.trimIndent())
    }

    private fun fieldTypeAtCaret(): Set<String> {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        // The framework's __get() return types are not part of the configured type:
        return projectClassTypes(fieldReference!!.type.lookupCompleteType(project, null).types)
    }

    private fun completions(): List<String> {
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        return result!!
    }

    private fun gotoClassNames(targets: Array<*>?): Set<String> =
        targets.orEmpty().filterIsInstance<PhpClass>().map { it.name }.toSet()

    fun `test an aliased component resolves to its className only`() {
        withFramework()
        appController()
        configureController(
            "public \$components = array('Meta' => array('className' => 'MovieMetadata'));",
            "\$this->Me<caret>ta;"
        )
        assertEquals(setOf("\\MovieMetadataComponent"), fieldTypeAtCaret())
    }

    fun `test a plain component resolves by name and not as a model`() {
        withFramework()
        appController()
        configureController("public \$components = array('MovieMetadata');", "\$this->MovieMeta<caret>data;")
        assertEquals(setOf("\\MovieMetadataComponent"), fieldTypeAtCaret())
    }

    fun `test an AppController alias resolves in the child`() {
        withFramework()
        appController("public \$components = array('Meta' => array('className' => 'MovieMetadata'));")
        configureController("public \$components = array('Paginator');", "\$this->Me<caret>ta;")
        assertEquals(setOf("\\MovieMetadataComponent"), fieldTypeAtCaret())
    }

    fun `test an unloaded component gets no type even when its class exists`() {
        withFramework()
        appController()
        configureController("public \$components = array('MovieMetadata');", "\$this->Unlo<caret>aded;")
        assertEquals(emptySet<String>(), fieldTypeAtCaret())
    }

    fun `test component completion lists only the loaded components`() {
        withFramework()
        appController()
        configureController("public \$components = array('MovieMetadata');", "\$this-><caret>")
        val result = completions()
        assertTrue("Expected MovieMetadata in $result", result.contains("MovieMetadata"))
        assertTrue(result.contains("Session"))
        assertFalse(result.contains("Unloaded"))
    }

    fun `test uses entries resolve and other models do not exist`() {
        withFramework()
        appController()
        configureController("public \$uses = array('Artist');", "\$this->Art<caret>ist;")
        assertEquals(setOf("\\Artist"), fieldTypeAtCaret())

        configureController("public \$uses = array('Artist');", "\$this->Mo<caret>vie;")
        assertEquals(emptySet<String>(), fieldTypeAtCaret())
    }

    fun `test model completion with uses hides other models`() {
        withFramework()
        appController()
        configureController("public \$uses = array('Artist');", "\$this-><caret>")
        val result = completions()
        assertTrue(result.contains("Artist"))
        assertFalse(result.contains("Movie"))
        assertFalse(result.contains("User"))
    }

    fun `test a controller without uses has its default model`() {
        withFramework()
        appController()
        configureController("", "\$this->Mo<caret>vie;")
        assertEquals(setOf("\\Movie"), fieldTypeAtCaret())
    }

    fun `test uses false has no models`() {
        withFramework()
        appController("public \$uses = array('User');")
        configureController("public \$uses = false;", "\$this->Mo<caret>vie;")
        assertEquals(emptySet<String>(), fieldTypeAtCaret())
    }

    fun `test AppController uses are appended`() {
        withFramework()
        appController("public \$uses = array('User');")
        configureController("public \$uses = array('Artist');", "\$this->Us<caret>er;")
        assertEquals(setOf("\\User"), fieldTypeAtCaret())
    }

    fun `test loadModel makes a model resolve that uses does not list`() {
        withFramework()
        appController()
        configureController(
            "public \$uses = array('Artist');",
            "\$this->loadModel('Movie');\n\$this->Mo<caret>vie;"
        )
        assertEquals(setOf("\\Movie"), fieldTypeAtCaret())
    }

    fun `test incomplete components keep the component convention only`() {
        withFramework()
        appController()
        configureController("public \$components = array(self::AUTH);", "\$this->Fo<caret>o;")
        assertEquals(setOf("\\FooComponent"), fieldTypeAtCaret())
    }

    fun `test without the framework and declarations convention stays`() {
        assumeStubVendor("The real framework is always attached in real mode")
        appController()
        configureController("", "\$this->Fo<caret>o;")
        assertEquals(setOf("\\Foo", "\\FooComponent"), fieldTypeAtCaret())

        configureController("", "\$this-><caret>")
        val result = completions()
        assertTrue("Expected Unloaded in $result", result.contains("Unloaded"))
        assertTrue("Expected User in $result", result.contains("User"))
    }

    fun `test goto on an aliased field goes to the className`() {
        withFramework()
        appController()
        configureController(
            "public \$components = array('Meta' => array('className' => 'MovieMetadata'));",
            "\$this->Me<caret>ta;"
        )
        assertEquals(
            setOf("MovieMetadataComponent"),
            gotoClassNames(gotoDeclarationHandlerTargets(ControllerFieldGotoDeclarationHandler()))
        )
    }

    fun `test goto on property array strings resolves through the configuration`() {
        withFramework()
        appController("public \$components = array('Auth' => array('className' => 'MyAuth'));")
        val handler = ControllerComponentsGotoDeclarationHandler()

        configureController("public \$components = array('Me<caret>ta' => array('className' => 'MovieMetadata'));", "")
        assertEquals(setOf("MovieMetadataComponent"), gotoClassNames(gotoDeclarationHandlerTargets(handler)))

        configureController("public \$components = array('Meta' => array('className' => 'MovieMeta<caret>data'));", "")
        assertEquals(setOf("MovieMetadataComponent"), gotoClassNames(gotoDeclarationHandlerTargets(handler)))

        configureController("public \$components = array('Au<caret>th' => array('authorize' => 'Controller'));", "")
        assertEquals(setOf("MyAuthComponent"), gotoClassNames(gotoDeclarationHandlerTargets(handler)))

        configureController("public \$uses = array('Art<caret>ist');", "")
        assertEquals(setOf("Artist"), gotoClassNames(gotoDeclarationHandlerTargets(handler)))
    }
}
