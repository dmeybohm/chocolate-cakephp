package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.model.TableLocatorGotoDeclarationHandler
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class CellFetchTableTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Model/Table/ArticlesTable.php",
            "cake5/src5/Model/Table/MoviesTable.php",
            "cake5/src5/Model/Entity/Article.php",
            "cake5/src5/Model/Entity/Movie.php"
        )
    }

    private fun cellWithBody(body: String) {
        myFixture.configureByFilePathAndText("cake5/src5/View/Cell/InventoryCell.php", """
        <?php
        namespace App\View\Cell;

        use Cake\View\Cell;

        class InventoryCell extends Cell
        {
            public function display(${'$'}id) {
                $body
            }
        }
        """.trimIndent())
    }

    fun `test fetchTable in a cell returns the table class`() {
        cellWithBody("${'$'}this->fetchTable('Articles')-><caret>")
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "myCustomArticleMethod")
    }

    fun `test fetchTable argument in a cell is completed`() {
        cellWithBody("${'$'}this->fetchTable('<caret>')")
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "Articles", "Movies")
    }

    fun `test get through fetchTable in a cell returns the entity`() {
        cellWithBody("""
            ${'$'}articles = ${'$'}this->fetchTable('Articles');
            ${'$'}article = ${'$'}articles->get(${'$'}id);
            ${'$'}article-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertContainsElements(result!!, "someArticleMethod")
    }

    fun `test goto declaration from fetchTable in a cell`() {
        cellWithBody("${'$'}this->fetchTable('<caret>Movies');")
        assertGotoDeclarationHandlerGoesToTableClass(TableLocatorGotoDeclarationHandler(), "MoviesTable")
    }

}
