package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

class ConfiguredTableTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
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

    fun `test aliased loadModel resolves to the table class`() {
        configureUsage("\$this->Fil<caret>ms;")
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
        assertTrue(result!!.contains("Films"))
        assertTrue(result.contains("Catalog"))
        assertFalse(result.contains("Movies"))
        assertFalse(result.contains("Articles"))
    }

    fun `test nested completion resolves the inner alias by type`() {
        configureUsage("\$this->Films-><caret>")
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("Articles"))
    }
}
