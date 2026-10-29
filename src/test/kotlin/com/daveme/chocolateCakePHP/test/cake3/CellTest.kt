package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.controller.ControllerMethodLineMarker
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.CellGotoDeclarationHandler
import com.daveme.chocolateCakePHP.view.ViewToControllerGotoRelatedProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass

class CellTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/MovieController.php",
            "cake3/src/View/AppView.php",
            "cake3/vendor/cakephp.php",
        )
        myFixture.copyFileToProject("cake3/src/View/Cell/InventoryCell.php")
        myFixture.copyFileToProject("cake3/src/Template/Cell/Inventory/display.ctp")
        myFixture.copyFileToProject("cake3/src/Template/Cell/Inventory/compact.ctp")
    }

    private fun gotoTargets() =
        gotoDeclarationHandlerTargets(CellGotoDeclarationHandler())?.toList() ?: emptyList()

    fun `test cell name goes to the cell class`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        echo ${'$'}this->cell('<caret>Inventory');
        """.trimIndent())
        assertEquals(
            listOf("\\App\\View\\Cell\\InventoryCell"),
            gotoTargets().filterIsInstance<PhpClass>().map { it.fqn }
        )
    }

    fun `test render on a cell goes to the cell template`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        echo ${'$'}this->cell('Inventory')->render('<caret>compact');
        """.trimIndent())
        assertEquals(
            listOf("src/Template/Cell/Inventory/compact.ctp"),
            gotoTargets().mapNotNull { (it as? PsiFile)?.virtualFile?.path?.substringAfter("/cake3/") }
        )
    }

    fun `test marker on a cell action targets its template`() {
        myFixture.configureByFilePathAndText("cake3/src/View/Cell/InventoryCell.php", """
        <?php
        namespace App\View\Cell;

        use Cake\View\Cell;

        class InventoryCell extends Cell
        {
            public function display() {}
        }
        """.trimIndent())
        val method = PsiTreeUtil.findChildOfType(myFixture.file, Method::class.java)!!
        val markers = calculateLineMarkers(method.nameIdentifier!!, ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)
        val targets = gotoRelatedItems(markers.first())
            .mapNotNull { (it.element as? PsiFile)?.virtualFile?.path?.substringAfter("/cake3/") }
        assertEquals(listOf("src/Template/Cell/Inventory/display.ctp"), targets)
    }

    fun `test cell action variables complete in the cell template and caller variables do not`() {
        myFixture.configureByFilePathAndText("cake3/src/Template/Movie/artist.ctp", """
        <?php
        ${'$'}this->set('fromCaller', 1);
        echo ${'$'}this->cell('Inventory');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake3/src/Template/Cell/Inventory/display.ctp", """
        <?php
        ${'$'}local = 1;
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "\$items")
        assertDoesntContain(result, "\$limit", "\$fromCaller", "\$metadata")
    }

    fun `test cell template navigates to the cell action`() {
        val templateFile = myFixture.configureByFile("cake3/src/Template/Cell/Inventory/display.ctp")
        val items = ViewToControllerGotoRelatedProvider().getItems(templateFile.firstChild!!)
        assertEquals(
            listOf("InventoryCell::display"),
            items.filter { it.group == "Cells" }.map {
                val method = it.element as Method
                "${method.containingClass?.name}::${method.name}"
            }
        )
    }

}
