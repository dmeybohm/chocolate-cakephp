package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.test.ViewNavigation
import com.intellij.psi.PsiFile

class LayoutNavigationTest : Cake2BaseTestCase() {

    private val app = "cake2/app/View"
    private val blog = "cake2/app/Plugin/Blog/View"
    private val navigation by lazy { ViewNavigation(myFixture) }

    override fun setUpTestFiles() {
        myFixture.configureByFiles("cake2/app/Controller/AppController.php")
    }

    private fun template(path: String, code: String = "echo 1;"): PsiFile =
        myFixture.addFileToProject("$path.ctp", "<?php $code")

    private fun controller(path: String, name: String, members: String) {
        myFixture.addFileToProject("$path/${name}Controller.php", """
            <?php
            class ${name}Controller extends AppController {
                $members
            }
        """.trimIndent())
    }

    private fun appController(name: String, members: String) = controller("cake2/app/Controller", name, members)

    private fun blogController(name: String, members: String) = controller("cake2/app/Plugin/Blog/Controller", name, members)

    fun `test related items list every form naming the layout`() {
        appController("Articles", "public function index() { \$this->render('view', 'admin'); }")
        appController("Comments", "public function beforeFilter() { \$this->layout = 'admin'; }")
        appController("Tags", "public \$layout = 'admin';")
        val layout = template("$app/Layouts/admin")

        assertEquals(
            setOf(
                "Controllers: ArticlesController::index()",
                "Controllers: CommentsController::beforeFilter()",
                "Controllers: TagsController",
            ),
            navigation.related(layout)
        )
    }

    fun `test toggle action opens the only controller naming the layout`() {
        appController("Articles", "public function index() { \$this->render('view', 'admin'); }")
        val layout = template("$app/Layouts/admin")

        assertEquals("ArticlesController.php", navigation.toggle(layout).openedFile)
    }

    fun `test toggle action reports a layout nothing names`() {
        val layout = template("$app/Layouts/admin")

        assertEquals("No related file found", navigation.toggle(layout).message)
    }

    fun `test views and elements naming the layout have their own groups`() {
        template("$app/Articles/index", "\$this->layout = 'admin';")
        template("$app/Elements/sidebar", "\$this->layout = 'admin';")
        val layout = template("$app/Layouts/admin")

        assertEquals(setOf("Views: index.ctp", "Elements: sidebar.ctp"), navigation.related(layout))
    }

    fun `test layout in a data view directory lists the references to its name`() {
        appController("Articles", "public function index() { \$this->layout = 'default'; }")
        val layout = template("$app/Layouts/json/default")

        assertEquals(setOf("Controllers: ArticlesController::index()"), navigation.related(layout))
    }

    fun `test plugin controller links to the plugin's layout when it exists`() {
        blogController("BlogPosts", "public function index() { \$this->layout = 'admin'; }")
        val appLayout = template("$app/Layouts/admin")
        val blogLayout = template("$blog/Layouts/admin")

        assertEquals(setOf("Controllers: BlogPostsController::index()"), navigation.related(blogLayout))
        assertEquals(emptySet<String>(), navigation.related(appLayout))
    }

    fun `test plugin controller falls back to the app's layout`() {
        blogController("BlogPosts", "public function index() { \$this->layout = 'admin'; }")
        val appLayout = template("$app/Layouts/admin")

        assertEquals(setOf("Controllers: BlogPostsController::index()"), navigation.related(appLayout))
    }

    fun `test app controller reaches a plugin layout through its prefix`() {
        appController("Articles", "public function index() { \$this->layout = 'Blog.admin'; }")
        appController("Pages", "public function index() { \$this->layout = 'admin'; }")
        val appLayout = template("$app/Layouts/admin")
        val blogLayout = template("$blog/Layouts/admin")

        assertEquals(setOf("Controllers: ArticlesController::index()"), navigation.related(blogLayout))
        assertEquals(setOf("Controllers: PagesController::index()"), navigation.related(appLayout))
    }
}
