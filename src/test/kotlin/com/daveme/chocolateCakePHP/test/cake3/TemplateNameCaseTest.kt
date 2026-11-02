package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.test.ViewNavigation
import com.intellij.psi.PsiFile

class TemplateNameCaseTest : Cake3BaseTestCase() {

    private val app = "cake3/src/Template"
    private val navigation by lazy { ViewNavigation(myFixture) }

    override fun setUpTestFiles() {
        myFixture.configureByFiles("cake3/src/Controller/AppController.php")
    }

    private fun template(path: String, code: String = "echo 1;"): PsiFile =
        myFixture.addFileToProject("$path.ctp", "<?php $code")

    private fun controller(name: String, members: String): PsiFile =
        myFixture.addFileToProject("cake3/src/Controller/${name}Controller.php", """
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
