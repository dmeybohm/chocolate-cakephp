package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.daveme.chocolateCakePHP.view.ViewHelperGotoDeclarationHandler
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpClass

class TemplateHelperTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.addFileToProject("cake2/app/View/Helper/Helpers.php", """
            <?php
            class AppHelper extends Helper {}
            class HtmlHelper extends AppHelper {}
            class BootstrapHtmlHelper extends HtmlHelper {}
            class FormHelper extends AppHelper {}
            class BootstrapFormHelper extends FormHelper {}
            class TimeHelper extends AppHelper {}
        """.trimIndent())
        myFixture.addFileToProject("cake2/app/Controller/AppController.php", """
            <?php
            class AppController extends Controller {
                public ${'$'}helpers = array('Form' => array('className' => 'BootstrapForm'));
            }
        """.trimIndent())
        myFixture.addFileToProject("cake2/app/Controller/MoviesController.php", """
            <?php
            class MoviesController extends AppController {
                public ${'$'}helpers = array('Html' => array('className' => 'BootstrapHtml'));
                public function index() {}
            }
        """.trimIndent())
    }

    private fun configureView(path: String, code: String) {
        val text = "<?php\n$code\n"
        val caret = text.indexOf("<caret>")
        val filePath = "cake2/app/View/$path"
        myFixture.addFileToProject(filePath, text.replace("<caret>", ""))
        myFixture.configureFromTempProjectFile(filePath)
        if (caret >= 0) {
            myFixture.editor.caretModel.moveToOffset(caret)
        }
    }

    private fun fieldTypeAtCaret(): Set<String> {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    fun `test a helper an AppView does not list keeps its convention type`() {
        myFixture.addFileToProject("cake2/app/View/AppView.php", """
            <?php
            class AppView extends View {
                public ${'$'}helpers = array('Form');
            }
        """.trimIndent())
        configureView("Movies/index.ctp", "\$this->Ti<caret>me;")
        val types = fieldTypeAtCaret()
        assertTrue("Expected \\TimeHelper in $types", types.contains("\\TimeHelper"))
        assertFalse("Unexpected \\null in $types", types.contains("\\null"))
    }

    fun `test a helper a helper does not list is null`() {
        myFixture.configureByText("MovieHelper.php", """
            <?php
            class MovieHelper extends AppHelper {
                public ${'$'}helpers = array('Form');
                public function title() {
                    ${'$'}this->Ti<caret>me;
                }
            }
        """.trimIndent())
        assertEquals(setOf("\\null"), fieldTypeAtCaret())
    }

    fun `test an aliased controller helper resolves in its template`() {
        configureView("Movies/index.ctp", "echo \$this->element('sidebar');\n\$this->Ht<caret>ml;")
        assertEquals(setOf("\\BootstrapHtmlHelper"), fieldTypeAtCaret())
    }

    fun `test an AppController helper alias resolves in a child controller's template`() {
        configureView("Movies/index.ctp", "\$this->Fo<caret>rm;")
        assertEquals(setOf("\\BootstrapFormHelper"), fieldTypeAtCaret())
    }

    fun `test a helper the controllers do not load keeps its convention type`() {
        configureView("Movies/index.ctp", "\$this->Ti<caret>me;")
        assertEquals(setOf("\\TimeHelper"), fieldTypeAtCaret())
    }

    fun `test an element and a nested element get the including template's helpers`() {
        myFixture.addFileToProject("cake2/app/View/Movies/index.ctp", "<?php echo \$this->element('sidebar');\n")
        configureView("Elements/sidebar.ctp", "echo \$this->element('nested');\n\$this->Ht<caret>ml;")
        assertEquals(setOf("\\BootstrapHtmlHelper"), fieldTypeAtCaret())

        configureView("Elements/nested.ctp", "\$this->Ht<caret>ml;")
        assertEquals(setOf("\\BootstrapHtmlHelper"), fieldTypeAtCaret())
    }

    fun `test an element nothing includes falls back to AppController`() {
        configureView("Elements/orphan.ctp", "\$this->Fo<caret>rm;")
        assertEquals(setOf("\\BootstrapFormHelper"), fieldTypeAtCaret())

        configureView("Elements/other_orphan.ctp", "\$this->Ht<caret>ml;")
        assertEquals(setOf("\\HtmlHelper"), fieldTypeAtCaret())
    }

    fun `test template completion offers the aliases and the convention helpers`() {
        configureView("Movies/index.ctp", "\$this-><caret>")
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        for (helper in listOf("Html", "Form", "Time")) {
            assertTrue("Expected $helper in $result", result!!.contains(helper))
        }
    }

    fun `test goto on a template helper goes to the aliased class`() {
        configureView("Movies/index.ctp", "\$this->Ht<caret>ml;")
        val targets = gotoDeclarationHandlerTargets(ViewHelperGotoDeclarationHandler())
        assertEquals(setOf("BootstrapHtmlHelper"), targets.orEmpty().filterIsInstance<PhpClass>().map { it.name }.toSet())
    }
}
