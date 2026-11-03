package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.ActionNames
import com.daveme.chocolateCakePHP.cake.actionNameFromPath
import com.daveme.chocolateCakePHP.cake.allTemplatePathsFromTopSourceDirectory
import com.daveme.chocolateCakePHP.cake.allViewPathsFromPluginTemplate
import com.daveme.chocolateCakePHP.cake.isInConfiguredPlugin
import com.daveme.chocolateCakePHP.cake.topSourceDirectoryFromSourceFile
import com.daveme.chocolateCakePHP.view.TemplateGotoDeclarationHandler
import com.daveme.chocolateCakePHP.view.viewFileOf
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

class AnchoredPluginPathTest : Cake5BaseTestCase() {

    private val settings get() = Settings.getInstance(project)

    private val blog = CakeThreePluginConfig(pluginName = "Blog", namespace = "\\Acme\\Blog", pluginPath = "vendor/acme/blog")

    override fun setUpTestFiles() {
        myFixture.addFileToProject("cake5/src5/Application.php", "<?php namespace App; class Application {}")
        configure(pluginNamespaces = listOf())
    }

    private fun configure(pluginNamespaces: List<String>) {
        val state = settings.state.copy()
        state.pluginConfigs = listOf(blog)
        state.pluginNamespaces = pluginNamespaces
        settings.loadState(state)
    }

    private fun template(pluginDir: String, name: String = "Posts/index"): VirtualFile =
        myFixture.addFileToProject("$pluginDir/templates/$name.php", "<?php echo 1;").virtualFile

    private fun allTemplatePaths(sourceFile: VirtualFile) = allTemplatePathsFromTopSourceDirectory(
        project, settings, topSourceDirectoryFromSourceFile(settings, sourceFile)!!
    )!!

    fun `test configured plugin under the root is recognised`() {
        val template = template("cake5/vendor/acme/blog")
        assertEquals("Blog", viewFileOf(project, settings, template)?.plugin)
        assertTrue(isInConfiguredPlugin(template, settings))
    }

    fun `test copy of the configured path outside the root is not the plugin`() {
        val template = template("examples/vendor/acme/blog")
        assertNull(viewFileOf(project, settings, template)?.plugin)
        assertFalse(isInConfiguredPlugin(template, settings))
    }

    fun `test copy in another plugin's vendor directory is not the plugin`() {
        myFixture.addFileToProject("cake5/plugins/Foo/src/FooPlugin.php", "<?php namespace Foo; class FooPlugin {}")
        val template = template("cake5/plugins/Foo/vendor/acme/blog")
        assertFalse("Blog" == viewFileOf(project, settings, template)?.plugin)
        assertFalse(isInConfiguredPlugin(template, settings))
    }

    fun `test copy in a package's vendor directory is not the plugin`() {
        myFixture.addFileToProject("cake5/packages/Parent/src/ParentPlugin.php", "<?php namespace Parent; class ParentPlugin {}")
        val template = template("cake5/packages/Parent/vendor/acme/blog")
        assertFalse("Blog" == viewFileOf(project, settings, template)?.plugin)
        assertFalse(isInConfiguredPlugin(template, settings))
    }

    fun `test path relative to the project directory is recognised without a composer json`() {
        val state = settings.state.copy()
        state.composerJsonPath = "missing/composer.json"
        state.pluginConfigs = listOf(blog.copy(pluginPath = "cake5/vendor/acme/blog"))
        settings.loadState(state)
        val template = template("cake5/vendor/acme/blog")
        assertEquals("Blog", viewFileOf(project, settings, template)?.plugin)
        assertTrue(isInConfiguredPlugin(template, settings))
    }

    fun `test blank configured path identifies no directory`() {
        configure(pluginNamespaces = listOf("\\Legacy"))
        val appTemplate = myFixture.addFileToProject("cake5/templates/Pages/home.php", "<?php echo 1;").virtualFile
        assertFalse(isInConfiguredPlugin(appTemplate, settings))
        assertNull(viewFileOf(project, settings, appTemplate)?.plugin)
    }

    fun `test blank configured path has no template paths`() {
        configure(pluginNamespaces = listOf("\\Legacy"))
        template("cake5/vendor/acme/blog")
        val appTemplate = myFixture.addFileToProject("cake5/templates/Pages/home.php", "<?php echo 1;").virtualFile
        val controller = myFixture.addFileToProject("cake5/src5/Controller/PagesController.php", "<?php").virtualFile
        val allTemplatePaths = allTemplatePaths(controller)

        val legacy = settings.pluginConfigs.single { it.pluginPath.isEmpty() }
        assertNull(allTemplatePaths.pluginTemplatePaths(legacy))
        assertFalse(allTemplatePaths.pluginAndThemeTemplatePaths.paths.any {
            it.templatesDir.directory == appTemplate.parent.parent
        })
        assertEquals(
            listOf("cake5/vendor/acme/blog/templates"),
            allTemplatePaths.pluginTemplatePaths(blog)?.paths?.map { it.templatesPath }
        )
    }

    fun `test navigation from an app controller reaches a configured plugin template`() {
        val blogTemplate = template("cake5/vendor/acme/blog")
        myFixture.addFileToProject("cake5/templates/Pages/home.php", "<?php echo 1;")
        myFixture.copyFileToProject("cake5/src5/Controller/AppController.php")
        val controller = myFixture.addFileToProject("cake5/src5/Controller/PagesController.php", """
        <?php
        namespace App\Controller;

        class PagesController extends AppController {
            public function show() {
                ${'$'}this->render('Blog.Posts/in<caret>dex');
            }
        }
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(controller.virtualFile)
        val targets = gotoDeclarationHandlerTargets(TemplateGotoDeclarationHandler())
            ?.mapNotNull { (it as? PsiFile)?.virtualFile }
        assertEquals(listOf(blogTemplate), targets)
    }

    fun `test path for creating a configured plugin view is project relative`() {
        template("cake5/vendor/acme/blog")
        myFixture.addFileToProject("cake5/templates/Pages/home.php", "<?php echo 1;")
        val controller = myFixture.addFileToProject("cake5/src5/Controller/PagesController.php", "<?php").virtualFile
        val allViewPaths = allViewPathsFromPluginTemplate(
            allTemplatePaths(controller),
            settings,
            ActionNames(defaultActionName = actionNameFromPath("Posts/missing")),
            blog,
            convertDefaultCase = false,
        )!!
        assertEquals("cake5/vendor/acme/blog/templates/Posts/missing.php", allViewPaths.defaultViewPath.fullPath)
    }
}
