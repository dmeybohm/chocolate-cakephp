package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

class ConfiguredTableTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/ConfiguredTableController.php",
            "cake3/src/Model/Table/MoviesTable.php",
            "cake3/src/Model/Table/ArticlesTable.php",
            "cake3/src/Model/Table/CatalogTable.php",
            "cake3/vendor/test/test_plugin/src/Model/Table/CatalogTable.php",
            "cake3/vendor/cakephp.php",
        )
    }

    private fun configureUsage(body: String) {
        myFixture.configureByText("UsageController.php", """
        <?php
        namespace App\Controller;

        class UsageController extends ConfiguredTableController
        {
            public function index() {
                $body
            }
        }
        """.trimIndent())
    }

    private fun fieldTypeAtCaret(): Set<String> {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    fun `test loadModel overrides the convention candidates`() {
        configureUsage("\$this->Mov<caret>ies;")
        assertEquals(setOf("\\App\\Model\\Table\\MoviesTable"), fieldTypeAtCaret())
    }

    fun `test plugin prefixed loadModel resolves to the plugin table only`() {
        configureUsage("\$this->Cata<caret>log;")
        assertEquals(setOf("\\TestPlugin\\Model\\Table\\CatalogTable"), fieldTypeAtCaret())
    }

    fun `test table completion lists the loaded aliases and hides the convention list`() {
        configureUsage("\$this-><caret>")
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("Movies"))
        assertTrue(result.contains("Catalog"))
        assertFalse(result.contains("Articles"))
    }

    fun `test nested completion on a plugin table resolves the inner field by type`() {
        configureUsage("\$this->Catalog-><caret>")
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("Articles"))
    }
}
