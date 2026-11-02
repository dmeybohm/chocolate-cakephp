package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

class FrameworkNotIndexedControllerHelperTest : Cake3BaseTestCase() {

    override val attachesFramework = false

    override fun setUpTestFiles() {
    }

    fun `test a layout unions the aliases without the convention type when only app controllers settle it`() {
        myFixture.addFileToProject("cake3/src/View/Helper/Helpers.php", """
            <?php
            namespace App\View\Helper;
            class MovieFormatterHelper extends \Cake\View\Helper {}
            class ArtistFormatterHelper extends \Cake\View\Helper {}
        """.trimIndent())
        myFixture.addFileToProject("cake3/src/View/AppView.php", """
            <?php
            namespace App\View;
            class AppView extends \Cake\View\View {}
        """.trimIndent())
        for ((name, helper) in listOf("Movie" to "MovieFormatter", "Artist" to "ArtistFormatter")) {
            myFixture.addFileToProject("cake3/src/Controller/${name}Controller.php", """
                <?php
                namespace App\Controller;
                class ${name}Controller extends \Cake\Controller\Controller {
                    public ${'$'}helpers = ['Fmt' => ['className' => '$helper']];
                    public function index() {}
                }
            """.trimIndent())
        }
        val code = "<?php\n\$this->Fmt;\n"
        myFixture.addFileToProject("cake3/src/Template/Layout/default.ctp", code)
        myFixture.configureFromTempProjectFile("cake3/src/Template/Layout/default.ctp")
        myFixture.editor.caretModel.moveToOffset(code.indexOf("Fmt") + 1)
        val fieldReference = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), FieldReference::class.java)
        assertNotNull(fieldReference)
        val types = fieldReference!!.type.lookupCompleteType(project, null).types.filter { it.startsWith("\\") }.toSet()
        assertEquals(setOf("\\App\\View\\Helper\\MovieFormatterHelper", "\\App\\View\\Helper\\ArtistFormatterHelper"), types)
    }
}
