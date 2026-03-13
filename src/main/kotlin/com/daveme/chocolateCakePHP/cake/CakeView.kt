package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

data class ControllerPath(
    val name: String,
    val prefix: String
) {
    val viewPath: String get() =
        if (prefix.isEmpty())
            "${name}/"
        else
            "${prefix}/${name}/"
}

/**
 * Result of parsing and looking up a plugin-prefixed resource path.
 *
 * This matches CakePHP's View::pluginSplit() behavior: when a dot-prefix is present
 * but doesn't match a loaded plugin, the full string is treated as a literal path
 * (i.e., falls back to NoPlugin).
 */
sealed class PluginLookupResult {
    /** A known plugin was matched — use plugin-specific resolution */
    data class PluginFound(val resourcePath: String, val pluginConfig: PluginConfig) : PluginLookupResult()
    /** No plugin prefix present, or prefix didn't match any configured plugin — use normal resolution with the original full path */
    data class NoPlugin(val originalPath: String) : PluginLookupResult()
}

/**
 * Parses a CakePHP plugin-prefixed resource path and looks up the plugin config.
 *
 * If the path contains a dot prefix that matches a configured plugin, returns
 * [PluginLookupResult.PluginFound] with the resource path after the dot.
 * Otherwise returns [PluginLookupResult.NoPlugin] with the original path,
 * matching CakePHP's fallback behavior.
 *
 * Examples:
 * - "MyPlugin.stylesheet" (MyPlugin configured) -> PluginFound("stylesheet", config)
 * - "MyPlugin.subdir/file" (MyPlugin configured) -> PluginFound("subdir/file", config)
 * - "unknown.file" (unknown not configured) -> NoPlugin("unknown.file")
 * - "regular/path" -> NoPlugin("regular/path")
 * - ".something" -> NoPlugin(".something")
 */
fun parseAndLookupPlugin(
    path: String,
    settings: Settings
): PluginLookupResult {
    val dotIndex = path.indexOf('.')
    if (dotIndex > 0 && !path.substring(0, dotIndex).contains('/')) {
        val pluginName = path.substring(0, dotIndex)
        val resourcePath = path.substring(dotIndex + 1)
        val pluginConfig = settings.findPluginConfigByName(pluginName)
        if (pluginConfig != null) {
            return PluginLookupResult.PluginFound(resourcePath, pluginConfig)
        }
    }
    return PluginLookupResult.NoPlugin(path)
}

data class TemplatesDirWithPath(
    val templatesDir: TemplatesDir,
    val templatesPath: String
)

data class AllViewPaths(
    val defaultViewPaths: List<ViewPath>,
    val otherViewPaths: List<ViewPath>,
    val dataViewPaths: List<ViewPath>,
) {
    val defaultViewPath: ViewPath =
        defaultViewPaths.first()

    val all: Sequence<ViewPath>
        get() = defaultViewPaths.asSequence() +
                otherViewPaths.asSequence() +
                dataViewPaths.asSequence()
}

fun controllerPathFromControllerFile(controllerFile: VirtualFile): ControllerPath? {
    val baseName = controllerFile.nameWithoutExtension.controllerBaseName()
    if (baseName.isNullOrEmpty()) {
        return null
    }
    var parent : VirtualFile? = controllerFile.parent
    val prefix = mutableListOf<String>()
    while (parent != null && parent.name != "Controller") {
        prefix.add(parent.name)
        parent = parent.parent
    }
    return ControllerPath(
        prefix = prefix.reversed().joinToString("/"),
        name = baseName
    )
}

fun viewFilesFromPluginViewPaths(
    project: Project,
    allTemplatesPaths: AllTemplatePaths,
    allViewPaths: AllViewPaths,
    pluginConfig: PluginConfig,
): Collection<PsiFile> {
    val pluginTemplatePaths = allTemplatesPaths.pluginAndThemeTemplatePaths.paths.filter { templateDirWithPath ->
        templateDirWithPath.templatesPath.startsWith(pluginConfig.pluginPath + "/")
    }
    val filteredTemplatePaths = AllTemplatePaths(
        mainTemplatePaths = listOf(),
        pluginAndThemeTemplatePaths = PluginAndThemeTemplatePaths(pluginTemplatePaths)
    )
    return viewFilesFromAllViewPaths(project, filteredTemplatePaths, allViewPaths)
}

