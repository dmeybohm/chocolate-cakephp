package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.CakeTwoPluginConfig
import com.daveme.chocolateCakePHP.PluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.effectivePluginName
import com.daveme.chocolateCakePHP.findRelativeFile
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
        .firstNotNullOfOrNull { configuredCakeTwoPluginOfDirectory(it, projectDir, settings) }
        ?.pluginName
}

/**
 * The configured CakePHP 2 plugin whose directory [directory] is.
 */
fun configuredCakeTwoPluginOfDirectory(
    directory: VirtualFile,
    projectDir: VirtualFile?,
    settings: Settings
): CakeTwoPluginConfig? =
    settings.pluginConfigs.filterIsInstance<CakeTwoPluginConfig>()
        .firstOrNull { isDirectoryOfConfiguredPlugin(directory, it, projectDir, settings) }

/**
 * Whether [directory] is the directory of the configured plugin [pluginConfig]: the plugin's path
 * from an app root of its CakePHP version that is not itself a plugin, or from the project directory.
 */
fun isDirectoryOfConfiguredPlugin(
    directory: VirtualFile,
    pluginConfig: PluginConfig,
    projectDir: VirtualFile?,
    settings: Settings
): Boolean {
    val path = pluginConfig.pluginPath.trim('/')
    if (path.isEmpty() || !directory.path.endsWith("/$path")) {
        return false
    }
    val root = generateSequence(directory) { it.parent }.elementAtOrNull(path.split('/').size)
        ?: return false
    if (root == projectDir) {
        return true
    }
    val appDirectory = when (pluginConfig) {
        is CakeTwoPluginConfig -> settings.cake2AppDirectory
        is CakeThreePluginConfig -> settings.appDirectory
    }
    return root.parent?.name != "plugins" && findRelativeFile(root, appDirectory)?.isDirectory == true
}

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
    val configs = settings.cakeThreePluginConfigs
        .filter { it.pluginPath.isNotBlank() && it.effectivePluginName().isNotBlank() }
    var current: VirtualFile? = file.parent
    while (current != null && current != projectDir) {
        val directory = current
        configs.firstOrNull { isDirectoryOfConfiguredPlugin(directory, it, projectDir, settings) }
            ?.let { return it.effectivePluginName() }
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
fun isInConfiguredPlugin(file: VirtualFile, projectDir: VirtualFile?, settings: Settings): Boolean =
    generateSequence(file.parent) { it.parent }
        .takeWhile { it != projectDir }
        .any { directory ->
            settings.pluginConfigs.any { isDirectoryOfConfiguredPlugin(directory, it, projectDir, settings) }
        }
