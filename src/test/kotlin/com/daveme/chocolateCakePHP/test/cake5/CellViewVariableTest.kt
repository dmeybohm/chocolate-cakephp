package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.jetbrains.php.lang.inspections.PhpUndefinedVariableInspection

class CellViewVariableTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Controller/MovieController.php",
            "cake5/src5/View/AppView.php",
            "cake5/src5/View/Cell/InventoryCell.php",
            "cake5/src5/View/Cell/Admin/ReportCell.php",
            "cake5/vendor/cakephp.php",
        )
    }

    fun `test cell action variable completes with its type in the cell template`() {
        myFixture.configureByFilePathAndText("cake5/templates/cell/Inventory/display.php", """
        <?php
        ${'$'}local = 1;
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "\$items")
        assertDoesntContain(result, "\$limit", "\$total")

        val itemsElement = myFixture.lookupElements!!.first { it.lookupString == "\$items" }
        val presentation = LookupElementPresentation()
        itemsElement.renderElement(presentation)
        assertEquals("string[]", presentation.typeText)
    }

    fun `test variables of another action stay in that action's template`() {
        myFixture.configureByFilePathAndText("cake5/templates/cell/Inventory/compact.php", """
        <?php
        ${'$'}local = 1;
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "\$limit")
        assertDoesntContain(result, "\$items")
    }

    fun `test sub-namespace cell variables reach its template`() {
        myFixture.configureByFilePathAndText("cake5/templates/cell/Admin/Report/display.php", """
        <?php
        ${'$'}local = 1;
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "\$total")
    }

    fun `test undefined variable inspection accepts cell variables`() {
        myFixture.enableInspections(PhpUndefinedVariableInspection::class.java)
        myFixture.configureByFilePathAndText("cake5/templates/cell/Inventory/display.php", """
        <?php
        echo count(${'$'}items);
        echo <error descr="Undefined variable '${'$'}limit'">${'$'}limit</error>;
        """.trimIndent())
        myFixture.checkHighlighting(true, false, false)
    }

    fun `test variables of a template calling the cell do not reach the cell template`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}this->set('fromCaller', 1);
        echo ${'$'}this->cell('Inventory');
        """.trimIndent())
        myFixture.configureByFilePathAndText("cake5/templates/cell/Inventory/display.php", """
        <?php
        ${'$'}local = 1;
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "\$items")
        assertDoesntContain(result, "\$fromCaller", "\$metadata")
    }

}
