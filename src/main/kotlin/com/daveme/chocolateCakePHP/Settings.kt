package com.daveme.chocolateCakePHP

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

data class SettingsState(
    var cakeTemplateExtension: String = "php",
    var appDirectory: String = "src",
    var appNamespace: String = "\\App",
    var pluginPath: String = "plugins",
    var cake2AppDirectory: String =  "app",
    var cake2TemplateExtension: String = "ctp",
    var cake2PluginPath: String = "app/Plugin",
    var cake2Enabled: Boolean = true,
    var cake3Enabled: Boolean = true,
    var pluginNamespaces: List<String> = arrayListOf("\\DebugKit")
)

@Service
@State(
    name = "ChocolateCakePHPSettings",
    storages = [Storage( "ChocolateCakePHP.xml")]
)
class Settings : PersistentStateComponent<SettingsState> {

    private var state = SettingsState()

    val cakeTemplateExtension get() = state.cakeTemplateExtension
    val appDirectory get() = state.appDirectory
    val appNamespace get() = state.appNamespace
    val pluginPath get() = state.pluginPath
    val cake2AppDirectory get() = state.cake2AppDirectory
    val cake2TemplateExtension get() = state.cake2TemplateExtension
    val cake2Enabled get() = state.cake2Enabled
    val cake3Enabled get() = state.cake3Enabled

    val pluginEntries: List<PluginEntry>
        get() {
            return pluginEntryListFromNamespaceList(state.pluginNamespaces)
        }

    val enabled: Boolean
        get() {
            return cake2Enabled || cake3Enabled
        }

    override fun equals(other: Any?): Boolean {
        return if (other is Settings) {
            this.state == other.state
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return state.hashCode()
    }

    override fun getState(): SettingsState? {
        return this.state
    }

    override fun loadState(state: SettingsState) {
        this.state = state
    }

    companion object {

        @JvmStatic
        fun getInstance(project: Project): Settings {
            val settings = project.getService(Settings::class.java)
            return settings
        }

        @JvmStatic
        val defaults get() = Settings()

        @JvmStatic
        fun fromSettings(settings: Settings): Settings {
            val newState = settings.state.copy()
            val newSettings = Settings()
            newSettings.loadState(newState)
            return newSettings
        }

        @JvmStatic
        fun pluginEntryListFromNamespaceList(list: List<String>): List<PluginEntry> {
            val result = arrayListOf<PluginEntry>()
            list.forEach { result.add(PluginEntry(it)) }
            return result.toList()
        }

        @JvmStatic
        fun pluginNamespaceListFromEntryList(list: List<PluginEntry>): List<String> {
            val result = arrayListOf<String>()
            list.forEach { result.add(it.namespace) }
            return result
        }

    }

}

