package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.controller.ControllerMethodLineMarker
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.CellGotoDeclarationHandler
import com.daveme.chocolateCakePHP.view.ViewToControllerGotoRelatedProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass

class CellTest : Cake4BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles(
            "cake4/src4/Controller/AppController.php",
            "cake4/src4/Controller/MovieController.php",
            "cake4/src4/View/AppView.php",
            "cake4/vendor/cakephp.php",
        )
        myFixture.copyFileToProject("cake4/src4/View/Cell/InventoryCell.php")
        myFixture.copyFileToProject("cake4/templates/cell/Inventory/display.php")
        myFixture.copyFileToProject("cake4/templates/cell/Inventory/compact.php")
        myFixture.copyFileToProject("cake4/vendor/test/test_plugin/src/View/Cell/WidgetCell.php")
        myFixture.copyFileToProject("cake4/vendor/test/test_plugin/templates/cell/Widget/display.php")
    }

    private fun gotoTargets() =
        gotoDeclarationHandlerTargets(CellGotoDeclarationHandler())?.toList() ?: emptyList()

    fun `test cell name goes to the cell class`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('<caret>Inventory');
        """.trimIndent())
        assertEquals(
            listOf("\\App\\View\\Cell\\InventoryCell"),
            gotoTargets().filterIsInstance<PhpClass>().map { it.fqn }
        )
    }

    fun `test render on a cell goes to the cell template`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('Inventory')->render('<caret>compact');
        """.trimIndent())
        assertEquals(
            listOf("templates/cell/Inventory/compact.php"),
            gotoTargets().mapNotNull { (it as? PsiFile)?.virtualFile?.path?.substringAfter("/cake4/") }
        )
    }

    fun `test marker on a cell action targets its template`() {
        myFixture.configureByFilePathAndText("cake4/src4/View/Cell/InventoryCell.php", """
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
            .mapNotNull { (it.element as? PsiFile)?.virtualFile?.path?.substringAfter("/cake4/") }
        assertEquals(listOf("templates/cell/Inventory/display.php"), targets)
    }

    fun `test cell action variables complete in the cell template and caller variables do not`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this->set('fromCaller', 1);
        echo ${'$'}this->cell('Inventory');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake4/templates/cell/Inventory/display.php", """
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
        val templateFile = myFixture.configureByFile("cake4/templates/cell/Inventory/display.php")
        val items = ViewToControllerGotoRelatedProvider().getItems(templateFile.firstChild!!)
        assertEquals(
            listOf("InventoryCell::display"),
            items.filter { it.group == "Cells" }.map {
                val method = it.element as Method
                "${method.containingClass?.name}::${method.name}"
            }
        )
    }

    fun `test cell names are completed including plugin cells`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('<caret>');
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "Inventory", "TestPlugin.Widget")
    }

    fun `test cell actions are completed after the separator`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('Inventory::<caret>');
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "Inventory::display", "Inventory::compact")
    }

    fun `test plugin cell name goes to the plugin cell class`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('TestPlugin.<caret>Widget');
        """.trimIndent())
        assertEquals(
            listOf("\\TestPlugin\\View\\Cell\\WidgetCell"),
            gotoTargets().filterIsInstance<PhpClass>().map { it.fqn }
        )
    }

    fun `test render on a plugin cell goes to the plugin's cell template`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('TestPlugin.Widget')->render('<caret>display');
        """.trimIndent())
        assertEquals(
            listOf("vendor/test/test_plugin/templates/cell/Widget/display.php"),
            gotoTargets().mapNotNull { (it as? PsiFile)?.virtualFile?.path?.substringAfter("/cake4/") }
        )
    }

    fun `test fetchTable in a cell returns the table class`() {
        myFixture.copyFileToProject("cake4/src4/Model/Table/ArticlesTable.php")
        myFixture.configureByFilePathAndText("cake4/src4/View/Cell/InventoryCell.php", """
        <?php
        namespace App\View\Cell;

        use Cake\View\Cell;

        class InventoryCell extends Cell
        {
            public function display() {
                ${'$'}this->fetchTable('Articles')-><caret>
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "myCustomArticleMethod")
    }

}
