package com.daveme.chocolateCakePHP

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "ChocolateCakePHPSettings", storages = [Storage(file = "ChocolateCakePHP.xml")])
data class Settings(
    var cakeTemplateExtension: String = DefaultCakeTemplateExtension,
    var appDirectory: String = DefaultAppDirectory,
    var appNamespace: String = DefaultAppNamespace
): PersistentStateComponent<Settings> {

    constructor(other: Settings): this(
        cakeTemplateExtension = other.cakeTemplateExtension,
        appDirectory = other.appDirectory,
        appNamespace = other.appNamespace
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

        fun getInstance(project: Project): Settings {
            return ServiceManager.getService<Settings>(project, Settings::class.java)
        }
    }
}