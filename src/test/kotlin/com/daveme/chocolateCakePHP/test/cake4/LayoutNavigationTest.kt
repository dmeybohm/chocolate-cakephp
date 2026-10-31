package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.ViewNavigation
import com.daveme.chocolateCakePHP.view.viewFileOf
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndexService
import com.intellij.psi.PsiFile

class LayoutNavigationTest : Cake4BaseTestCase() {

    private val app = "cake4/templates"
    private val blog = "cake4/plugins/Blog/templates"
    private val layouts = "layout"
    private val cells = "cell"
    private val elements = "element"
    private val navigation by lazy { ViewNavigation(myFixture) }

    override fun setUpTestFiles() {
        myFixture.configureByFiles("cake4/src4/Controller/AppController.php")
    }

    private fun template(path: String, code: String = "echo 1;"): PsiFile =
        myFixture.addFileToProject("$path.php", "<?php $code")

    private fun controller(path: String, name: String, members: String) {
        myFixture.addFileToProject("$path/${name}Controller.php", """
            <?php
            namespace App\Controller;

            class ${name}Controller extends AppController {
                $members
            }
        """.trimIndent())
    }

    private fun appController(name: String, members: String) = controller("cake4/src4/Controller", name, members)

    private fun blogController(name: String, members: String) = controller("cake4/plugins/Blog/src/Controller", name, members)

    private fun variables(file: PsiFile): Set<String> {
        val settings = Settings.getInstance(project)
        val viewFile = viewFileOf(project, settings, file.virtualFile)!!
        return ViewVariableIndexService.lookupVariablesFromViewPathInSmartReadAction(project, settings, viewFile).keys
    }

    fun `test related items list the controller forms naming the layout`() {
        appController("Articles", "public function index() { \$this->viewBuilder()->setLayout('admin'); }")
        appController("Comments", "public function beforeRender(\$event) { \$this->viewBuilder()->setLayout('admin'); }")
        appController("Tags", "public function index() { \$this->render('view', 'admin'); }")
        val layout = template("$app/$layouts/admin")

        assertEquals(
            setOf(
                "Controllers: ArticlesController::index()",
                "Controllers: CommentsController::beforeRender()",
                "Controllers: TagsController::index()",
            ),
            navigation.related(layout)
        )
    }

    fun `test setTemplate chained after setLayout links both`() {
        appController("Articles", "public function custom() { \$this->viewBuilder()->setLayout('admin')->setTemplate('other'); }")
        val layout = template("$app/$layouts/admin")
        val other = template("$app/Articles/other")

        assertEquals(setOf("Controllers: ArticlesController::custom()"), navigation.related(layout))
        assertTrue(navigation.related(other).contains("Controllers: ArticlesController::custom()"))
    }

    fun `test setTemplatePath chained after setLayout keeps its path`() {
        appController("Articles", "public function custom() { \$this->viewBuilder()->setLayout('admin')->setTemplatePath('Shared')->setTemplate('other'); }")
        val layout = template("$app/$layouts/admin")
        val other = template("$app/Shared/other")

        assertEquals(setOf("Controllers: ArticlesController::custom()"), navigation.related(layout))
        assertEquals(setOf("Controllers: ArticlesController::custom()"), navigation.related(other))
    }

    fun `test templates naming the layout are listed under views`() {
        template("$app/Articles/index", "\$this->setLayout('admin');")
        template("$app/Articles/view", "\$this->layout = 'admin';")
        template("$app/$elements/sidebar", "\$this->setLayout('admin');")
        val layout = template("$app/$layouts/admin")

        assertEquals(
            setOf("Views: index.php", "Views: view.php", "Elements: sidebar.php"),
            navigation.related(layout)
        )
    }

    fun `test toggle action opens the only controller naming the layout`() {
        appController("Articles", "public function index() { \$this->viewBuilder()->setLayout('admin'); }")
        val layout = template("$app/$layouts/admin")

        assertEquals("ArticlesController.php", navigation.toggle(layout).openedFile)
    }

    fun `test toggle action reports a layout nothing names`() {
        val layout = template("$app/$layouts/admin")

        assertEquals("No related file found", navigation.toggle(layout).message)
    }

    fun `test plugin controller links to the plugin's layout when it exists`() {
        blogController("BlogPosts", "public function index() { \$this->viewBuilder()->setLayout('admin'); }")
        val appLayout = template("$app/$layouts/admin")
        val blogLayout = template("$blog/$layouts/admin")

        assertEquals(setOf("Controllers: BlogPostsController::index()"), navigation.related(blogLayout))
        assertEquals(emptySet<String>(), navigation.related(appLayout))
    }

    fun `test plugin controller falls back to the app's layout`() {
        blogController("BlogPosts", "public function index() { \$this->viewBuilder()->setLayout('admin'); }")
        val appLayout = template("$app/$layouts/admin")

        assertEquals(setOf("Controllers: BlogPostsController::index()"), navigation.related(appLayout))
    }

    fun `test app controller reaches a plugin layout through its prefix`() {
        appController("Articles", "public function index() { \$this->viewBuilder()->setLayout('Blog.admin'); }")
        template("$app/$layouts/admin")
        val blogLayout = template("$blog/$layouts/admin")

        assertEquals(setOf("Controllers: ArticlesController::index()"), navigation.related(blogLayout))
    }

    fun `test receivers other than this name no layout`() {
        appController("Articles", """
            public function index(${'$'}email, ${'$'}view) {
                ${'$'}email->viewBuilder()->setLayout('admin');
                ${'$'}view->render('x', 'admin');
            }
        """.trimIndent())
        template("$app/Articles/other", "\$other->setLayout('admin');")
        val layout = template("$app/$layouts/admin")

        assertEquals(emptySet<String>(), navigation.related(layout))
    }

    fun `test cells name no layout`() {
        myFixture.addFileToProject("cake4/src4/View/Cell/InventoryCell.php", """
            <?php
            namespace App\View\Cell;

            class InventoryCell extends \Cake\View\Cell {
                public function display() { ${'$'}this->viewBuilder()->setLayout('admin'); }
            }
        """.trimIndent())
        template("$app/$cells/Inventory/display", "\$this->setLayout('admin');")
        val layout = template("$app/$layouts/admin")

        assertEquals(emptySet<String>(), navigation.related(layout))
    }

    fun `test layout does not offer the variables of an action naming it`() {
        appController("Articles", """
            public function index() {
                ${'$'}this->viewBuilder()->setLayout('admin');
                ${'$'}this->set('actionVar', 1);
            }
        """.trimIndent())
        val layout = template("$app/$layouts/admin", "echo \$this->element('shared', ['passed' => 1]);")
        val element = template("$app/$elements/shared")

        assertEquals(emptySet<String>(), variables(layout))
        assertEquals(setOf("passed"), variables(element))
    }

    fun `test forms from older versions name no layout`() {
        appController("Articles", """
            public ${'$'}layout = 'admin';
            public function index() {
                ${'$'}this->layout = 'admin';
                ${'$'}this->viewBuilder()->layout('admin');
            }
        """.trimIndent())
        template("$app/Articles/index", "\$this->layout('admin');")
        val layout = template("$app/$layouts/admin")

        assertEquals(emptySet<String>(), navigation.related(layout))
    }

    fun `test layout property assigned in a template still names the layout`() {
        template("$app/Articles/index", "\$this->layout = 'admin';")
        val layout = template("$app/$layouts/admin")

        assertEquals(setOf("Views: index.php"), navigation.related(layout))
    }

}
