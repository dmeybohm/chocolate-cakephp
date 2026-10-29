package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.viewfileindex.ElementType
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.application.ReadAction
import com.jetbrains.php.lang.psi.elements.MethodReference

class CellReferenceIndexTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Controller/MovieController.php",
            "cake5/src5/View/AppView.php",
            "cake5/src5/View/Cell/InventoryCell.php",
            "cake5/templates/cell/Inventory/display.php",
            "cake5/templates/cell/Inventory/compact.php",
            "cake5/vendor/cakephp.php",
        )
    }

    private fun referencesTo(key: String): List<String> = ReadAction.compute<List<String>, Nothing> {
        ViewFileIndexService.referencingElementsInSmartReadAction(project, key)
            .filter { it.elementType == ElementType.CELL_RENDER }
            .mapNotNull { (it.psiElement as? MethodReference)?.text }
    }

    fun `test cell call is indexed under the default template`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('Inventory');
        """.trimIndent())
        assertEquals(listOf("\$this->cell('Inventory')"), referencesTo("cell/Inventory/display"))
    }

    fun `test cell call with an action is indexed under the action template`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('Inventory::compact', [5]);
        """.trimIndent())
        assertEquals(listOf("\$this->cell('Inventory::compact', [5])"), referencesTo("cell/Inventory/compact"))
        assertEmpty(referencesTo("cell/Inventory/display"))
    }

    fun `test chained render is indexed under the rendered template only`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('Inventory')->render('compact');
        """.trimIndent())
        assertEquals(listOf("\$this->cell('Inventory')->render('compact')"), referencesTo("cell/Inventory/compact"))
        assertEmpty(referencesTo("cell/Inventory/display"))
    }

    fun `test render on a variable assigned a cell is indexed`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}cell = ${'$'}this->cell('Inventory');
        echo ${'$'}cell->render('compact');
        """.trimIndent())
        assertEquals(listOf("\$cell->render('compact')"), referencesTo("cell/Inventory/compact"))
    }

    fun `test cell call through the view in a helper is indexed`() {
        myFixture.configureByFilePathAndText("cake5/src5/View/Helper/MovieFormatterHelper.php", """
        <?php
        namespace App\View\Helper;

        class MovieFormatterHelper extends \Cake\View\Helper
        {
            public function show() {
                return ${'$'}this->_View->cell('Inventory');
            }
        }
        """.trimIndent())
        assertEquals(listOf("\$this->_View->cell('Inventory')"), referencesTo("cell/Inventory/display"))
    }

    fun `test cell call on another object is not indexed`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}obj = new SomeOtherObject();
        echo ${'$'}obj->cell('Inventory');
        echo ${'$'}obj->render('compact');
        """.trimIndent())
        assertEmpty(referencesTo("cell/Inventory/display"))
        assertEmpty(referencesTo("cell/Inventory/compact"))
    }

    fun `test variables of the calling template do not reach the cell template`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}this->set('fromCaller', 1);
        echo ${'$'}this->cell('Inventory');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake5/templates/cell/Inventory/display.php", """
        <?php
        ${'$'}first = 1;
        ${'$'}second = 2;
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertDoesntContain(result!!, "\$fromCaller", "\$metadata")
    }

}
