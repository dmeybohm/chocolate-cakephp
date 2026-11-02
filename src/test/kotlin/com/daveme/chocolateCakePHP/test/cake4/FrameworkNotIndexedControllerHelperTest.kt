package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

class FrameworkNotIndexedControllerHelperTest : Cake4BaseTestCase() {

    override val attachesFramework = false

    override fun setUpTestFiles() {
    }

    fun `test a layout unions the aliases without the convention type when only app controllers settle it`() {
        myFixture.addFileToProject("cake4/src4/View/Helper/Helpers.php", """
            <?php
            namespace App\View\Helper;
            class MovieFormatterHelper extends \Cake\View\Helper {}
            class ArtistFormatterHelper extends \Cake\View\Helper {}
        """.trimIndent())
        myFixture.addFileToProject("cake4/src4/View/AppView.php", """
            <?php
            namespace App\View;
            class AppView extends \Cake\View\View {}
        """.trimIndent())
        for ((name, helper) in listOf("Movie" to "MovieFormatter", "Artist" to "ArtistFormatter")) {
            myFixture.addFileToProject("cake4/src4/Controller/${name}Controller.php", """
                <?php
                namespace App\Controller;
                class ${name}Controller extends \Cake\Controller\Controller {
                    public function initialize(): void { ${'$'}this->viewBuilder()->addHelper('Fmt', ['className' => '$helper']); }
                    public function index() {}
                }
            """.trimIndent())
        }
        val code = "<?php\n\$this->Fmt;\n"
        myFixture.addFileToProject("cake4/templates/layout/default.php", code)
        myFixture.configureFromTempProjectFile("cake4/templates/layout/default.php")
        myFixture.editor.caretModel.moveToOffset(code.indexOf("Fmt") + 1)
        val fieldReference = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), FieldReference::class.java)
        assertNotNull(fieldReference)
        val types = fieldReference!!.type.lookupCompleteType(project, null).types.filter { it.startsWith("\\") }.toSet()
        assertEquals(setOf("\\App\\View\\Helper\\MovieFormatterHelper", "\\App\\View\\Helper\\ArtistFormatterHelper"), types)
    }
}
