package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class CellCompletionTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/View/AppView.php",
            "cake5/src5/View/Cell/InventoryCell.php",
            "cake5/src5/View/Cell/Admin/ReportCell.php",
            "cake5/vendor/test/test_plugin/src/View/Cell/WidgetCell.php",
            "cake5/templates/Movie/artist.php",
        )
    }

    fun `test cell names are completed`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('<caret>');
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "Inventory", "Admin/Report", "TestPlugin.Widget")
    }

    fun `test plugin cell names are completed after the plugin prefix`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('TestPlugin.<caret>');
        """.trimIndent())
        myFixture.completeBasic()

        myFixture.checkResult("""
        <?php
        echo ${'$'}this->cell('TestPlugin.Widget');
        """.trimIndent())
    }

    fun `test cell actions are completed after the separator`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->cell('Inventory::<caret>');
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "Inventory::display", "Inventory::compact")
        assertDoesntContain(result, "Inventory::initialize", "Inventory::render")
    }

    fun `test cell names are not completed for other objects`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}obj = new SomeOtherObject();
        echo ${'$'}obj->cell('<caret>');
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        if (result != null) {
            assertDoesntContain(result, "Inventory", "Admin/Report")
        }
    }

}
