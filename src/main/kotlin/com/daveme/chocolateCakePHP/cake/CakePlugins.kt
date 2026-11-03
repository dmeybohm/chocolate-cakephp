package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.CakeTwoPluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.effectivePluginName
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

internal val cakeTwoPluginDirectoryNames = setOf("Plugin", "plugins")

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
    return generateSequence(file.parent) { it.parent }
        .takeWhile { it != projectDir }
        .firstNotNullOfOrNull { configuredCakeTwoPluginOfDirectory(it, settings) }
        ?.pluginName
}

/**
 * The configured CakePHP 2 plugin whose directory [directory] is.
 */
fun configuredCakeTwoPluginOfDirectory(directory: VirtualFile, settings: Settings): CakeTwoPluginConfig? =
    settings.pluginConfigs.filterIsInstance<CakeTwoPluginConfig>()
        .firstOrNull { settings.configuredDirectory(it.pluginPath) == directory }

private val cakeThreeTemplateDirectoryNames = setOf("templates", "Template")

/**
 * The CakePHP 3+ plugin [file] belongs to, from its path: a configured plugin's path, a directory
 * under the `plugins` directory, or the app's [overrideDirectory] for a plugin. Null for a file of
 * the app itself.
 */
fun cakeThreePluginName(
    file: VirtualFile,
    projectDir: VirtualFile?,
    settings: Settings,
    overrideDirectory: String
): String? {
    val configuredDirectories = settings.cakeThreePluginConfigs
        .filter { it.effectivePluginName().isNotBlank() }
        .mapNotNull { config -> settings.configuredDirectory(config.pluginPath)?.let { it to config } }
    var current: VirtualFile? = file.parent
    while (current != null && current != projectDir) {
        val directory = current
        configuredDirectories.firstOrNull { it.first == directory }
            ?.let { return it.second.effectivePluginName() }
        val parent = directory.parent
        if (parent?.name == "plugins") {
            return directory.name
        }
        if (parent?.name == overrideDirectory && parent.parent?.name in cakeThreeTemplateDirectoryNames) {
            return directory.name
        }
        current = parent
    }
    return null
}

/**
 * Whether [file] is under the directory of a configured plugin.
 */
fun isInConfiguredPlugin(file: VirtualFile, settings: Settings): Boolean =
    settings.pluginConfigs.any { config ->
        settings.configuredDirectory(config.pluginPath)?.let { VfsUtil.isAncestor(it, file, true) } == true
    }
