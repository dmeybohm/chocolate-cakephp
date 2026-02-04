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
 * Represents a parsed plugin resource path.
 * CakePHP uses dot notation to reference plugin resources: "PluginName.resource_path"
 */
data class PluginResourcePath(
    val pluginName: String?,  // null if no plugin prefix
    val resourcePath: String  // path after the dot (or full path if no prefix)
)

/**
 * Parses a CakePHP plugin-prefixed resource path.
 *
 * Examples:
 * - "MyPlugin.stylesheet" -> PluginResourcePath("MyPlugin", "stylesheet")
 * - "MyPlugin.subdir/file" -> PluginResourcePath("MyPlugin", "subdir/file")
 * - "regular/path" -> PluginResourcePath(null, "regular/path")
 * - ".something" -> PluginResourcePath(null, ".something") // empty plugin name
 *
 * The first dot is treated as the separator only if:
 * - There is at least one character before it
 * - The portion before the dot doesn't contain a slash
 */
fun parsePluginResourcePath(path: String): PluginResourcePath {
    val dotIndex = path.indexOf('.')
    return if (dotIndex > 0 && !path.substring(0, dotIndex).contains('/')) {
        PluginResourcePath(path.substring(0, dotIndex), path.substring(dotIndex + 1))
    } else {
        PluginResourcePath(null, path)
    }
}

/**
 * Parses a plugin resource path and looks up the plugin config if present.
 * Returns a pair of (PluginResourcePath, PluginConfig?) where the config is
 * non-null only if a valid plugin prefix was found and matched.
 */
fun parseAndLookupPlugin(
    path: String,
    settings: Settings
): Pair<PluginResourcePath, PluginConfig?> {
    val pluginResourcePath = parsePluginResourcePath(path)
    val pluginConfig = pluginResourcePath.pluginName?.let {
        settings.findPluginConfigByName(it)
    }
    return Pair(pluginResourcePath, pluginConfig)
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
        templateDirWithPath.templatesPath.startsWith(pluginConfig.pluginPath)
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
        templateDirWithPath.templatesPath.startsWith(pluginConfig.pluginPath)
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
