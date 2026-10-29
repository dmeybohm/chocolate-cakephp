package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.effectivePluginName
import com.daveme.chocolateCakePHP.findPluginConfigByName

const val CELL_PARENT_CLASS = "\\Cake\\View\\Cell"
const val CELL_TRAIT = "\\Cake\\View\\CellTrait"

/**
 * A parsed cell string, as passed to `cell()`: an optional plugin, the cell name and an
 * optional action, written `Plugin.Name::action`.
 */
data class CellName(
    val plugin: String?,
    val name: String,
    val action: String?,
) {
    val actionOrDefault: String get() = action ?: "display"
}

fun parseCellName(cell: String): CellName? {
    val pluginAndName = cell.substringBefore("::")
    val action = if (cell.contains("::")) cell.substringAfter("::").ifEmpty { null } else null
    val dotIndex = pluginAndName.lastIndexOf('.')
    val plugin = if (dotIndex > 0) pluginAndName.substring(0, dotIndex) else null
    val name = pluginAndName.substring(dotIndex + 1)
    if (name.isEmpty()) {
        return null
    }
    return CellName(plugin, name, action)
}

/**
 * The namespaces cells live in: the app namespace and each configured plugin namespace,
 * paired with the plugin name to prefix the cell name with (null for the app).
 */
fun cellNamespaces(settings: Settings): List<Pair<String?, String>> =
    listOf(null to settings.appNamespace) +
        settings.pluginConfigs.map { it.effectivePluginName() to it.namespace }

fun cellClassFqn(settings: Settings, cellName: CellName): String {
    val namespace = when (val plugin = cellName.plugin) {
        null -> settings.appNamespace
        else -> settings.findPluginConfigByName(plugin)?.namespace
            ?: ("\\" + plugin.replace('/', '\\'))
    }
    return "${namespace}\\View\\Cell\\${cellName.name.replace('/', '\\')}Cell"
}

/**
 * The name `cell()` takes for a cell class, or null when the class is not under the
 * `View\Cell` namespace of the given namespace.
 */
fun cellNameFromClassFqn(namespace: String, classFqn: String): String? {
    val prefix = "${namespace}\\View\\Cell\\"
    if (!classFqn.startsWith(prefix, ignoreCase = true) || !classFqn.endsWith("Cell")) {
        return null
    }
    val name = classFqn.substring(prefix.length).removeSuffix("Cell")
    if (name.isEmpty()) {
        return null
    }
    return name.replace('\\', '/')
}

val TemplatesDir.cellDirName: String
    get() = when (this) {
        is CakeFourTemplatesDir -> "cell"
        else -> "Cell"
    }

fun cellPluginLookupResult(settings: Settings, cellName: CellName): PluginLookupResult {
    val pluginConfig = cellName.plugin?.let { settings.findPluginConfigByName(it) }
        ?: return PluginLookupResult.NoPlugin(cellName.name)
    return PluginLookupResult.PluginFound(cellName.name, pluginConfig)
}

/**
 * The view paths of a cell template: `template` under the cell's template directory, such as
 * `cell/Admin/Inventory/` in CakePHP 4+ or `Cell/Admin/Inventory/` in CakePHP 3.
 */
fun allViewPathsFromCellTemplate(
    allTemplatePaths: AllTemplatePaths,
    settings: Settings,
    cellName: CellName,
    template: String,
    convertCase: Boolean,
): AllViewPaths {
    val viewPaths = allTemplatePaths.allPaths.map { templatesDirWithPath ->
        ViewPath(
            label = template,
            templatePath = templatesDirWithPath.templatesPath,
            prefix = "${templatesDirWithPath.templatesDir.cellDirName}/${cellName.name}/",
            altLabel = template,
            relativePath = addViewFilenameExtension(
                templatesDirectory = templatesDirWithPath.templatesDir,
                name = template,
                settings = settings,
                convertCase = convertCase,
            )
        )
    }.toList()
    return AllViewPaths(
        defaultViewPaths = viewPaths,
        otherViewPaths = listOf(),
        dataViewPaths = listOf()
    )
}
