package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.controller.ControllerMethodLineMarker
import com.daveme.chocolateCakePHP.test.ViewNavigation
import com.daveme.chocolateCakePHP.view.ElementGotoDeclarationHandler
import com.daveme.chocolateCakePHP.view.TemplateGotoDeclarationHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
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

    // The files Go to Declaration opens from the string literal [literal] in [file]
    private fun declarations(handler: GotoDeclarationHandler, file: PsiFile, literal: String): Set<String> {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("'$literal'") + 1)
        return gotoDeclarationHandlerTargets(handler).orEmpty()
            .mapNotNull { (it as? PsiFile)?.virtualFile?.path?.substringAfter("/cake2/") }
            .toSet()
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

    fun `test a camelCase render name links its underscored template only`() {
        controller("Widgets", "public function index() { \$this->render('viewAll'); }")
        val underscored = template("$app/Widgets/view_all")
        val camelCased = template("$app/Widgets/viewAll")

        assertEquals(setOf("Controllers: WidgetsController::index()"), navigation.related(underscored))
        assertEmpty(navigation.related(camelCased))
    }

    fun `test a camelCase render name navigates to its underscored template`() {
        val controller = controller("Widgets", "public function index() { \$this->render('viewAll'); }")
        template("$app/Widgets/view_all")
        template("$app/Widgets/viewAll")

        assertEquals(setOf("Widgets/view_all.ctp"), markedTemplates(controller, "index"))
        assertEquals(
            setOf("app/View/Widgets/view_all.ctp"),
            declarations(TemplateGotoDeclarationHandler(), controller, "viewAll")
        )
    }

    fun `test a render name with a slash keeps its case`() {
        val controller = controller("Widgets", "public function index() { \$this->render('Sub/viewAll'); }")
        val camelCased = template("$app/Widgets/Sub/viewAll")
        val underscored = template("$app/Widgets/Sub/view_all")

        assertEquals(setOf("Controllers: WidgetsController::index()"), navigation.related(camelCased))
        assertEmpty(navigation.related(underscored))
        assertEquals(setOf("Sub/viewAll.ctp"), markedTemplates(controller, "index"))
        assertEquals(
            setOf("app/View/Widgets/Sub/viewAll.ctp"),
            declarations(TemplateGotoDeclarationHandler(), controller, "Sub/viewAll")
        )
    }

    fun `test a camelCase view property links its underscored template`() {
        val controller = controller("Widgets", "public function index() { \$this->view = 'viewAll'; }")
        val underscored = template("$app/Widgets/view_all")
        val camelCased = template("$app/Widgets/viewAll")

        assertEquals(setOf("Controllers: WidgetsController::index()"), navigation.related(underscored))
        assertEmpty(navigation.related(camelCased))
        assertEquals(setOf("Widgets/view_all.ctp"), markedTemplates(controller, "index"))
        assertEquals(
            setOf("app/View/Widgets/view_all.ctp"),
            declarations(TemplateGotoDeclarationHandler(), controller, "viewAll")
        )
    }

    fun `test a camelCase element name keeps its case`() {
        val template = template("$app/Widgets/index", "echo \$this->element('camelElement');")
        template("$app/Elements/camelElement")
        template("$app/Elements/camel_element")

        assertEquals(
            setOf("app/View/Elements/camelElement.ctp"),
            declarations(ElementGotoDeclarationHandler(), template, "camelElement")
        )
    }
}
