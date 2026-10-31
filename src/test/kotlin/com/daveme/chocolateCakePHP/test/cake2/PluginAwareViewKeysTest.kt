package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.view.RenderingControllers
import com.daveme.chocolateCakePHP.view.viewFileOf
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndexService
import com.intellij.psi.PsiFile

class PluginAwareViewKeysTest : Cake2BaseTestCase() {

    private val app = "cake2/app/View"
    private val blog = "cake2/app/Plugin/Blog/View"
    private val settings get() = Settings.getInstance(project)

    override fun setUpTestFiles() {
        myFixture.configureByFiles("cake2/app/Controller/AppController.php")
    }

    private fun template(path: String, code: String = "echo 1;"): PsiFile =
        myFixture.addFileToProject("$path.ctp", "<?php $code")

    private fun controller(path: String, name: String, actions: String) {
        myFixture.addFileToProject("$path/${name}Controller.php", """
            <?php
            class ${name}Controller extends AppController {
                $actions
            }
        """.trimIndent())
    }

    private fun viewFile(file: PsiFile) = viewFileOf(project, settings, file.virtualFile)!!

    private fun variables(file: PsiFile): Set<String> =
        ViewVariableIndexService.lookupVariablesFromViewPathInSmartReadAction(project, settings, viewFile(file)).keys

    private fun appAndPluginControllers() {
        controller("cake2/app/Controller", "Articles", "public function index() { \$this->set('appVar', 1); }")
        controller("cake2/app/Plugin/Blog/Controller", "BlogArticles", "public function index() { \$this->set('blogVar', 1); }")
    }

    fun `test controllers of the same name keep their variables apart`() {
        controller("cake2/app/Controller", "Articles", "public function index() { \$this->set('appVar', 1); }")
        controller("cake2/app/Plugin/Blog/Controller", "Articles", "public function index() { \$this->set('blogVar', 1); }")
        val appTemplate = template("$app/Articles/index")
        val blogTemplate = template("$blog/Articles/index")
        assertEquals(setOf("appVar"), variables(appTemplate))
        assertEquals(setOf("blogVar"), variables(blogTemplate))
    }

    fun `test plugin's element reached through a layout gets only the plugin's controllers`() {
        appAndPluginControllers()
        template("$app/Layouts/default", "\$this->element('shared');")
        val appElement = template("$app/Elements/shared")
        val blogElement = template("$blog/Elements/shared")

        val blogControllers = RenderingControllers.of(project, viewFile(blogElement))
        assertEquals(listOf("\\BlogArticlesController"), blogControllers)
        val appControllers = RenderingControllers.of(project, viewFile(appElement))
        assertTrue(appControllers.contains("\\ArticlesController"))
        assertFalse(appControllers.contains("\\BlogArticlesController"))
    }

    fun `test layout itself gets all controllers`() {
        appAndPluginControllers()
        val layout = template("$app/Layouts/default")
        val controllers = RenderingControllers.of(project, viewFile(layout))
        assertTrue(controllers.containsAll(listOf("\\ArticlesController", "\\BlogArticlesController")))
    }

    fun `test element in a fallback template follows the rendering plugin controller`() {
        controller("cake2/app/Plugin/Blog/Controller", "Articles", "public function index() {}")
        template("$app/Articles/index", "\$this->element('shared');")
        val appElement = template("$app/Elements/shared")
        val blogElement = template("$blog/Elements/shared")

        assertEquals(emptyList<String>(), RenderingControllers.of(project, viewFile(appElement)))
        assertEquals(listOf("\\ArticlesController"), RenderingControllers.of(project, viewFile(blogElement)))
    }
}