fun viewFilesFromAllViewPaths(
    project: Project,
    allTemplatesPaths: AllTemplatePaths,
    allViewPaths: AllViewPaths,
): Collection<PsiFile> {
    val files = allViewPaths.all.asSequence()
        .flatMap { viewPath ->
            allTemplatesPaths.allPaths.asSequence()
                .map { templatePath -> viewPath to templatePath }
        }
        .mapNotNull { (viewPath, templatePath) ->
            viewPath.toVirtualFile(templatePath.templatesDir)
        }
        .toList()

    return virtualFilesToPsiFiles(project, files)
}

data class ViewPath(
    val label: String,
    val templatePath: String,
    val prefix: String,
    val relativePath: String,
    val altLabel: String = "",
) {
    val pathWithoutTemplate: String
        get() = "${prefix}${relativePath}"

    val fullPath: String
        get() = "${templatePath}/${pathWithoutTemplate}"

    fun toVirtualFile(templatesDir: TemplatesDir): VirtualFile? =
        findRelativeFile(templatesDir.directory, this.pathWithoutTemplate)
}

fun viewPathsFromControllerNameAndActionName(
    templatesPaths: Sequence<TemplatesDirWithPath>,
    settings: Settings,
    label: String,
    controllerPath: ControllerPath,
    actionName: ActionName,
    convertCase: Boolean,
    altLabel: String = ""
): List<ViewPath> {
    return templatesPaths.map { templateDirWithPath ->
        if (actionName.isAbsolute)
            ViewPath(
                templatePath = templateDirWithPath.templatesPath,
                prefix = "",
                label = label,
                altLabel = altLabel,
                relativePath = actionName.getViewFilename(templateDirWithPath.templatesDir, settings, convertCase)
            )
        else
            ViewPath(
                templatePath = templateDirWithPath.templatesPath,
                prefix = controllerPath.viewPath,
                label = label,
                altLabel = altLabel,
                relativePath = actionName.getViewFilename(templateDirWithPath.templatesDir, settings, convertCase)
            )
    }.toList()
}


fun allViewPathsFromController(
    controllerPath: ControllerPath,
    allTemplatePaths: AllTemplatePaths,
    settings: Settings,
    actionNames: ActionNames,
): AllViewPaths {
    val dataViewPaths = settings.dataViewExtensions.flatMap {
        val dataViewPrefix = if (actionNames.defaultActionName.isAbsolute)
            actionNames.defaultActionName.pathPrefix
        else
            "/${controllerPath.viewPath}"
        val actionName = ActionName(
            pathPrefix = "${dataViewPrefix}${it}/",
            name = actionNames.defaultActionName.name,
        )
        viewPathsFromControllerNameAndActionName(
            templatesPaths = allTemplatePaths.allPaths,
            settings = settings,
            label = it.uppercase(),
            controllerPath = controllerPath,
            actionName = actionName,
            convertCase = true
        )
    }
    val otherViewPaths = actionNames.otherActionNames.flatMap { actionName ->
        viewPathsFromControllerNameAndActionName(
            templatesPaths = allTemplatePaths.allPaths,
            settings = settings,
            label = actionName.path,
            controllerPath = controllerPath,
            actionName = actionName,
            convertCase = false
        )
    }
    return AllViewPaths(
        defaultViewPaths = viewPathsFromControllerNameAndActionName(
            templatesPaths = allTemplatePaths.allPaths,
            settings = settings,
            label = "Default",
            altLabel = actionNames.defaultActionName.path,
            controllerPath = controllerPath,
            actionName = actionNames.defaultActionName,
            convertCase = true,
        ),
        otherViewPaths = otherViewPaths,
        dataViewPaths = dataViewPaths,
    )
}

/**
 * Creates AllViewPaths for a template, filtering to only include a specific plugin's template paths.
 *
 * @param allTemplatePaths All available template paths
 * @param settings Plugin settings
 * @param actionNames The action names (template paths) to resolve
 * @param pluginConfig The plugin configuration to filter to
 * @return AllViewPaths containing only paths from the specified plugin
 */
