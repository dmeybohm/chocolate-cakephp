package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.CellGotoDeclarationHandler
import com.intellij.psi.PsiFile

class CellRenderGotoDeclarationTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/View/AppView.php",
            "cake5/src5/View/Cell/InventoryCell.php",
            "cake5/src5/View/Cell/Admin/ReportCell.php",
            "cake5/templates/Movie/artist.php",
            "cake5/templates/cell/Inventory/display.php",
            "cake5/templates/cell/Inventory/compact.php",
            "cake5/templates/cell/Admin/Report/display.php",
            "cake5/vendor/test/test_plugin/src/View/Cell/WidgetCell.php",
            "cake5/vendor/test/test_plugin/templates/cell/Widget/display.php",
            "cake5/vendor/cakephp.php",
        )
    }

    private fun targetPaths(): List<String> =
        gotoDeclarationHandlerTargets(CellGotoDeclarationHandler())
            ?.mapNotNull { (it as? PsiFile)?.virtualFile?.path?.substringAfter("/cake5/") }
            ?: emptyList()

    fun `test chained render goes to the cell template`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('Inventory')->render('<caret>compact');
        """.trimIndent())
        assertEquals(listOf("templates/cell/Inventory/compact.php"), targetPaths())
    }

    fun `test render on a variable assigned a cell goes to the cell template`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}cell = ${'$'}this->cell('Inventory');
        echo ${'$'}cell->render('<caret>compact');
        """.trimIndent())
        assertEquals(listOf("templates/cell/Inventory/compact.php"), targetPaths())
    }

    fun `test render on a sub-namespace cell goes to its template`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('Admin/Report')->render('<caret>display');
        """.trimIndent())
        assertEquals(listOf("templates/cell/Admin/Report/display.php"), targetPaths())
    }

    fun `test render on an unrelated receiver does not navigate`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}cell = new SomeOtherObject();
        echo ${'$'}cell->render('<caret>compact');
        """.trimIndent())
        assertEmpty(targetPaths())
    }

    fun `test render on a cell call of another object does not navigate`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}obj = new SomeOtherObject();
        echo ${'$'}obj->cell('Inventory')->render('<caret>compact');
        """.trimIndent())
        assertEmpty(targetPaths())
    }

    fun `test render on a plugin cell goes to the plugin's cell template`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('TestPlugin.Widget')->render('<caret>display');
        """.trimIndent())
        assertEquals(listOf("vendor/test/test_plugin/templates/cell/Widget/display.php"), targetPaths())
    }

}
