package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.PluginConfig
import com.daveme.chocolateCakePHP.SettingsState
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import junit.framework.TestCase

/**
 * A plugin entry in the polymorphic list.
 */
private sealed interface SpikePluginConfig {
    val pluginName: String
    val pluginPath: String
    val assetPath: String
}

@Tag("PluginConfig")
private data class SpikeLegacyPluginConfig(
    @Property override val pluginName: String = "",
    @Property val namespace: String = "",
    @Property val srcPath: String = "src",
    @Property override val pluginPath: String = "",
    @Property override val assetPath: String = "webroot",
) : SpikePluginConfig

@Tag("CakeTwoPluginConfig")
private data class SpikeCakeTwoPluginConfig(
    @Property override val pluginName: String = "",
    @Property override val pluginPath: String = "",
    @Property override val assetPath: String = "webroot",
) : SpikePluginConfig

@Tag("CakeThreePluginConfig")
private data class SpikeCakeThreePluginConfig(
    @Property override val pluginName: String = "",
    @Property val namespace: String = "",
    @Property val srcPath: String = "src",
    @Property override val pluginPath: String = "",
    @Property override val assetPath: String = "webroot",
) : SpikePluginConfig

private data class SpikeState(
    var cake2Enabled: Boolean = false,

    @XCollection(elementTypes = [
        SpikeLegacyPluginConfig::class,
        SpikeCakeTwoPluginConfig::class,
        SpikeCakeThreePluginConfig::class,
    ])
    var pluginConfigs: List<SpikePluginConfig> = listOf(),
)

/**
 * Checks that xmlb can persist a plugin list mixing the legacy tag with the
 * CakePHP 2 and CakePHP 3+ tags, before the settings model depends on it.
 */
class PluginConfigSerializationTest : TestCase() {

    private fun roundTrip(state: SpikeState): SpikeState =
        XmlSerializer.deserialize(XmlSerializer.serialize(state), SpikeState::class.java)

    fun `test mixed plugin list round trips`() {
        val state = SpikeState(
            cake2Enabled = true,
            pluginConfigs = listOf(
                SpikeCakeTwoPluginConfig(pluginName = "DebugKit", pluginPath = "app/Plugin/DebugKit"),
                SpikeCakeThreePluginConfig(
                    pluginName = "Blog",
                    namespace = "\\Vendor\\Blog",
                    srcPath = "lib",
                    pluginPath = "plugins/Blog",
                    assetPath = "assets",
                ),
                SpikeLegacyPluginConfig(namespace = "\\Old", pluginPath = "plugins/Old"),
            ),
        )
        assertEquals(state, roundTrip(state))
    }

    fun `test cakephp 2 entry is written without namespace or src path`() {
        val xml = JDOMUtil.write(XmlSerializer.serialize(SpikeState(
            pluginConfigs = listOf(SpikeCakeTwoPluginConfig(pluginName = "DebugKit", pluginPath = "app/Plugin/DebugKit")),
        )))
        assertTrue(xml, xml.contains("<CakeTwoPluginConfig"))
        assertFalse(xml, xml.contains("namespace"))
        assertFalse(xml, xml.contains("srcPath"))
    }

    fun `test entries saved by the current settings load as legacy entries`() {
        val saved = SettingsState(
            pluginConfigs = listOf(
                PluginConfig(pluginName = "Blog", namespace = "\\Vendor\\Blog", pluginPath = "plugins/Blog"),
                PluginConfig(namespace = "\\", pluginPath = "app/Plugin/DebugKit", assetPath = "webroot"),
            ),
        )
        val loaded = XmlSerializer.deserialize(XmlSerializer.serialize(saved), SpikeState::class.java)
        assertEquals(
            listOf(
                SpikeLegacyPluginConfig(pluginName = "Blog", namespace = "\\Vendor\\Blog", pluginPath = "plugins/Blog"),
                SpikeLegacyPluginConfig(namespace = "\\", pluginPath = "app/Plugin/DebugKit"),
            ),
            loaded.pluginConfigs,
        )
    }

    fun `test empty plugin list round trips`() {
        val state = SpikeState(cake2Enabled = true)
        assertEquals(state, roundTrip(state))
    }
}