fun allViewPathsFromPluginTemplate(
    allTemplatePaths: AllTemplatePaths,
    settings: Settings,
    actionNames: ActionNames,
    pluginConfig: PluginConfig,
): AllViewPaths {
    // Filter template paths to only include those from the specified plugin
    val pluginTemplatePaths = allTemplatePaths.pluginAndThemeTemplatePaths.paths.filter { templateDirWithPath ->
        templateDirWithPath.templatesPath.startsWith(pluginConfig.pluginPath + "/")
    }

    if (pluginTemplatePaths.isEmpty()) {
        return AllViewPaths(
            defaultViewPaths = listOf(),
            otherViewPaths = listOf(),
            dataViewPaths = listOf()
        )
    }

    // Use empty controller path since plugin templates use absolute paths
    val emptyControllerPath = ControllerPath(name = "", prefix = "")

    return AllViewPaths(
        defaultViewPaths = viewPathsFromControllerNameAndActionName(
            templatesPaths = pluginTemplatePaths.asSequence(),
            settings = settings,
            label = "Default",
            altLabel = actionNames.defaultActionName.path,
            controllerPath = emptyControllerPath,
            actionName = actionNames.defaultActionName,
            convertCase = true,
        ),
        otherViewPaths = listOf(),
        dataViewPaths = listOf(),
    )
}

private fun elementViewPaths(
    allTemplatesPaths: AllTemplatePaths,
    settings: Settings,
    elementPath: String,
): List<ViewPath> {
    return allTemplatesPaths.allPaths.map { templatesDirWithPath ->
        ViewPath(
            label = elementPath,
            templatePath = templatesDirWithPath.templatesPath,
            prefix = templatesDirWithPath.templatesDir.elementDirName + "/",
            altLabel = elementPath,
            relativePath = addViewFilenameExtension(
                templatesDirectory = templatesDirWithPath.templatesDir,
                name = elementPath,
                settings = settings,
                convertCase = false,
            )
        )
    }.toList()
}

fun allViewPathsFromElementPath(
    allTemplatePaths: AllTemplatePaths,
    settings: Settings,
    elementPath: String,
): AllViewPaths {
    return AllViewPaths(
        defaultViewPaths = elementViewPaths(
            allTemplatesPaths = allTemplatePaths,
            settings = settings,
            elementPath = elementPath,
        ),
        otherViewPaths = listOf(),
        dataViewPaths = listOf()
    )
}

/**
 * Creates AllViewPaths for an element, filtering to only include a specific plugin's template paths.
 *
 * @param allTemplatePaths All available template paths
 * @param settings Plugin settings
 * @param elementPath The element path (without plugin prefix)
 * @param pluginConfig The plugin configuration to filter to
 * @return AllViewPaths containing only paths from the specified plugin
 */
fun allViewPathsFromPluginElementPath(
    allTemplatePaths: AllTemplatePaths,
    settings: Settings,
    elementPath: String,
    pluginConfig: PluginConfig,
): AllViewPaths {
    // Filter template paths to only include those from the specified plugin
    val pluginTemplatePaths = allTemplatePaths.pluginAndThemeTemplatePaths.paths.filter { templateDirWithPath ->
        templateDirWithPath.templatesPath.startsWith(pluginConfig.pluginPath + "/")
    }

    if (pluginTemplatePaths.isEmpty()) {
        return AllViewPaths(
            defaultViewPaths = listOf(),
            otherViewPaths = listOf(),
            dataViewPaths = listOf()
        )
    }

    // Create a filtered AllTemplatePaths with only the plugin's template paths
    val filteredTemplatePaths = AllTemplatePaths(
        mainTemplatePaths = listOf(), // No main template paths when looking up plugin elements
        pluginAndThemeTemplatePaths = PluginAndThemeTemplatePaths(pluginTemplatePaths)
    )

    return AllViewPaths(
        defaultViewPaths = elementViewPaths(
            allTemplatesPaths = filteredTemplatePaths,
            settings = settings,
            elementPath = elementPath,
        ),
        otherViewPaths = listOf(),
        dataViewPaths = listOf()
    )
}

fun allViewPathsToFiles(
    project: Project,
    allViewPaths: AllViewPaths
): Collection<PsiFile> {
    val projectRoot: VirtualFile = project.guessProjectDir()
        ?: return setOf()
    val files = allViewPaths.all.mapNotNull { viewPath ->
        val templatePath = findRelativeFile(projectRoot, viewPath.templatePath)
            ?: return@mapNotNull null
        findRelativeFile(templatePath, viewPath.pathWithoutTemplate)
    }.toList()

    return virtualFilesToPsiFiles(project, files)
}
