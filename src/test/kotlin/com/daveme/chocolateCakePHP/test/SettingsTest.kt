package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.CakePhpAutoDetector
import com.daveme.chocolateCakePHP.PluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.effectivePluginName
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

    // ========== effectivePluginName() Tests ==========

    fun `test effectivePluginName returns pluginName when set`() {
        val config = PluginConfig(pluginName = "MyPlugin", namespace = "\\Vendor\\SomethingElse")
        assertEquals("MyPlugin", config.effectivePluginName())
    }

    fun `test effectivePluginName derives from namespace when pluginName is empty`() {
        val config = PluginConfig(namespace = "\\Vendor\\MyPlugin")
        assertEquals("MyPlugin", config.effectivePluginName())
    }

    fun `test effectivePluginName handles namespace without backslash prefix`() {
        val config = PluginConfig(namespace = "Vendor\\MyPlugin")
        assertEquals("MyPlugin", config.effectivePluginName())
    }

    fun `test effectivePluginName handles simple namespace`() {
        val config = PluginConfig(namespace = "MyPlugin")
        assertEquals("MyPlugin", config.effectivePluginName())
    }

    fun `test effectivePluginName handles empty namespace`() {
        val config = PluginConfig(namespace = "")
        assertEquals("", config.effectivePluginName())
    }

    // ========== findPluginConfigByName() Tests ==========

    fun `test findPluginConfigByName finds by pluginName`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            PluginConfig(pluginName = "Blog", namespace = "\\Vendor\\BlogPlugin", pluginPath = "plugins/Blog")
        )

        val result = settings.findPluginConfigByName("Blog")
        assertNotNull(result)
        assertEquals("Blog", result!!.pluginName)
        assertEquals("plugins/Blog", result.pluginPath)
    }

    fun `test findPluginConfigByName backwards compat derives from namespace`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            PluginConfig(namespace = "\\Vendor\\MyPlugin", pluginPath = "plugins/MyPlugin")
        )

        // Should find by deriving plugin name from namespace
        val result = settings.findPluginConfigByName("MyPlugin")
        assertNotNull(result)
        assertEquals("\\Vendor\\MyPlugin", result!!.namespace)
    }

    fun `test findPluginConfigByName backwards compat with simple namespace`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            PluginConfig(namespace = "Blog", pluginPath = "plugins/Blog")
        )

        val result = settings.findPluginConfigByName("Blog")
        assertNotNull(result)
        assertEquals("Blog", result!!.namespace)
        assertEquals("plugins/Blog", result.pluginPath)
    }

    fun `test findPluginConfigByName returns null when no match`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            PluginConfig(pluginName = "MyPlugin", pluginPath = "plugins/MyPlugin")
        )

        val result = settings.findPluginConfigByName("NonExistent")
        assertNull(result)
    }

    fun `test findPluginConfigByName pluginName takes precedence over namespace derivation`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            PluginConfig(pluginName = "DebugKit", namespace = "\\CakePHP\\DebugKit", pluginPath = "vendor/cakephp/debug_kit")
        )

        // Should find by explicit pluginName
        val result = settings.findPluginConfigByName("DebugKit")
        assertNotNull(result)
        assertEquals("DebugKit", result!!.pluginName)

        // Should NOT find by namespace-derived name since pluginName is set
        val resultByNamespace = settings.findPluginConfigByName("CakePHP\\DebugKit")
        assertNull(resultByNamespace)
    }

}