package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

class PluginTemplateHelperTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles("cake2/vendor/cakephp.php")
        myFixture.addFileToProject("cake2/app/View/Helper/Helpers.php", """
            <?php
            class AppHelper extends Helper {}
            class HtmlHelper extends AppHelper {}
            class BootstrapHtmlHelper extends HtmlHelper {}
            class FooHtmlHelper extends HtmlHelper {}
        """.trimIndent())
        myFixture.addFileToProject("cake2/app/Controller/AppController.php", """
            <?php
            class AppController extends Controller {}
        """.trimIndent())
        myFixture.addFileToProject("cake2/app/Controller/MoviesController.php", """
            <?php
            class MoviesController extends AppController {
                public ${'$'}helpers = array('Html' => array('className' => 'BootstrapHtml'));
                public function index() {}
            }
        """.trimIndent())
        myFixture.addFileToProject("cake2/app/Plugin/Foo/Controller/FooAppController.php", """
            <?php
            class FooAppController extends AppController {}
        """.trimIndent())
        myFixture.addFileToProject("cake2/app/Plugin/Foo/Controller/BarsController.php", """
            <?php
            class BarsController extends FooAppController {
                public ${'$'}helpers = array('Html' => array('className' => 'FooHtml'));
                public function index() {}
            }
        """.trimIndent())
        // Element references are only indexed once the element directory exists:
        myFixture.addFileToProject("cake2/app/View/Elements/empty.ctp", "")
        myFixture.addFileToProject("cake2/app/Plugin/Foo/View/Elements/empty.ctp", "")
        myFixture.addFileToProject(
            "cake2/app/View/Movies/index.ctp",
            "<?php echo \$this->element('sidebar'); echo \$this->element('Foo.promo');\n"
        )
        myFixture.addFileToProject(
            "cake2/app/Plugin/Foo/View/Bars/index.ctp",
            "<?php echo \$this->element('sidebar'); echo \$this->element('footer');\n"
        )
    }

    private fun configureView(path: String, code: String) {
        val text = "<?php\n$code\n"
        myFixture.addFileToProject(path, text.replace("<caret>", ""))
        myFixture.configureFromTempProjectFile(path)
        myFixture.editor.caretModel.moveToOffset(text.indexOf("<caret>"))
    }

    private fun fieldTypeAtCaret(): Set<String> {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    fun `test same-named app and plugin elements each get their own controllers`() {
        configureView("cake2/app/Plugin/Foo/View/Elements/sidebar.ctp", "\$this->Ht<caret>ml;")
        assertEquals(setOf("\\FooHtmlHelper"), fieldTypeAtCaret())

        // The plugin has its own sidebar, so its unprefixed reference does not reach the app's:
        configureView("cake2/app/View/Elements/sidebar.ctp", "\$this->Ht<caret>ml;")
        assertEquals(setOf("\\BootstrapHtmlHelper"), fieldTypeAtCaret())
    }

    fun `test an app override of a plugin template gets the plugin controller`() {
        configureView("cake2/app/View/Plugin/Foo/Bars/index.ctp", "\$this->Ht<caret>ml;")
        assertEquals(setOf("\\FooHtmlHelper"), fieldTypeAtCaret())
    }

    fun `test a plugin prefixed element gets the including app template's controller`() {
        configureView("cake2/app/Plugin/Foo/View/Elements/promo.ctp", "\$this->Ht<caret>ml;")
        assertEquals(setOf("\\BootstrapHtmlHelper"), fieldTypeAtCaret())
    }

    fun `test an element the plugin lacks falls back to the app's and gets the plugin controller`() {
        configureView("cake2/app/View/Elements/footer.ctp", "\$this->Ht<caret>ml;")
        assertEquals(setOf("\\FooHtmlHelper"), fieldTypeAtCaret())
    }
}
