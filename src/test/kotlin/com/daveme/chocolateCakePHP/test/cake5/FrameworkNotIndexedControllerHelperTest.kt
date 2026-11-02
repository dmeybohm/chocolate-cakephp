package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.daveme.chocolateCakePHP.view.RenderingControllers
import com.daveme.chocolateCakePHP.view.viewFileOf
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

class FrameworkNotIndexedControllerHelperTest : Cake5BaseTestCase() {

    override val attachesFramework = false

    override fun setUpTestFiles() {
    }

    fun `test a layout finds controllers at any depth when the framework class is not indexed`() {
        myFixture.addFileToProject("cake5/src5/Controller/AppController.php", """
            <?php
            namespace App\Controller;
            class AppController extends \Cake\Controller\Controller {}
        """.trimIndent())
        myFixture.addFileToProject("cake5/src5/Controller/MovieController.php", """
            <?php
            namespace App\Controller;
            class MovieController extends AppController {}
        """.trimIndent())
        val layout = myFixture.addFileToProject("cake5/templates/layout/default.php", "<?php echo 1;")
        val viewFile = viewFileOf(project, Settings.getInstance(project), layout.virtualFile)!!
        assertEquals(
            setOf("\\App\\Controller\\AppController", "\\App\\Controller\\MovieController"),
            RenderingControllers.of(project, viewFile).toSet()
        )
    }

    fun `test a layout unions the aliases without the convention type when only app controllers settle it`() {
        myFixture.configureByFiles(
            "cake5/src5/View/Helper/MovieFormatterHelper.php",
            "cake5/src5/View/Helper/ArtistFormatterHelper.php",
        )
        myFixture.addFileToProject("cake5/src5/View/AppView.php", """
            <?php
            namespace App\View;
            class AppView extends \Cake\View\View {}
        """.trimIndent())
        for ((name, helper) in listOf("Movie" to "MovieFormatter", "Artist" to "ArtistFormatter")) {
            myFixture.addFileToProject("cake5/src5/Controller/${name}Controller.php", """
                <?php
                namespace App\Controller;
                class ${name}Controller extends \Cake\Controller\Controller {
                    public function initialize(): void { ${'$'}this->viewBuilder()->addHelper('Fmt', ['className' => '$helper']); }
                    public function index() {}
                }
            """.trimIndent())
        }
        val code = "<?php\n\$this->Fmt;\n"
        myFixture.addFileToProject("cake5/templates/layout/default.php", code)
        myFixture.configureFromTempProjectFile("cake5/templates/layout/default.php")
        myFixture.editor.caretModel.moveToOffset(code.indexOf("Fmt") + 1)
        val fieldReference = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), FieldReference::class.java)
        assertNotNull(fieldReference)
        val types = fieldReference!!.type.lookupCompleteType(project, null).types.filter { it.startsWith("\\") }.toSet()
        assertEquals(setOf("\\App\\View\\Helper\\MovieFormatterHelper", "\\App\\View\\Helper\\ArtistFormatterHelper"), types)
    }
}
