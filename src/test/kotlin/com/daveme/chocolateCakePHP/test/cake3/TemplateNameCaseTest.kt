package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.controller.ControllerMethodLineMarker
import com.daveme.chocolateCakePHP.test.ViewNavigation
import com.daveme.chocolateCakePHP.view.CellGotoDeclarationHandler
import com.daveme.chocolateCakePHP.view.ElementGotoDeclarationHandler
import com.daveme.chocolateCakePHP.view.TemplateGotoDeclarationHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method

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
            .mapNotNull { (it as? PsiFile)?.virtualFile?.path?.substringAfter("/cake3/") }
            .toSet()
    }

    fun `test an implicit render is labelled with its controller method`() {
        controller("Widgets", "public function viewAll() {}")
        val template = template("$app/Widgets/view_all")

        assertEquals(setOf("Controllers: WidgetsController::viewAll()"), navigation.related(template))
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
            setOf("src/Template/Widgets/view_all.ctp"),
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
            setOf("src/Template/Widgets/Sub/viewAll.ctp"),
            declarations(TemplateGotoDeclarationHandler(), controller, "Sub/viewAll")
        )
    }

    fun `test a camelCase setTemplate name links its underscored template`() {
        val controller = controller("Widgets", "public function index() { \$this->viewBuilder()->setTemplate('viewAll'); }")
        val underscored = template("$app/Widgets/view_all")
        template("$app/Widgets/viewAll")

        assertEquals(setOf("Controllers: WidgetsController::index()"), navigation.related(underscored))
        assertEquals(setOf("Widgets/view_all.ctp"), markedTemplates(controller, "index"))
        assertEquals(
            setOf("src/Template/Widgets/view_all.ctp"),
            declarations(TemplateGotoDeclarationHandler(), controller, "viewAll")
        )
    }

    fun `test a camelCase setTemplate name after setTemplatePath links its underscored template`() {
        val controller = controller(
            "Widgets",
            "public function index() { \$this->viewBuilder()->setTemplatePath('Gadgets')->setTemplate('viewAll'); }"
        )
        val underscored = template("$app/Gadgets/view_all")
        val camelCased = template("$app/Gadgets/viewAll")

        assertEquals(setOf("Controllers: WidgetsController::index()"), navigation.related(underscored))
        assertEmpty(navigation.related(camelCased))
        assertEquals(setOf("Gadgets/view_all.ctp"), markedTemplates(controller, "index"))
        assertEquals(
            setOf("src/Template/Gadgets/view_all.ctp"),
            declarations(TemplateGotoDeclarationHandler(), controller, "viewAll")
        )
    }

    fun `test a camelCase element name keeps its case`() {
        val template = template("$app/Widgets/index", "echo \$this->element('camelElement');")
        template("$app/Element/camelElement")
        template("$app/Element/camel_element")

        assertEquals(
            setOf("src/Template/Element/camelElement.ctp"),
            declarations(ElementGotoDeclarationHandler(), template, "camelElement")
        )
    }

    fun `test a camelCase cell render name links its underscored template`() {
        myFixture.addFileToProject("cake3/src/View/Cell/LampCell.php", """
            <?php
            namespace App\View\Cell;

            class LampCell extends \Cake\View\Cell {
                public function display() {}
            }
        """.trimIndent())
        val caller = template("$app/Widgets/index", "echo \$this->cell('Lamp')->render('viewAll');")
        val underscored = template("$app/Cell/Lamp/view_all")
        val camelCased = template("$app/Cell/Lamp/viewAll")

        assertTrue(navigation.related(underscored).contains("Cell usages: index.ctp"))
        assertFalse(navigation.related(camelCased).contains("Cell usages: index.ctp"))
        assertEquals(
            setOf("src/Template/Cell/Lamp/view_all.ctp"),
            declarations(CellGotoDeclarationHandler(), caller, "viewAll")
        )
    }
}
