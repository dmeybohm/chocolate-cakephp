package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.CakeTwoPluginConfig
import com.daveme.chocolateCakePHP.PluginConfig

data class PluginEntry(
    var pluginName: String,
    var namespace: String,
    var pluginPath: String,
    var assetPath: String = "webroot",
    var srcPath: String = "src",
    var cakeTwo: Boolean = false,
) {
    companion object {
        @JvmStatic
        fun fromPluginConfig(pluginConfig: PluginConfig): PluginEntry {
            return when (pluginConfig) {
                is CakeTwoPluginConfig -> PluginEntry(
                    pluginName = pluginConfig.pluginName,
                    namespace = "",
                    pluginPath = pluginConfig.pluginPath,
                    assetPath = pluginConfig.assetPath,
                    srcPath = "",
                    cakeTwo = true,
                )
                is CakeThreePluginConfig -> PluginEntry(
                    pluginName = pluginConfig.pluginName,
                    namespace = pluginConfig.namespace,
                    pluginPath = pluginConfig.pluginPath,
                    assetPath = pluginConfig.assetPath,
                    srcPath = pluginConfig.srcPath,
                )
            }
        }
    }

    fun toPluginConfig(): PluginConfig {
        return if (cakeTwo) {
            CakeTwoPluginConfig(
                pluginName = pluginName,
                pluginPath = pluginPath,
                assetPath = assetPath,
            )
        } else {
            CakeThreePluginConfig(
                pluginName = pluginName,
                namespace = namespace,
                pluginPath = pluginPath,
                assetPath = assetPath,
                srcPath = srcPath
            )
        }
    }
}
