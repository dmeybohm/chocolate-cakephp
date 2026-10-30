package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.CakeTwoPluginConfig
import com.daveme.chocolateCakePHP.LegacyPluginConfig
import com.daveme.chocolateCakePHP.SettingsState
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import junit.framework.TestCase

class PluginConfigSerializationTest : TestCase() {

    private fun roundTrip(state: SettingsState): SettingsState =
        XmlSerializer.deserialize(XmlSerializer.serialize(state), SettingsState::class.java)

    fun `test mixed plugin list round trips`() {
        val state = SettingsState(
            cake2Enabled = true,
            pluginConfigs = listOf(
                CakeTwoPluginConfig(pluginName = "DebugKit", pluginPath = "app/Plugin/DebugKit"),
                CakeThreePluginConfig(
                    pluginName = "Blog",
                    namespace = "\\Vendor\\Blog",
                    srcPath = "lib",
                    pluginPath = "plugins/Blog",
                    assetPath = "assets",
                ),
                LegacyPluginConfig(namespace = "\\Old", pluginPath = "plugins/Old"),
            ),
        )
        assertEquals(state, roundTrip(state))
    }

    fun `test cakephp 2 entry is written without namespace or src path`() {
        val xml = JDOMUtil.write(XmlSerializer.serialize(SettingsState(
            pluginConfigs = listOf(CakeTwoPluginConfig(pluginName = "DebugKit", pluginPath = "app/Plugin/DebugKit")),
        )))
        val pluginXml = xml.substringAfter("<CakeTwoPluginConfig", "").substringBefore("</CakeTwoPluginConfig>")
        assertTrue(xml, pluginXml.isNotEmpty())
        assertFalse(xml, pluginXml.contains("namespace"))
        assertFalse(xml, pluginXml.contains("srcPath"))
    }

    fun `test entries saved before plugin versions load as legacy entries`() {
        val saved = JDOMUtil.load(LEGACY_SETTINGS_XML)
        val loaded = XmlSerializer.deserialize(saved, SettingsState::class.java)
        assertEquals(
            listOf(
                LegacyPluginConfig(pluginName = "Blog", namespace = "\\Vendor\\Blog", pluginPath = "plugins/Blog"),
                LegacyPluginConfig(namespace = "\\", pluginPath = "app/Plugin/DebugKit"),
            ),
            loaded.pluginConfigs,
        )
    }

    fun `test empty plugin list round trips`() {
        val state = SettingsState(cake2Enabled = true)
        assertEquals(state, roundTrip(state))
    }
}

/**
 * Settings as saved before plugins had a CakePHP version.
 */
private const val LEGACY_SETTINGS_XML = """
<SettingsState>
  <option name="cake2Enabled" value="true" />
  <option name="pluginConfigs">
    <PluginConfig>
      <option name="pluginName" value="Blog" />
      <option name="namespace" value="\Vendor\Blog" />
      <option name="srcPath" value="src" />
      <option name="pluginPath" value="plugins/Blog" />
      <option name="assetPath" value="webroot" />
    </PluginConfig>
    <PluginConfig>
      <option name="pluginName" value="" />
      <option name="namespace" value="\" />
      <option name="srcPath" value="src" />
      <option name="pluginPath" value="app/Plugin/DebugKit" />
      <option name="assetPath" value="webroot" />
    </PluginConfig>
  </option>
</SettingsState>
"""
