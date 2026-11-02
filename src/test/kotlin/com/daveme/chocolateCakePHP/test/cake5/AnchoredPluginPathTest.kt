package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isInConfiguredPlugin
import com.daveme.chocolateCakePHP.view.viewFileOf
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile

class AnchoredPluginPathTest : Cake5BaseTestCase() {

    private val settings get() = Settings.getInstance(project)
    private val projectDir get() = project.guessProjectDir()

    override fun setUpTestFiles() {
        myFixture.addFileToProject("cake5/src5/Application.php", "<?php namespace App; class Application {}")
        val state = settings.state.copy()
        state.pluginConfigs = listOf(
            CakeThreePluginConfig(pluginName = "Blog", namespace = "\\Acme\\Blog", pluginPath = "vendor/acme/blog")
        )
        settings.loadState(state)
    }

    private fun template(pluginDir: String): VirtualFile =
        myFixture.addFileToProject("$pluginDir/templates/Posts/index.php", "<?php echo 1;").virtualFile

    fun `test configured plugin under the app root is recognised`() {
        val template = template("cake5/vendor/acme/blog")
        assertEquals("Blog", viewFileOf(project, settings, template)?.plugin)
        assertTrue(isInConfiguredPlugin(template, projectDir, settings))
    }

    fun `test copy of the configured path outside any app root is not the plugin`() {
        val template = template("examples/vendor/acme/blog")
        assertNull(viewFileOf(project, settings, template)?.plugin)
        assertFalse(isInConfiguredPlugin(template, projectDir, settings))
    }

    fun `test copy in another plugin's vendor directory is not the plugin`() {
        myFixture.addFileToProject("cake5/plugins/Foo/src/FooPlugin.php", "<?php namespace Foo; class FooPlugin {}")
        val template = template("cake5/plugins/Foo/vendor/acme/blog")
        assertFalse("Blog" == viewFileOf(project, settings, template)?.plugin)
        assertFalse(isInConfiguredPlugin(template, projectDir, settings))
    }
}
