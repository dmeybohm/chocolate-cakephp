package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.controller.ControllerComponentsGotoDeclarationHandler
import com.daveme.chocolateCakePHP.controller.ControllerFieldGotoDeclarationHandler
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import java.io.File

class ConfiguredComponentTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/ConfiguredController.php",
            "cake3/src/Controller/ChildConfiguredController.php",
            "cake3/src/Controller/BeforeFilterOnlyController.php",
            "cake3/src/Controller/MovieController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/src/Controller/Component/InsidePluginComponent.php",
            "cake3/src/Controller/Component/ConditionalComponent.php",
            "cake3/src/Controller/Component/UnloadedComponent.php",
            "cake3/vendor/test/test_plugin/src/Controller/Component/InsidePluginComponent.php",
            "cake3/vendor/test/test_plugin/src/Controller/Component/ConditionalComponent.php",
            "cake3/src/Model/Table/MoviesTable.php",
            "cake3/src/Model/Table/ArticlesTable.php",
        )
    }

    private fun configureControllerWithBody(path: String, body: String) {
        val original = File("src/test/fixtures/$path").readText().trimEnd()
        val text = original.removeSuffix("}") +
                "\n    public function probe()\n    {\n        $body\n    }\n}\n"
        myFixture.configureByFilePathAndText(path, text)
    }

    private fun configureConfiguredController(body: String) =
        configureControllerWithBody("cake3/src/Controller/ConfiguredController.php", body)

    private fun fieldTypeAtCaret(): Set<String> {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    private fun completionsOnThis(): List<String> {
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        return result!!
    }

    fun `test aliased load with class constant resolves to the class and not by convention`() {
        configureConfiguredController("\$this->Me<caret>ta;")
        assertEquals(
            setOf(
                "\\App\\Controller\\Component\\MovieMetadataComponent",
                // Unioned from the re-load in reload():
                "\\App\\Controller\\Component\\ConditionalComponent",
                "\\TestPlugin\\Controller\\Component\\ConditionalComponent",
            ),
            fieldTypeAtCaret()
        )
    }

    fun `test aliased load with plain class name string is not treated as an FQN`() {
        configureConfiguredController("\$this->Leg<caret>acy;")
        assertEquals(setOf("\\App\\Controller\\Component\\MovieMetadataComponent"), fieldTypeAtCaret())
    }

    fun `test plugin prefixed load resolves to the plugin component only`() {
        configureConfiguredController("\$this->Inside<caret>Plugin;")
        assertEquals(setOf("\\TestPlugin\\Controller\\Component\\InsidePluginComponent"), fieldTypeAtCaret())
    }

    fun `test core component load resolves to the core class`() {
        configureConfiguredController("\$this->Fl<caret>ash;")
        assertEquals(setOf("\\Cake\\Controller\\Component\\FlashComponent"), fieldTypeAtCaret())
    }

    fun `test unloaded table property keeps convention`() {
        configureConfiguredController("\$this->Mov<caret>ies;")
        assertTrue(fieldTypeAtCaret().contains("\\App\\Model\\Table\\MoviesTable"))
    }

    fun `test loads resolve through parent classes`() {
        myFixture.configureByText("UsageController.php", """
        <?php
        namespace App\Controller;

        class UsageController extends ChildConfiguredController
        {
            public function index() {
                ${'$'}this->Leg<caret>acy;
            }
        }
        """.trimIndent())
        assertEquals(setOf("\\App\\Controller\\Component\\MovieMetadataComponent"), fieldTypeAtCaret())
    }

    fun `test flash resolves through the child controller`() {
        configureControllerWithBody("cake3/src/Controller/ChildConfiguredController.php", "\$this->Fl<caret>ash;")
        assertEquals(setOf("\\Cake\\Controller\\Component\\FlashComponent"), fieldTypeAtCaret())
    }

    fun `test load in beforeFilter is unioned with convention`() {
        configureConfiguredController("\$this->Conditional-><caret>")
        val result = completionsOnThis()
        assertTrue(result.contains("pluginConditionalMethod"))
        assertTrue(result.contains("appConditionalMethod"))
    }

    fun `test reload in an action is unioned onto the initialize load`() {
        configureConfiguredController("\$this->Meta-><caret>")
        val result = completionsOnThis()
        assertTrue(result.contains("generateMetadata"))
        assertTrue(result.contains("appConditionalMethod"))
    }

    fun `test component completion lists only the loaded aliases`() {
        configureConfiguredController("\$this-><caret>")
        val result = completionsOnThis()
        for (alias in listOf("MovieMetadata", "Meta", "Legacy", "InsidePlugin", "Flash", "Conditional", "RequestHandler")) {
            assertTrue("Expected $alias in $result", result.contains(alias))
        }
        assertFalse(result.contains("Unloaded"))
        assertEquals(1, result.count { it == "MovieMetadata" })

        // Nothing in the chain calls loadModel, so tables keep the full list:
        assertTrue(result.contains("Movies"))
        assertTrue(result.contains("Articles"))
    }

    fun `test loaded alias shows the resolved class as type text`() {
        configureConfiguredController("\$this-><caret>")
        myFixture.completeBasic()
        val element = myFixture.lookupElements!!.find { it.lookupString == "Legacy" }
        assertNotNull(element)
        val presentation = LookupElementPresentation()
        element!!.renderElement(presentation)
        assertEquals("App\\Controller\\Component\\MovieMetadataComponent", presentation.typeText)
    }

    fun `test component completion without loads lists all components`() {
        configureControllerWithBody("cake3/src/Controller/MovieController.php", "\$this-><caret>")
        val result = completionsOnThis()
        assertTrue(result.contains("Unloaded"))
        assertTrue(result.contains("MovieMetadata"))
        assertTrue(result.contains("Flash"))
    }

    fun `test component completion with only a beforeFilter load lists the alias and convention`() {
        configureControllerWithBody("cake3/src/Controller/BeforeFilterOnlyController.php", "\$this-><caret>")
        val result = completionsOnThis()
        assertTrue(result.contains("Late"))
        assertTrue(result.contains("Unloaded"))
    }

    fun `test goto declaration on an aliased field has one target`() {
        configureConfiguredController("\$this->Leg<caret>acy;")
        val targets = gotoDeclarationHandlerTargets(ControllerFieldGotoDeclarationHandler())
        assertNotNull(targets)
        assertEquals(listOf("MovieMetadataComponent"), targets!!.map { (it as PhpClass).name })
    }

    fun `test goto declaration on a plugin prefixed field has one target`() {
        configureConfiguredController("\$this->Inside<caret>Plugin;")
        val targets = gotoDeclarationHandlerTargets(ControllerFieldGotoDeclarationHandler())
        assertNotNull(targets)
        assertEquals(listOf("\\TestPlugin\\Controller\\Component\\InsidePluginComponent"),
            targets!!.map { (it as PhpClass).fqn })
    }

    fun `test goto declaration on the loadComponent string`() {
        myFixture.configureByText("StringController.php", """
        <?php
        namespace App\Controller;

        class StringController extends AppController
        {
            public function initialize(): void {
                ${'$'}this->loadComponent('MovieMeta<caret>data');
            }
        }
        """.trimIndent())
        val targets = gotoDeclarationHandlerTargets(ControllerComponentsGotoDeclarationHandler())
        assertNotNull(targets)
        assertEquals(listOf("MovieMetadataComponent"), targets!!.map { (it as PhpClass).name })
    }

    fun `test goto declaration on a plugin prefixed loadComponent string`() {
        myFixture.configureByText("StringController.php", """
        <?php
        namespace App\Controller;

        class StringController extends AppController
        {
            public function initialize(): void {
                ${'$'}this->loadComponent('TestPlugin.Inside<caret>Plugin');
            }
        }
        """.trimIndent())
        val targets = gotoDeclarationHandlerTargets(ControllerComponentsGotoDeclarationHandler())
        assertNotNull(targets)
        assertEquals(listOf("\\TestPlugin\\Controller\\Component\\InsidePluginComponent"),
            targets!!.map { (it as PhpClass).fqn })
    }

    fun `test goto declaration on the alias and className strings`() {
        myFixture.configureByText("StringController.php", """
        <?php
        namespace App\Controller;

        class StringController extends AppController
        {
            public function initialize(): void {
                ${'$'}this->loadComponent('Al<caret>ias', ['className' => 'TestPlugin.Conditional']);
            }
        }
        """.trimIndent())
        val aliasTargets = gotoDeclarationHandlerTargets(ControllerComponentsGotoDeclarationHandler())
        assertEquals(listOf("\\TestPlugin\\Controller\\Component\\ConditionalComponent"),
            aliasTargets!!.map { (it as PhpClass).fqn })

        val offset = myFixture.file.text.indexOf("Conditional'")
        myFixture.editor.caretModel.moveToOffset(offset + 2)
        val classNameTargets = gotoDeclarationHandlerTargets(ControllerComponentsGotoDeclarationHandler())
        assertEquals(listOf("\\TestPlugin\\Controller\\Component\\ConditionalComponent"),
            classNameTargets!!.map { (it as PhpClass).fqn })
    }

    private fun configureUnionReceiver(body: String) {
        myFixture.configureByText("UnionController.php", """
        <?php
        namespace App\Controller;

        class UnionController extends \Cake\Controller\Controller
        {
            /**
             * @param ConfiguredController|MovieController ${'$'}c
             */
            public function index(${'$'}c) {
                $body
            }
        }
        """.trimIndent())
    }

    fun `test union receiver resolves each controller against its own loads`() {
        configureUnionReceiver("\$c->Leg<caret>acy;")
        val types = fieldTypeAtCaret()
        assertTrue(types.contains("\\App\\Controller\\Component\\MovieMetadataComponent"))
        // MovieController does not load Legacy, so it keeps the convention candidates:
        assertTrue(types.contains("\\App\\Controller\\Component\\LegacyComponent"))
    }

    fun `test union receiver keeps convention completion when one controller has no loads`() {
        configureUnionReceiver("\$c-><caret>")
        val result = completionsOnThis()
        assertTrue(result.contains("Legacy"))
        assertTrue(result.contains("Unloaded"))
    }
}
