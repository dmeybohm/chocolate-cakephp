package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

class ControllerSideHelperTest : Cake4BaseTestCase() {

    private val movieHelper = "\\App\\View\\Helper\\MovieFormatterHelper"

    override fun setUpTestFiles() {
        myFixture.addFileToProject("cake4/src4/View/Helper/MovieFormatterHelper.php", """
            <?php
            namespace App\View\Helper;
            class MovieFormatterHelper extends \Cake\View\Helper {}
        """.trimIndent())
    }

    private fun controller(body: String) {
        myFixture.addFileToProject("cake4/src4/Controller/MovieController.php", """
            <?php
            namespace App\Controller;
            class MovieController extends \Cake\Controller\Controller {
                $body
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

    fun `test a helper added through the view builder resolves in the template and a layout`() {
        controller("public function index() { \$this->viewBuilder()->addHelper('Fmt', ['className' => 'MovieFormatter']); }")
        assertTrue(fmtTypesIn("cake4/templates/Movie/index.php").contains(movieHelper))
        assertTrue(fmtTypesIn("cake4/templates/layout/default.php").contains(movieHelper))
    }

    fun `test a helpers property on the view class is ignored`() {
        myFixture.addFileToProject("cake4/src4/View/AppView.php", """
            <?php
            namespace App\View;
            class AppView extends \Cake\View\View {
                protected ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];
            }
        """.trimIndent())
        controller("public function index() {}")
        assertFalse(fmtTypesIn("cake4/templates/Movie/index.php").contains(movieHelper))
    }

    fun `test a helpers property on a controller is ignored`() {
        controller("""
            public ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];
            public function index() {}
        """)
        assertFalse(fmtTypesIn("cake4/templates/Movie/index.php").contains(movieHelper))
    }
}
