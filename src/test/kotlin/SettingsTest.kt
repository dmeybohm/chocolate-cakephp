import com.intellij.openapi.util.JDOMUtil
import org.junit.jupiter.api.Assertions.*

internal class SettingsTest {

    @org.junit.jupiter.api.Test
    fun getState() {
        JDOMUtil.load("")
        assertEquals(true, true)
    }

    @org.junit.jupiter.api.Test
    fun loadState() {
        assertEquals(false, true)
    }

}