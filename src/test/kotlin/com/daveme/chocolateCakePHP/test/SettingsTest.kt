package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Settings
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

}