package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Settings
import org.junit.Test

public class SettingsTest() : BaseTestCase() {

    @Test
    public fun `test default constructed settings equals defaults property`() {
        val settings = Settings()
        val defaults = Settings.defaults
        assertEquals(settings, defaults)
        assertEquals(settings.state, defaults.state)
    }

    @Test
    public fun `test getting settings service`() {
        assertNotNull(Settings.getInstance(this.project))
    }

}