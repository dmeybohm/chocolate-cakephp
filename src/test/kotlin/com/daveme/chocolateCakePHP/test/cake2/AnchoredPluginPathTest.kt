package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.CakeTwoPluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.cakeTwoPluginName
import com.daveme.chocolateCakePHP.cake.isInConfiguredPlugin
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.view.viewFileOf
import com.daveme.chocolateCakePHP.view.viewfileindex.lookupControllerFileInfo
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile

class AnchoredPluginPathTest : Cake2BaseTestCase() {

    private val settings get() = Settings.getInstance(project)
    private val projectDir get() = project.guessProjectDir()

    override fun setUpTestFiles() {
        myFixture.configureByFiles("cake2/app/Controller/AppController.php")
        configure("lib/Shop")
    }

    private fun configure(pluginPath: String) {
        val state = settings.state.copy()
        state.pluginConfigs = listOf(CakeTwoPluginConfig(pluginName = "Shop", pluginPath = pluginPath))
        settings.loadState(state)
    }

    private fun view(pluginDir: String): VirtualFile =
        myFixture.addFileToProject("$pluginDir/View/Posts/index.ctp", "<?php echo 1;").virtualFile

    private fun controller(pluginDir: String): VirtualFile =
        myFixture.addFileToProject("$pluginDir/Controller/PostsController.php", """
            <?php
            class PostsController extends AppController {}
        """.trimIndent()).virtualFile

    private fun assertRecognised(pluginDir: String) {
        val view = view(pluginDir)
        assertNotNull(templatesDirectoryOfViewFile(project, settings, view))
        assertEquals("Shop", viewFileOf(project, settings, view)?.plugin)
        assertTrue(isInConfiguredPlugin(view, projectDir, settings))
    }

    fun `test configured plugin under the app root is recognised`() {
        assertRecognised("cake2/lib/Shop")
        assertTrue(lookupControllerFileInfo(controller("cake2/lib/Shop"), projectDir, settings).isCakeTwo)
    }

    fun `test copy of the configured path outside any app root is not the plugin`() {
        val view = view("examples/lib/Shop")
        val controller = controller("examples/lib/Shop")

        assertNull(templatesDirectoryOfViewFile(project, settings, view))
        assertNull(cakeTwoPluginName(view, projectDir, settings))
        assertFalse(isInConfiguredPlugin(view, projectDir, settings))
        assertFalse(lookupControllerFileInfo(controller, projectDir, settings).isCakeTwo)
        assertFalse(isInConfiguredPlugin(controller, projectDir, settings))
    }

    fun `test configured path applies under every app root`() {
        myFixture.addFileToProject("examples/app/Controller/AppController.php", "<?php class AppController {}")
        assertRecognised("examples/lib/Shop")
    }

    fun `test nested app directory setting anchors its configured plugin`() {
        val state = settings.state.copy()
        state.cake2AppDirectory = "src/app"
        settings.loadState(state)
        myFixture.addFileToProject("cake2/src/app/Controller/AppController.php", "<?php class AppController {}")
        assertRecognised("cake2/lib/Shop")
    }

    fun `test path entered relative to the project root is still recognised`() {
        configure("cake2/lib/Shop")
        assertRecognised("cake2/lib/Shop")
    }
}
