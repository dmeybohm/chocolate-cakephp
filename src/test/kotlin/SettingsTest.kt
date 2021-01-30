import com.daveme.chocolateCakePHP.Settings
import junit.framework.TestCase

class SettingsTest : TestCase() {

    fun `test settings equals defaults`() {
        val settings = Settings()
        val defaults = Settings.defaults
        assertEquals(settings, defaults)
        assertEquals(settings.state, defaults.state)
    }

}