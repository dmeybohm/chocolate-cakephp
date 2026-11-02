package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.test.ViewNavigation
import com.intellij.psi.PsiFile

class TemplateNameCaseTest : Cake4BaseTestCase() {

    private val app = "cake4/templates"
    private val navigation by lazy { ViewNavigation(myFixture) }

    override fun setUpTestFiles() {
        myFixture.configureByFiles("cake4/src4/Controller/AppController.php")
    }

    private fun template(path: String, code: String = "echo 1;"): PsiFile =
        myFixture.addFileToProject("$path.php", "<?php $code")

    private fun controller(name: String, members: String): PsiFile =
        myFixture.addFileToProject("cake4/src4/Controller/${name}Controller.php", """
            <?php
            namespace App\Controller;

            class ${name}Controller extends AppController {
                $members
            }
        """.trimIndent())

    fun `test an implicit render is labelled with its controller method`() {
        controller("Widgets", "public function viewAll() {}")
        val template = template("$app/Widgets/view_all")

        assertEquals(setOf("Controllers: WidgetsController::viewAll()"), navigation.related(template))
    }
}
