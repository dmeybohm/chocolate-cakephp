package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.ViewToControllerGotoRelatedProvider
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference

class CellTemplateToCellGotoRelatedTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/View/AppView.php",
            "cake5/src5/View/Cell/InventoryCell.php",
        )
    }

    fun `test cell template navigates to the cell action and its callers`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('Inventory');
        """.trimIndent())
        val templateFile = myFixture.configureByFile("cake5/templates/cell/Inventory/display.php")

        val items = ViewToControllerGotoRelatedProvider().getItems(templateFile.firstChild!!)

        val cellItems = items.filter { it.group == "Cells" }
        assertEquals(listOf("display"), cellItems.map { (it.element as Method).name })
        assertEquals("\\App\\View\\Cell\\InventoryCell", (cellItems[0].element as Method).containingClass?.fqn)

        val usageItems = items.filter { it.group == "Cell usages" }
        assertEquals(listOf("\$this->cell('Inventory')"), usageItems.map { (it.element as MethodReference).text })
    }

    fun `test cell template rendered by setTemplate navigates to the cell action`() {
        myFixture.configureByFilePathAndText("cake5/src5/View/Cell/InventoryCell.php", """
        <?php
        namespace App\View\Cell;

        use Cake\View\Cell;

        class InventoryCell extends Cell
        {
            public function summary(): void {
                ${'$'}this->viewBuilder()->setTemplate('compact');
            }
        }
        """.trimIndent())
        val templateFile = myFixture.configureByFile("cake5/templates/cell/Inventory/compact.php")

        val items = ViewToControllerGotoRelatedProvider().getItems(templateFile.firstChild!!)

        val methods = items.filter { it.group == "Cells" }.mapNotNull {
            com.intellij.psi.util.PsiTreeUtil.getParentOfType(it.element, Method::class.java, false)?.name
        }
        assertContainsElements(methods, "summary")
    }

}
