package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.PluginConfig

data class PluginEntry(
    var namespace: String,
    var pluginPath: String,
    var assetPath: String = "webroot",
    var srcPath: String = "src"
) {
    companion object {
        @JvmStatic
        fun fromPluginConfig(pluginConfig: PluginConfig): PluginEntry {
            return PluginEntry(
                namespace = pluginConfig.namespace,
                pluginPath = pluginConfig.pluginPath,
                assetPath = pluginConfig.assetPath,
                srcPath = pluginConfig.srcPath,
            )
        }
    }

    fun toPluginConfig(): PluginConfig {
        return PluginConfig(
            namespace = namespace,
            pluginPath = pluginPath,
            assetPath = assetPath,
            srcPath = srcPath
        )
    }
}