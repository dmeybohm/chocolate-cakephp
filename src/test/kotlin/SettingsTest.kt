package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Settings

class SettingsTest : BaseTestCase() {

    fun `test default constructed settings equals defaults property`() {
        val settings = Settings()
        val defaults = Settings.defaults
        assertEquals(settings, defaults)
        assertEquals(settings.state, defaults.state)
    }

    fun `test getting settings service`() {
        assertNotNull(Settings.getInstance(this.project))
    }


}