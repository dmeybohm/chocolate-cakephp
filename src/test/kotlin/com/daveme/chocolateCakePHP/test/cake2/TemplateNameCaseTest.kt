package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.controller.ControllerMethodLineMarker
import com.daveme.chocolateCakePHP.test.ViewNavigation
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method

class TemplateNameCaseTest : Cake2BaseTestCase() {

    private val app = "cake2/app/View"
    private val navigation by lazy { ViewNavigation(myFixture) }

    override fun setUpTestFiles() {
        myFixture.configureByFiles("cake2/app/Controller/AppController.php")
    }

    private fun template(path: String, code: String = "echo 1;"): PsiFile =
        myFixture.addFileToProject("$path.ctp", "<?php $code")

    private fun controller(name: String, members: String): PsiFile =
        myFixture.addFileToProject("cake2/app/Controller/${name}Controller.php", """
            <?php
            class ${name}Controller extends AppController {
                $members
            }
        """.trimIndent())

    private fun markedTemplates(controller: PsiFile, methodName: String): Set<String> {
        val method = PsiTreeUtil.findChildrenOfType(controller, Method::class.java).single { it.name == methodName }
        val marker = calculateLineMarkers(method.nameIdentifier!!, ControllerMethodLineMarker::class).single()
        return getRelatedItemInfos(gotoRelatedItems(marker)).map { "${it.containingDir}/${it.filename}" }.toSet()
    }

    fun `test an implicit render is labelled with its controller method`() {
        controller("Widgets", "public function viewAll() {}")
        val template = template("$app/Widgets/view_all")

        assertEquals(setOf("Controllers: WidgetsController::viewAll()"), navigation.related(template))
    }

    fun `test a camelCase action links its underscored template only`() {
        controller("Widgets", "public function viewAll() {}")
        template("$app/Widgets/view_all")
        val camelCased = template("$app/Widgets/viewAll")

        assertEmpty(navigation.related(camelCased))
    }

    fun `test a camelCase action navigates to its underscored template`() {
        val controller = controller("Widgets", "public function viewAll() {}")
        template("$app/Widgets/view_all")
        template("$app/Widgets/viewAll")

        assertEquals(setOf("Widgets/view_all.ctp"), markedTemplates(controller, "viewAll"))
    }
}
