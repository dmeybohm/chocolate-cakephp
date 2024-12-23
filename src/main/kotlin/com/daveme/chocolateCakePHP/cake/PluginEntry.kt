package com.daveme.chocolateCakePHP.cake

data class PluginEntry(var namespace: String, var templatePath: String?)
{
    companion object {
        @JvmStatic
        fun fromNamespace(namespace: String): PluginEntry {
            return PluginEntry(namespace, null)
        }
    }
}