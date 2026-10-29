package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.effectivePluginName
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

private val cakeTwoPluginDirectoryNames = setOf("Plugin", "plugins")

/**
 * The CakePHP 2 plugin [file] belongs to, from its path: a directory under a `Plugin` or
 * `plugins` directory (the plugin roots `CakePlugin::load()` searches, and the app's
 * `View/Plugin/{Name}` override directory), or a configured plugin's path. Null for a file
 * of the app itself.
 */
fun cakeTwoPluginName(file: VirtualFile, projectDir: VirtualFile?, settings: Settings): String? {
    var current: VirtualFile? = file.parent
    while (current != null && current != projectDir) {
        val parent = current.parent
        if (parent != null && parent.name in cakeTwoPluginDirectoryNames) {
            return current.name
        }
        current = parent
    }
    if (projectDir == null) {
        return null
    }
    return settings.pluginConfigs.firstOrNull { pluginConfig ->
        val pluginDir = projectDir.findFileByRelativePath(pluginConfig.pluginPath)
        pluginConfig.pluginPath.isNotEmpty() && pluginDir != null && VfsUtil.isAncestor(pluginDir, file, false)
    }?.let { pluginConfig ->
        pluginConfig.effectivePluginName().ifEmpty { pluginConfig.pluginPath.trimEnd('/').substringAfterLast('/') }
    }
}
