package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.controller.ControllerMethodLineMarker
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.Variable

class CellMethodLineMarkerTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/View/AppView.php",
            "cake5/templates/cell/Inventory/display.php",
            "cake5/templates/cell/Inventory/compact.php",
            "cake5/templates/cell/Admin/Report/display.php",
            "cake5/vendor/cakephp.php",
        )
    }

    private fun targetPaths(element: com.intellij.psi.PsiElement): Set<String> {
        val markers = calculateLineMarkers(element, ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)
        return gotoRelatedItems(markers.first())
            .mapNotNull { (it.element as? PsiFile)?.virtualFile?.path?.substringAfter("/cake5/") }
            .toSet()
    }

    private fun method(name: String): Method =
        PsiTreeUtil.findChildrenOfType(myFixture.file, Method::class.java).first { it.name == name }

    private fun thisOfCall(methodName: String): Variable {
        val ref = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
            .first { it.name == methodName }
        var receiver: com.intellij.psi.PsiElement? = ref
        while (receiver is MethodReference) {
            receiver = receiver.classReference
        }
        return receiver as Variable
    }

    fun `test marker on a cell action targets its template`() {
        myFixture.configureByFilePathAndText("cake5/src5/View/Cell/InventoryCell.php", """
        <?php
        namespace App\View\Cell;

        use Cake\View\Cell;

        class InventoryCell extends Cell
        {
            public function display(): void {}
        }
        """.trimIndent())
        assertEquals(
            setOf("templates/cell/Inventory/display.php"),
            targetPaths(method("display").nameIdentifier!!)
        )
    }

    fun `test marker on a sub-namespace cell action targets its template`() {
        myFixture.configureByFilePathAndText("cake5/src5/View/Cell/Admin/ReportCell.php", """
        <?php
        namespace App\View\Cell\Admin;

        use Cake\View\Cell;

        class ReportCell extends Cell
        {
            public function display(): void {}
        }
        """.trimIndent())
        assertEquals(
            setOf("templates/cell/Admin/Report/display.php"),
            targetPaths(method("display").nameIdentifier!!)
        )
    }

    fun `test setTemplate in a cell action gets its own marker`() {
        myFixture.configureByFilePathAndText("cake5/src5/View/Cell/InventoryCell.php", """
        <?php
        namespace App\View\Cell;

        use Cake\View\Cell;

        class InventoryCell extends Cell
        {
            public function display(): void {
                ${'$'}this->viewBuilder()->setTemplate('compact');
            }
        }
        """.trimIndent())
        assertEquals(
            setOf("templates/cell/Inventory/compact.php"),
            targetPaths(thisOfCall("setTemplate").firstChild)
        )
        assertEquals(
            setOf("templates/cell/Inventory/display.php", "templates/cell/Inventory/compact.php"),
            targetPaths(method("display").nameIdentifier!!)
        )
    }

    fun `test setTemplatePath chain in a cell action marks the chained template`() {
        myFixture.configureByFilePathAndText("cake5/src5/View/Cell/InventoryCell.php", """
        <?php
        namespace App\View\Cell;

        use Cake\View\Cell;

        class InventoryCell extends Cell
        {
            public function display(): void {
                ${'$'}this->viewBuilder()->setTemplatePath('cell/Admin/Report')->setTemplate('display');
            }
        }
        """.trimIndent())
        assertEquals(
            setOf("templates/cell/Admin/Report/display.php"),
            targetPaths(thisOfCall("setTemplate").firstChild)
        )
    }

    fun `test missing cell template offers to create it under the cell directory`() {
        myFixture.configureByFilePathAndText("cake5/src5/View/Cell/InventoryCell.php", """
        <?php
        namespace App\View\Cell;

        use Cake\View\Cell;

        class InventoryCell extends Cell
        {
            public function bigList(): void {}
        }
        """.trimIndent())
        val markers = calculateLineMarkers(method("bigList").nameIdentifier!!, ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)
        assertEmpty(gotoRelatedItems(markers.first()))
        val handler = markers.first().navigationHandler as com.daveme.chocolateCakePHP.controller.NavigateToViewPopupHandler
        val fullPath = handler.allViewPaths.defaultViewPath.fullPath
        assertTrue(fullPath, fullPath.endsWith("templates/cell/Inventory/big_list.php"))
        assertEmpty(handler.allViewPaths.dataViewPaths)
    }

}
