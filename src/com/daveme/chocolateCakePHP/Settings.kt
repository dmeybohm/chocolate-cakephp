package com.daveme.chocolateCakePHP

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "ChocolateCakePHPSettings",
    storages = [Storage(file = "ChocolateCakePHP.xml")]
)
data class Settings(
    var cakeTemplateExtension: String = DefaultCakeTemplateExtension,
    var appDirectory: String = DefaultAppDirectory,
    var appNamespace: String = DefaultAppNamespace,
    var pluginPath: String = DefaultPluginPath,
    var cake2AppDirectory: String = DefaultCake2AppDirectory,
    var cake2TemplateExtension: String = DefaultCake2TemplateExtension,
    var cake2PluginPath: String = DefaultCake2PluginPath,
    var cake2Enabled: Boolean = DefaultCake2Enabled,
    var cake3Enabled: Boolean = DefaultCake3Enabled
): PersistentStateComponent<Settings> {

    @NonInjectable
    constructor(other: Settings): this(
            cakeTemplateExtension = other.cakeTemplateExtension,
            appDirectory = other.appDirectory,
            appNamespace = other.appNamespace,
            pluginPath = other.pluginPath,
            cake2AppDirectory = other.cake2AppDirectory,
            cake2TemplateExtension = other.cake2TemplateExtension,
            cake2PluginPath = other.cake2PluginPath,
            cake2Enabled = other.cake2Enabled,
            cake3Enabled = other.cake3Enabled
    )

    override fun getState(): Settings? {
        return this
    }

    override fun loadState(settings: Settings) {
        XmlSerializerUtil.copyBean(settings, this)
    }

    companion object {
        const val DefaultCakeTemplateExtension = "ctp"
        const val DefaultAppDirectory = "src"
        const val DefaultAppNamespace = "\\App"
        const val DefaultPluginPath = "src/Plugin"
        const val DefaultCake2AppDirectory = "app"
        const val DefaultCake2TemplateExtension = "ctp"
        const val DefaultCake2PluginPath = "app/Plugin"
        const val DefaultCake2Enabled = true   // todo calculate these dynamically
        const val DefaultCake3Enabled = true   // todo calculate these dynamically

        @JvmStatic
        fun getInstance(project: Project): Settings {
            return ServiceManager.getService<Settings>(project, Settings::class.java)
        }
    }
}