package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

class ControllerSideHelperTest : Cake3BaseTestCase() {

    private val movieHelper = "\\App\\View\\Helper\\MovieFormatterHelper"

    override fun setUpTestFiles() {
        configureByFiles("cake3/vendor/cakephp.php")
        myFixture.addFileToProject("cake3/src/View/Helper/MovieFormatterHelper.php", """
            <?php
            namespace App\View\Helper;
            class MovieFormatterHelper extends \Cake\View\Helper {}
        """.trimIndent())
    }

    private fun controller(body: String) {
        myFixture.addFileToProject("cake3/src/Controller/MovieController.php", """
            <?php
            namespace App\Controller;
            class MovieController extends \Cake\Controller\Controller {
                $body
                public function index() {}
            }
        """.trimIndent())
    }

    private fun fmtTypesIn(path: String): Set<String> {
        val code = "<?php\n\$this->Fmt;\n"
        myFixture.addFileToProject(path, code)
        myFixture.configureFromTempProjectFile(path)
        myFixture.editor.caretModel.moveToOffset(code.indexOf("Fmt") + 1)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    fun `test the deprecated controller helpers property resolves in the template and a layout`() {
        controller("public ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];")
        assertEquals(setOf(movieHelper), fmtTypesIn("cake3/src/Template/Movie/index.ctp"))
        assertTrue(fmtTypesIn("cake3/src/Template/Layout/default.ctp").contains(movieHelper))
    }

    fun `test setHelpers on the view builder resolves in the template`() {
        controller("public function beforeRender(\\Cake\\Event\\Event ${'$'}event) { \$this->viewBuilder()->setHelpers(['Fmt' => ['className' => 'MovieFormatter']]); }")
        assertTrue(fmtTypesIn("cake3/src/Template/Movie/index.ctp").contains(movieHelper))
    }

    fun `test a helpers property on the view class is ignored`() {
        myFixture.addFileToProject("cake3/src/View/AppView.php", """
            <?php
            namespace App\View;
            class AppView extends \Cake\View\View {
                protected ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];
            }
        """.trimIndent())
        controller("")
        assertFalse(fmtTypesIn("cake3/src/Template/Movie/index.ctp").contains(movieHelper))
    }

    fun `test a helpers property on a cell resolves in its template`() {
        myFixture.addFileToProject("cake3/src/View/Cell/WidgetCell.php", """
            <?php
            namespace App\View\Cell;
            class WidgetCell extends \Cake\View\Cell {
                public ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];
                public function display() {}
            }
        """.trimIndent())
        assertEquals(setOf(movieHelper), fmtTypesIn("cake3/src/Template/Cell/Widget/display.ctp"))
    }
}
