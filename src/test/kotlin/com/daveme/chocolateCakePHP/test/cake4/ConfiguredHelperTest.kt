package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.ViewHelperGotoDeclarationHandler
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpClass

class ConfiguredHelperTest : Cake4BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake4/src4/View/ConfiguredView.php",
            "cake4/configured_view/AppView.php",
            "cake4/src4/View/Helper/ConfiguredHelper.php",
            "cake4/src4/View/Helper/MovieFormatterHelper.php",
            "cake4/src4/View/Helper/ArtistFormatterHelper.php",
            "cake4/src4/View/Helper/SharedHelper.php",
            "cake4/vendor/test/test_plugin/src/View/Helper/SharedHelper.php",
            "cake4/vendor/cakephp.php",
        )
    }

    private fun configureTemplate(body: String) {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", "<?php\n$body\n")
    }

    private fun configureConfiguredHelper(body: String) {
        myFixture.configureByFilePathAndText("cake4/src4/View/Helper/ConfiguredHelper.php", """
        <?php

        namespace App\View\Helper;

        class ConfiguredHelper extends \Cake\View\Helper
        {
            protected ${'$'}helpers = ['MovieFormatter', 'Alias' => ['className' => 'ArtistFormatter']];

            public function probe() {
                $body
            }
        }
        """.trimIndent())
    }

    private fun helperTypesAtCaret(): Set<String> {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    private fun completions(): List<String> {
        myFixture.completeBasic()
        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        return result!!
    }

    fun `test aliased helper in a template resolves through the view`() {
        configureTemplate("\$this->Fm<caret>t;")
        assertEquals(setOf("\\App\\View\\Helper\\MovieFormatterHelper"), helperTypesAtCaret())
    }

    fun `test plugin prefixed helper in a template resolves to the plugin helper only`() {
        configureTemplate("\$this->Sha<caret>red;")
        assertEquals(setOf("\\TestPlugin\\View\\Helper\\SharedHelper"), helperTypesAtCaret())
    }

    fun `test helper the view does not load keeps convention in a template`() {
        configureTemplate("\$this->ArtistForm<caret>atter;")
        assertTrue(helperTypesAtCaret().contains("\\App\\View\\Helper\\ArtistFormatterHelper"))
    }

    fun `test aliased helper in a helper resolves through the helpers array`() {
        configureConfiguredHelper("\$this->Ali<caret>as;")
        assertEquals(setOf("\\App\\View\\Helper\\ArtistFormatterHelper"), helperTypesAtCaret())
    }

    fun `test plain helper in a helper resolves by name`() {
        configureConfiguredHelper("\$this->MovieForm<caret>atter;")
        assertEquals(setOf("\\App\\View\\Helper\\MovieFormatterHelper"), helperTypesAtCaret())
    }

    fun `test helper completion in a template lists the loaded aliases first and keeps convention`() {
        configureTemplate("\$this-><caret>")
        val result = completions()
        for (alias in listOf("Html", "Fmt", "Shared", "ArtistFormatter")) {
            assertTrue("Expected $alias in $result", result.contains(alias))
        }
        assertEquals(1, result.count { it == "Shared" })

        val element = myFixture.lookupElements!!.find { it.lookupString == "Fmt" }
        val presentation = LookupElementPresentation()
        element!!.renderElement(presentation)
        assertEquals("App\\View\\Helper\\MovieFormatterHelper", presentation.typeText)
    }

    fun `test helper completion in a helper lists only the helpers array`() {
        configureConfiguredHelper("return \$this-><caret>;")
        val result = completions()
        assertTrue(result.contains("MovieFormatter"))
        assertTrue(result.contains("Alias"))
        assertFalse(result.contains("Shared"))
        assertFalse(result.contains("ArtistFormatter"))
    }

    fun `test goto declaration on an aliased helper in a template`() {
        configureTemplate("\$this->Fm<caret>t;")
        val targets = gotoDeclarationHandlerTargets(ViewHelperGotoDeclarationHandler())
        assertNotNull(targets)
        assertEquals(listOf("\\App\\View\\Helper\\MovieFormatterHelper"), targets!!.map { (it as PhpClass).fqn })
    }

    fun `test goto declaration on an aliased helper in a helper`() {
        configureConfiguredHelper("\$this->Ali<caret>as;")
        val targets = gotoDeclarationHandlerTargets(ViewHelperGotoDeclarationHandler())
        assertNotNull(targets)
        assertEquals(listOf("\\App\\View\\Helper\\ArtistFormatterHelper"), targets!!.map { (it as PhpClass).fqn })
    }

    fun `test union receiver resolves each helper against its own helpers array`() {
        myFixture.configureByText("UnionHelper.php", """
        <?php
        namespace App\View\Helper;

        class UnionHelper extends \Cake\View\Helper
        {
            /**
             * @param ConfiguredHelper|MovieFormatterHelper ${'$'}h
             */
            public function probe(${'$'}h) {
                ${'$'}h->Ali<caret>as;
            }
        }
        """.trimIndent())
        val types = helperTypesAtCaret()
        assertTrue(types.contains("\\App\\View\\Helper\\ArtistFormatterHelper"))
        // MovieFormatterHelper does not load Alias, so it keeps the convention candidates:
        assertTrue(types.contains("\\App\\View\\Helper\\AliasHelper"))
    }
}
