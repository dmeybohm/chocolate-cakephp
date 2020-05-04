import com.daveme.chocolateCakePHP.Settings
import org.junit.jupiter.api.Assertions.*

internal class SettingsTest {

    @org.junit.jupiter.api.Test
    fun settingsEqualsDefaults() {
        val settings = Settings()
        val defaults = Settings.defaults
        assertEquals(settings, defaults)
        assertEquals(settings.state, defaults.state)
    }

}