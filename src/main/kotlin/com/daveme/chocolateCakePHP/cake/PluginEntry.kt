package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.PluginConfig

data class PluginEntry(
    var pluginName: String,
    var namespace: String,
    var pluginPath: String,
    var assetPath: String = "webroot",
    var srcPath: String = "src"
) {
    companion object {
        @JvmStatic
        fun fromPluginConfig(pluginConfig: PluginConfig): PluginEntry {
            return PluginEntry(
                pluginName = pluginConfig.pluginName,
                namespace = pluginConfig.namespace,
                pluginPath = pluginConfig.pluginPath,
                assetPath = pluginConfig.assetPath,
                srcPath = pluginConfig.srcPath,
            )
        }
    }

    fun toPluginConfig(): PluginConfig {
        return PluginConfig(
            pluginName = pluginName,
            namespace = namespace,
            pluginPath = pluginPath,
            assetPath = assetPath,
            srcPath = srcPath
        )
    }
}