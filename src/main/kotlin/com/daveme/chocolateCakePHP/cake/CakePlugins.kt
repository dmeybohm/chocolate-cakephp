package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.CakeTwoPluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.effectivePluginName
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
 * The configured CakePHP 2 plugin whose directory [directory] is. Plugin paths are matched as path
 * suffixes, since they are relative to an app root that may be nested in the project.
 */
fun configuredCakeTwoPluginOfDirectory(directory: VirtualFile, settings: Settings): CakeTwoPluginConfig? =
    settings.pluginConfigs.filterIsInstance<CakeTwoPluginConfig>()
        .firstOrNull { isDirectoryOfPluginPath(directory, it.pluginPath) }

private fun isDirectoryOfPluginPath(directory: VirtualFile, pluginPath: String): Boolean {
    val path = pluginPath.trim('/')
    return path.isNotEmpty() && directory.path.endsWith("/$path")
}

private val cakeThreeTemplateDirectoryNames = setOf("templates", "Template")

/**
 * The CakePHP 3+ plugin [file] belongs to, from its path: a configured plugin's path, a directory
 * under the `plugins` directory, or the app's [overrideDirectory] for a plugin. Plugin paths are
 * matched as path suffixes, since they are relative to an app root that may be nested in the
 * project. Null for a file of the app itself.
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
        configs.firstOrNull { directory.path.endsWith("/" + it.pluginPath.trim('/')) }
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
 * Whether [file] is under the path of a configured plugin, matched the way the plugin name rules
 * for its CakePHP version match it.
 */
fun isInConfiguredPlugin(file: VirtualFile, projectDir: VirtualFile?, settings: Settings): Boolean =
    generateSequence(file.parent) { it.parent }
        .takeWhile { it != projectDir }
        .any { directory -> settings.pluginConfigs.any { isDirectoryOfPluginPath(directory, it.pluginPath) } }
