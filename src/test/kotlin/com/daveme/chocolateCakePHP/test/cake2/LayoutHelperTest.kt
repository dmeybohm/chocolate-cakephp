package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference

class LayoutHelperTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles("cake2/vendor/cakephp.php")
        myFixture.addFileToProject("cake2/app/View/Helper/Helpers.php", """
            <?php
            class AppHelper extends Helper {}
            class HtmlHelper extends AppHelper {}
            class BootstrapHtmlHelper extends HtmlHelper {}
            class ArtistHtmlHelper extends HtmlHelper {}
            class UsersHtmlHelper extends HtmlHelper {}
            class GroupsHtmlHelper extends HtmlHelper {}
            class FooHtmlHelper extends HtmlHelper {}
            class AdminHtmlHelper extends HtmlHelper {}
            class OrphanHtmlHelper extends HtmlHelper {}
            class PostsHtmlHelper extends HtmlHelper {}
            class AdminPostsHtmlHelper extends HtmlHelper {}
            class ReportsHtmlHelper extends HtmlHelper {}
            class MonthlyReportsHtmlHelper extends HtmlHelper {}
        """.trimIndent())
        // A public method with no template does not make a base class render pages:
        addController("AppController", "Controller", "public function isAuthorized(\$user) { return true; }")
        addController("MoviesController", "AppController", """
            public ${'$'}helpers = array('Html' => array('className' => 'BootstrapHtml'));
            public function index() {}
            public function view() {}
            public function admin() {
                ${'$'}this->layout = 'admin';
            }
            public function printable() {
                ${'$'}this->render('index', 'print');
            }
            public function promote() {
                ${'$'}this->layout = 'Foo.promo';
            }
        """)
        addController("ArtistsController", "AppController", """
            public ${'$'}helpers = array('Html' => array('className' => 'ArtistHtml'));
            public ${'$'}layout = 'artists';
        """)
        addController("BackendController", "AppController", """
            public ${'$'}layout = 'backend';
            public function isAuthorized(${'$'}user) { return true; }
        """)
        addController("UsersController", "BackendController", """
            public ${'$'}helpers = array('Html' => array('className' => 'UsersHtml'));
        """)
        addController("GroupsController", "BackendController", """
            public ${'$'}helpers = array('Html' => array('className' => 'GroupsHtml'));
            public ${'$'}layout = 'groups';
        """)
        myFixture.addFileToProject("cake2/app/Controller/AdminBaseController.php", """
            <?php
            abstract class AdminBaseController extends AppController {
                public ${'$'}helpers = array('Html' => array('className' => 'AdminHtml'));
                public ${'$'}layout = 'admin_area';
            }
        """.trimIndent())
        addController("AccountsController", "AdminBaseController", "public function index() {}")
        myFixture.addFileToProject("cake2/app/Controller/OrphanBaseController.php", """
            <?php
            abstract class OrphanBaseController extends AppController {
                public ${'$'}helpers = array('Html' => array('className' => 'OrphanHtml'));
                public ${'$'}layout = 'orphan_area';
            }
        """.trimIndent())
        addController("PostsController", "AppController", """
            public ${'$'}helpers = array('Html' => array('className' => 'PostsHtml'));
            public ${'$'}layout = 'posts';
            public function index() {}
        """)
        addController("AdminPostsController", "PostsController", """
            public ${'$'}helpers = array('Html' => array('className' => 'AdminPostsHtml'));
        """)
        myFixture.addFileToProject("cake2/app/View/Posts/index.ctp", "<?php\n")
        addController("ReportsController", "AppController", """
            public ${'$'}helpers = array('Html' => array('className' => 'ReportsHtml'));
            public ${'$'}layout = 'reports';
            public function export() {
                ${'$'}this->render('summary');
            }
        """)
        addController("MonthlyReportsController", "ReportsController", """
            public ${'$'}helpers = array('Html' => array('className' => 'MonthlyReportsHtml'));
        """)
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
        myFixture.addFileToProject(
            "cake2/app/View/Movies/view.ctp",
            "<?php \$this->layout = 'print_view'; echo \$this->element('switcher');\n"
        )
        myFixture.addFileToProject("cake2/app/View/Elements/switcher.ctp", "<?php \$this->layout = 'switched';\n")
        // The plugin's own default layout is the one its controllers render:
        myFixture.addFileToProject("cake2/app/Plugin/Foo/View/Layouts/default.ctp", "<?php\n")
    }

    private fun addController(name: String, parent: String, body: String) {
        myFixture.addFileToProject("cake2/app/Controller/$name.php", "<?php\nclass $name extends $parent {\n$body\n}\n")
    }

    private fun defaultLayoutHtml(): Set<String> =
        if (usesRealVendor) setOf("\\BootstrapHtmlHelper", "\\HtmlHelper") else setOf("\\BootstrapHtmlHelper")

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

    fun `test a layout assigned in an action gets that controller's helpers`() {
        assertEquals(setOf("\\BootstrapHtmlHelper"), htmlTypeIn("cake2/app/View/Layouts/admin.ctp"))
    }

    fun `test a layout passed to render gets that controller's helpers`() {
        assertEquals(setOf("\\BootstrapHtmlHelper"), htmlTypeIn("cake2/app/View/Layouts/print.ctp"))
    }

    fun `test a declared layout gets the declaring controller's helpers`() {
        assertEquals(setOf("\\ArtistHtmlHelper"), htmlTypeIn("cake2/app/View/Layouts/artists.ctp"))
    }

    fun `test an inherited layout declaration applies to subclasses that do not redeclare it`() {
        // BackendController is their base, so it does not render the layout itself:
        assertEquals(setOf("\\UsersHtmlHelper"), htmlTypeIn("cake2/app/View/Layouts/backend.ctp"))
    }

    fun `test an abstract base's helpers and layout reach the controllers extending it`() {
        assertEquals(setOf("\\AdminHtmlHelper"), htmlTypeIn("cake2/app/View/Layouts/admin_area.ctp"))
    }

    fun `test a layout only an abstract class names falls back to AppController`() {
        assertEquals(setOf("\\HtmlHelper"), htmlTypeIn("cake2/app/View/Layouts/orphan_area.ctp"))
    }

    fun `test an extended controller with a template of its own renders its layout`() {
        assertEquals(
            setOf("\\PostsHtmlHelper", "\\AdminPostsHtmlHelper"),
            htmlTypeIn("cake2/app/View/Layouts/posts.ctp")
        )
    }

    fun `test an extended controller with a render call of its own renders its layout`() {
        assertEquals(
            setOf("\\ReportsHtmlHelper", "\\MonthlyReportsHtmlHelper"),
            htmlTypeIn("cake2/app/View/Layouts/reports.ctp")
        )
    }

    fun `test the default layout gets the controllers that render it`() {
        // MoviesController; not the base classes, nor the controllers that render other layouts.
        // Real CakePHP's CakeErrorController renders error pages in the default layout too:
        assertEquals(defaultLayoutHtml(), htmlTypeIn("cake2/app/View/Layouts/default.ctp"))
    }

    fun `test the default layout unions a controller without the alias`() {
        addController("PagesController", "AppController", "public function display() {}")
        assertEquals(setOf("\\BootstrapHtmlHelper", "\\HtmlHelper"), htmlTypeIn("cake2/app/View/Layouts/default.ctp"))
    }

    fun `test a data view layout maps onto the plain layout`() {
        assertEquals(defaultLayoutHtml(), htmlTypeIn("cake2/app/View/Layouts/json/default.ctp"))
    }

    fun `test a layout in another subdirectory falls back to AppController`() {
        assertEquals(setOf("\\HtmlHelper"), htmlTypeIn("cake2/app/View/Layouts/other/stray.ctp"))
    }

    fun `test a layout assigned in a template or an element gets the template's controller`() {
        assertEquals(setOf("\\BootstrapHtmlHelper"), htmlTypeIn("cake2/app/View/Layouts/print_view.ctp"))
        assertEquals(setOf("\\BootstrapHtmlHelper"), htmlTypeIn("cake2/app/View/Layouts/switched.ctp"))
    }

    fun `test a plugin's default layout gets the plugin's controllers`() {
        assertEquals(setOf("\\FooHtmlHelper"), htmlTypeIn("cake2/app/Plugin/Foo/View/Layouts/default.ctp"))
    }

    fun `test a plugin prefixed layout gets the app controller that names it`() {
        assertEquals(setOf("\\BootstrapHtmlHelper"), htmlTypeIn("cake2/app/Plugin/Foo/View/Layouts/promo.ctp"))
    }
}
