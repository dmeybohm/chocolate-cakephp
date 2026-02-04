package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.CakePhpAutoDetector
import com.daveme.chocolateCakePHP.PluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.findPluginConfigByName
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.HeavyPlatformTestCase

// Extend from HeavyPlatformTestCase for the per-project settings autodetection state:
class SettingsTest() : HeavyPlatformTestCase() {

    fun `test default constructed settings equals defaults property`() {
        val settings = Settings(project)
        val defaults = Settings.getDefaults(project)
        assertEquals(settings, defaults)
        assertEquals(settings.state, defaults.state)
    }

    fun `test getting settings service`() {
        assertNotNull(Settings.getInstance(this.project))
    }

    fun `test autodetect works when composer json is not present`() {
        val settings = Settings.getInstance(project)
        assertFalse(settings.cake3Enabled)
    }

    fun `test autodetect cache invalidates when composer json is created`() {
        val settings = Settings.getInstance(project)
        val autodetector = project.getService(CakePhpAutoDetector::class.java)

        // Initially, no CakePHP files exist
        assertFalse(settings.cake3Enabled)
        val initialValues = autodetector.autoDetectedValues
        assertFalse(initialValues.cake3OrLaterPresent)

        // Create composer.json with CakePHP dependency
        val projectDir = orCreateProjectBaseDir
        assertNotNull("Project directory should exist", projectDir)

        WriteAction.runAndWait<RuntimeException> {
            val composerJson = projectDir!!.createChildData(this, "composer.json")
            val composerContent = """
                {
                    "require": {
                        "cakephp/cakephp": "^4.0"
                    },
                    "autoload": {
                        "psr-4": {
                            "App\\": "src/"
                        }
                    }
                }
            """.trimIndent()
            composerJson.setBinaryContent(composerContent.toByteArray())
        }

        // Force a small delay to ensure VFS events are processed
        Thread.sleep(100)

        // Access autodetected values again - cache should be invalidated and values recalculated
        val newValues = autodetector.autoDetectedValues

        // Verify that CakePHP was detected
        assertTrue("CakePHP should be detected after composer.json creation", newValues.cake3OrLaterPresent)
        assertEquals("src", newValues.appDirectory)
    }

    fun `test findPluginConfigByName exact match has priority`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            PluginConfig(namespace = "\\Vendor\\Blog", pluginPath = "plugins/VendorBlog"),
            PluginConfig(namespace = "Blog", pluginPath = "plugins/Blog")
        )

        // Exact match should win even though it's second in the list
        val result = settings.findPluginConfigByName("Blog")
        assertNotNull(result)
        assertEquals("Blog", result!!.namespace)
        assertEquals("plugins/Blog", result.pluginPath)
    }

    fun `test findPluginConfigByName suffix match works`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            PluginConfig(namespace = "\\Vendor\\MyPlugin", pluginPath = "plugins/MyPlugin")
        )

        val result = settings.findPluginConfigByName("MyPlugin")
        assertNotNull(result)
        assertEquals("\\Vendor\\MyPlugin", result!!.namespace)
    }

    fun `test findPluginConfigByName multiple suffix matches returns first`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            PluginConfig(namespace = "\\Vendor\\Foo\\Blog", pluginPath = "plugins/FooBlog"),
            PluginConfig(namespace = "\\Vendor\\Bar\\Blog", pluginPath = "plugins/BarBlog")
        )

        // First configured plugin should win when multiple suffix matches exist
        val result = settings.findPluginConfigByName("Blog")
        assertNotNull(result)
        assertEquals("\\Vendor\\Foo\\Blog", result!!.namespace)
        assertEquals("plugins/FooBlog", result.pluginPath)
    }

    fun `test findPluginConfigByName returns null when no match`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            PluginConfig(namespace = "\\Vendor\\MyPlugin", pluginPath = "plugins/MyPlugin")
        )

        val result = settings.findPluginConfigByName("NonExistent")
        assertNull(result)
    }

}