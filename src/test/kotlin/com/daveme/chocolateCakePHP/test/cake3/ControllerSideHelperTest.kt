package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

class ControllerSideHelperTest : Cake3BaseTestCase() {

    private val movieHelper = "\\App\\View\\Helper\\MovieFormatterHelper"
    private val artistHelper = "\\App\\View\\Helper\\ArtistFormatterHelper"
    private val conventionHelper = "\\App\\View\\Helper\\FmtHelper"

    override fun setUpTestFiles() {
        myFixture.addFileToProject("cake3/src/View/Helper/MovieFormatterHelper.php", """
            <?php
            namespace App\View\Helper;
            class MovieFormatterHelper extends \Cake\View\Helper {}
            class ArtistFormatterHelper extends \Cake\View\Helper {}
        """.trimIndent())
    }

    private fun controller(body: String, name: String = "Movie", parent: String = "\\Cake\\Controller\\Controller") {
        myFixture.addFileToProject("cake3/src/Controller/${name}Controller.php", """
            <?php
            namespace App\Controller;
            class ${name}Controller extends $parent {
                $body
                public function index() {}
            }
        """.trimIndent())
    }

    private fun fmtTypesIn(path: String): Set<String> {
        val code = "<?php\n\$this->Fmt;\n"
        myFixture.addFileToProject(path, code)
        myFixture.configureFromTempProjectFile(path)
        myFixture.editor.caretModel.moveToOffset(code.indexOf("Fmt") + 1)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    fun `test the deprecated controller helpers property resolves in the template and a layout`() {
        controller("public ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];")
        assertEquals(setOf(movieHelper), fmtTypesIn("cake3/src/Template/Movie/index.ctp"))
        assertTrue(fmtTypesIn("cake3/src/Template/Layout/default.ctp").contains(movieHelper))
    }

    fun `test a helpers property on AppController settles the type in a subclass's template`() {
        controller("public ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];", name = "App")
        controller("", parent = "AppController")
        assertEquals(setOf(movieHelper), fmtTypesIn("cake3/src/Template/Movie/index.ctp"))
    }

    fun `test a layout unions the aliases without the convention type when every project controller settles it`() {
        controller("public ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];")
        controller("public ${'$'}helpers = ['Fmt' => ['className' => 'ArtistFormatter']];", name = "Artist")
        assertEquals(setOf(movieHelper, artistHelper), fmtTypesIn("cake3/src/Template/Layout/default.ctp"))
    }

    fun `test setHelpers on the view builder resolves in the template`() {
        controller("public function beforeRender(\\Cake\\Event\\Event ${'$'}event) { \$this->viewBuilder()->setHelpers(['Fmt' => ['className' => 'MovieFormatter']]); }")
        assertTrue(fmtTypesIn("cake3/src/Template/Movie/index.ctp").contains(movieHelper))
    }

    fun `test a helpers property on the view class is ignored`() {
        myFixture.addFileToProject("cake3/src/View/AppView.php", """
            <?php
            namespace App\View;
            class AppView extends \Cake\View\View {
                protected ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];
            }
        """.trimIndent())
        controller("")
        assertFalse(fmtTypesIn("cake3/src/Template/Movie/index.ctp").contains(movieHelper))
    }

    fun `test a helpers property on a cell resolves in its template`() {
        myFixture.addFileToProject("cake3/src/View/Cell/WidgetCell.php", """
            <?php
            namespace App\View\Cell;
            class WidgetCell extends \Cake\View\Cell {
                public ${'$'}helpers = ['Fmt' => ['className' => 'MovieFormatter']];
                public function display() {}
            }
        """.trimIndent())
        assertEquals(setOf(movieHelper), fmtTypesIn("cake3/src/Template/Cell/Widget/display.ctp"))
    }
}
