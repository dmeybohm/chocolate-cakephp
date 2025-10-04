package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.CakePhpAutoDetector
import com.daveme.chocolateCakePHP.Settings
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

}