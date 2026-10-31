package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

class LayoutHelperTest : Cake2BaseTestCase() {

    private val everyController = setOf(
        "\\HtmlHelper",
        "\\BootstrapHtmlHelper",
        "\\ArtistHtmlHelper",
        "\\AdminHtmlHelper",
        "\\FooHtmlHelper",
    )

    override fun setUpTestFiles() {
        myFixture.addFileToProject("cake2/app/View/Helper/Helpers.php", """
            <?php
            class AppHelper extends Helper {}
            class HtmlHelper extends AppHelper {}
            class BootstrapHtmlHelper extends HtmlHelper {}
            class ArtistHtmlHelper extends HtmlHelper {}
            class FooHtmlHelper extends HtmlHelper {}
            class AdminHtmlHelper extends HtmlHelper {}
        """.trimIndent())
        addController("AppController", "Controller", "")
        addController("MoviesController", "AppController", """
            public ${'$'}helpers = array('Html' => array('className' => 'BootstrapHtml'));
            public function index() {}
            public function view() {}
            public function admin() {
                ${'$'}this->layout = 'admin';
            }
            public function promote() {
                ${'$'}this->layout = 'Foo.promo';
            }
        """)
        addController("ArtistsController", "AppController", """
            public ${'$'}helpers = array('Html' => array('className' => 'ArtistHtml'));
            public ${'$'}layout = 'artists';
        """)
        myFixture.addFileToProject("cake2/app/Controller/AdminBaseController.php", """
            <?php
            abstract class AdminBaseController extends AppController {
                public ${'$'}helpers = array('Html' => array('className' => 'AdminHtml'));
                public ${'$'}layout = 'admin_area';
            }
        """.trimIndent())
        myFixture.addFileToProject("cake2/app/Plugin/Foo/Controller/FooAppController.php", """
            <?php
            class FooAppController extends AppController {}
        """.trimIndent())
        myFixture.addFileToProject("cake2/app/Plugin/Foo/Controller/BarsController.php", """
            <?php
            class BarsController extends FooAppController {
                public ${'$'}helpers = array('Html' => array('className' => 'FooHtml'));
                public function index() {}
            }
        """.trimIndent())
        // Element references are only indexed once the element directory exists:
        myFixture.addFileToProject("cake2/app/View/Elements/empty.ctp", "")
        myFixture.addFileToProject("cake2/app/View/Movies/view.ctp", "<?php echo \$this->element('movie_nav'); echo \$this->element('shared_nav');\n")
        myFixture.addFileToProject("cake2/app/View/Layouts/artists.ctp", "<?php echo \$this->element('artist_nav'); echo \$this->element('shared_nav');\n")
        myFixture.addFileToProject("cake2/app/View/Layouts/default.ctp", "<?php echo \$this->element('site_nav');\n")
    }

    private fun addController(name: String, parent: String, body: String) {
        myFixture.addFileToProject("cake2/app/Controller/$name.php", "<?php\nclass $name extends $parent {\n$body\n}\n")
    }

    private fun htmlTypeIn(path: String): Set<String> {
        val code = "<?php\n\$this->Html;\n"
        val existing = myFixture.findFileInTempDir(path)
        if (existing != null) {
            WriteAction.runAndWait<Throwable> { VfsUtil.saveText(existing, code) }
        } else {
            myFixture.addFileToProject(path, code)
        }
        myFixture.configureFromTempProjectFile(path)
        myFixture.editor.caretModel.moveToOffset(code.indexOf("Html") + 1)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java)
        assertNotNull(fieldReference)
        return fieldReference!!.type.lookupCompleteType(project, null).types
            .filter { it.startsWith("\\") }
            .toSet()
    }

    fun `test a named layout gets every controller`() {
        assertEquals(everyController, htmlTypeIn("cake2/app/View/Layouts/admin.ctp"))
    }

    fun `test the default layout gets every controller`() {
        assertEquals(everyController, htmlTypeIn("cake2/app/View/Layouts/default.ctp"))
    }

    fun `test a data view layout gets every controller`() {
        assertEquals(everyController, htmlTypeIn("cake2/app/View/Layouts/json/default.ctp"))
    }

    fun `test a layout in a subdirectory gets every controller`() {
        assertEquals(everyController, htmlTypeIn("cake2/app/View/Layouts/other/stray.ctp"))
    }

    fun `test an element in a layout gets every controller`() {
        assertEquals(everyController, htmlTypeIn("cake2/app/View/Elements/artist_nav.ctp"))
        assertEquals(everyController, htmlTypeIn("cake2/app/View/Elements/site_nav.ctp"))
    }

    fun `test an element in a layout and a template gets every controller`() {
        assertEquals(everyController, htmlTypeIn("cake2/app/View/Elements/shared_nav.ctp"))
    }

    fun `test a template gets only its controller's helpers`() {
        assertEquals(setOf("\\BootstrapHtmlHelper"), htmlTypeIn("cake2/app/View/Movies/index.ctp"))
    }

    fun `test an element only a template includes gets only that controller's helpers`() {
        assertEquals(setOf("\\BootstrapHtmlHelper"), htmlTypeIn("cake2/app/View/Elements/movie_nav.ctp"))
    }

    fun `test a plugin's layouts get every controller`() {
        assertEquals(everyController, htmlTypeIn("cake2/app/Plugin/Foo/View/Layouts/default.ctp"))
        assertEquals(everyController, htmlTypeIn("cake2/app/Plugin/Foo/View/Layouts/promo.ctp"))
    }

    fun `test an app override of a plugin layout gets every controller`() {
        assertEquals(everyController, htmlTypeIn("cake2/app/View/Plugin/Foo/Layouts/default.ctp"))
    }

    fun `test a plugin controller's template gets only its helpers`() {
        assertEquals(setOf("\\FooHtmlHelper"), htmlTypeIn("cake2/app/Plugin/Foo/View/Bars/index.ctp"))
    }
}
