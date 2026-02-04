package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

//
// This represents either:
//   - the "app" directory in CakePHP 2
//   - the "src" directory in CakePHP 3+
//
sealed interface TopSourceDirectory {
    val directory: VirtualFile
}
sealed interface AppOrSrcDirectory : TopSourceDirectory
class AppDirectory(override val directory: VirtualFile) : AppOrSrcDirectory
class SrcDirectory(override val directory: VirtualFile) : AppOrSrcDirectory

sealed interface TemplatesDir {
    val directory: VirtualFile
    val elementDirName: String
}

data class PluginAndThemeTemplatePaths(
    val paths: List<TemplatesDirWithPath> = listOf(),
)

data class AllTemplatePaths(
    val mainTemplatePaths: List<TemplatesDirWithPath>,
    val pluginAndThemeTemplatePaths: PluginAndThemeTemplatePaths = PluginAndThemeTemplatePaths(),
) {
    val allPaths: Sequence<TemplatesDirWithPath>
        get() = mainTemplatePaths.asSequence() + pluginAndThemeTemplatePaths.paths.asSequence()
}

data class CakeFourTemplatesDir(override val directory: VirtualFile): TemplatesDir
{
    override val elementDirName: String
        get() = "element"
}

data class CakeThreeTemplatesDir(override val directory: VirtualFile): TemplatesDir
{
    override val elementDirName: String
        get() = "Element"
}

data class CakeTwoTemplatesDir(override val directory: VirtualFile): TemplatesDir
{
    override val elementDirName: String
        get() = "Elements"
}

data class AssetDirectory(val directory: VirtualFile)
data class RootDirectory(val directory: VirtualFile)

fun topSourceDirectoryFromSourceFile(settings: Settings, virtualFile: VirtualFile): TopSourceDirectory? {
    val originalFile = virtualFile.parent
    return appOrSrcDirectoryFromSourceFile(settings, originalFile)
}

fun topSourceDirectoryFromSourceFile(settings: Settings, file: PsiFile): TopSourceDirectory? {
    return topSourceDirectoryFromSourceFile(settings, file.virtualFile ?: return null)
}

fun allTemplatePathsFromTopSourceDirectory(
    project: Project,
    settings: Settings,
    topDir: TopSourceDirectory
): AllTemplatePaths? {
    val mainTemplatePaths : MutableList<TemplatesDirWithPath> = mutableListOf()

    // Note: guessProjectDir() is used only for computing relative path strings for display/identification.
    // Actual directory discovery uses appRootDir (derived from the view file context via topDir).
    // If the IDE project is opened at a subdirectory, these relative paths may appear differently
    // but navigation will still work correctly.
    val projectDir = project.guessProjectDir()
        ?: return null

    // Determine the application root directory based on the top source directory.
    // For CakePHP 4+, the src directory's parent is the app root.
    // For CakePHP 2, the app directory is the app root.
    val appRootDir = when (topDir) {
        is SrcDirectory -> topDir.directory.parent
        is AppDirectory -> topDir.directory
    } ?: return null

    if (settings.cake3Enabled) {
        val srcDir = topDir.directory
        val cakeFourViewDir = findRelativeFile(srcDir.parent, "templates")
        if (cakeFourViewDir != null) {
            val cake4Path = pathRelativeToProjectRoot(projectDir, cakeFourViewDir)
            mainTemplatePaths.add(
                TemplatesDirWithPath(
                    templatesDir = CakeFourTemplatesDir(cakeFourViewDir),
                    templatesPath = cake4Path
                )
            )
        }
        val cakeThreeViewDir = findRelativeFile(srcDir, "Template")
        if (cakeThreeViewDir != null) {
            val cake3Path = pathRelativeToProjectRoot(projectDir, cakeThreeViewDir)
            mainTemplatePaths.add(TemplatesDirWithPath(
                templatesDir = CakeThreeTemplatesDir(cakeThreeViewDir),
                templatesPath = cake3Path
            ))
        }
    }

    if (settings.cake2Enabled) {
        val cakeTwoViewDir = findRelativeFile(topDir.directory, "View")
        if (cakeTwoViewDir != null) {
            val cake2Path = pathRelativeToProjectRoot(projectDir, cakeTwoViewDir)
            mainTemplatePaths.add(TemplatesDirWithPath(
                templatesDir = CakeTwoTemplatesDir(cakeTwoViewDir),
                templatesPath = cake2Path
            ))
        }
    }

    // Ensure we always have a least one main template path, to simplify code.
    if (mainTemplatePaths.isEmpty()) {
        return null
    }

    val pluginAndThemePaths = pluginAndThemeTemplatePaths(
        appRootDir = appRootDir,
        settings = settings
    )
    return AllTemplatePaths(
        mainTemplatePaths = mainTemplatePaths,
        pluginAndThemeTemplatePaths = pluginAndThemePaths,
    )
}

fun templatesDirectoryOfViewFile(
    project: Project,
    settings: Settings,
    file: VirtualFile
): TemplatesDir? {
    if (!settings.enabled) {
        return null
    }

    val hasCakeFour = if (settings.cake3Enabled)
        file.name.endsWith("php")
    else
        false

    val hasCakeThree = if (settings.cake3Enabled)
        file.name.endsWith(settings.cakeTemplateExtension)
    else
        false

    val hasCakeTwo = if (settings.cake2Enabled)
        file.name.endsWith(settings.cake2TemplateExtension)
    else
        false

    val originalFile = file
    var child: VirtualFile? = originalFile.parent ?: return null
    var parent: VirtualFile? = child?.parent
    val projectDir = project.guessProjectDir() ?: return null
    while (child != null && child != projectDir) {
        if (hasCakeFour && parent?.name == "templates") {
            return CakeFourTemplatesDir(parent)
        }
        else if (hasCakeThree && parent != null && matchesAppDirectory(parent, settings) && child.name == "Template") {
            return CakeThreeTemplatesDir(child)
        }
        else if (hasCakeTwo) {
            if (parent != null && matchesCake2AppDirectory(parent, settings) && child.name == "View") {
                return CakeTwoTemplatesDir(child)
            }
            // plugins:
            if (child.name == "View") {
                val grandparent = parent?.parent
                val greatGrandparent = grandparent?.parent
                if (greatGrandparent != null && matchesCake2AppDirectory(greatGrandparent, settings)) {
                    return CakeTwoTemplatesDir(child)
                }
            }
            // themes:
            // For themed views like app/View/Themed/MyTheme/Movie/view.ctp,
            // the templates directory should be the theme directory (MyTheme),
            // not the Themed directory itself.
            if (parent?.name == "Themed") {
                val grandparent = parent.parent  // View
                val greatGrandparent = grandparent?.parent  // app
                if (grandparent != null && grandparent.name == "View" &&
                    greatGrandparent != null && matchesCake2AppDirectory(greatGrandparent, settings)) {
                    // child is the theme directory (e.g., "MyTheme")
                    return CakeTwoTemplatesDir(child)
                }
            }
        }
        child = parent
        parent = parent?.parent
    }

    return null
}

fun templatesDirectoryOfViewFile(
    project: Project,
    settings: Settings,
    file: PsiFile
): TemplatesDir? {
    return templatesDirectoryOfViewFile(
        project,
        settings,
        file.originalFile.virtualFile ?: return null
    )
}

private fun pluginAndThemeTemplatePaths(
    appRootDir: VirtualFile,
    settings: Settings,
): PluginAndThemeTemplatePaths {
    if (!settings.enabled) {
        return PluginAndThemeTemplatePaths()
    }

    val hasCakeTwo = settings.cake2Enabled
    val hasCakeThree = settings.cake3Enabled

    // todo: optimize the filesystem traversals here, as they are doing extra work
    val resultPaths = settings.pluginAndThemeConfigs.flatMap { pluginOrThemeConfig ->
        val results = mutableListOf<TemplatesDirWithPath>()
        if (hasCakeThree) {
            val cakeFourTemplatesPath = "${pluginOrThemeConfig.pluginPath}/templates"
            val cakeFourTemplateDir = findRelativeFile(appRootDir, cakeFourTemplatesPath)
            if (cakeFourTemplateDir != null) {
                // Use the plugin path as the relative path (this is used for matching)
                val templatesDir = TemplatesDirWithPath(
                    templatesDir = CakeFourTemplatesDir(cakeFourTemplateDir),
                    templatesPath = cakeFourTemplatesPath
                )
                results.add(templatesDir)
            }
            when (pluginOrThemeConfig) {
                is PluginConfig -> {
                    val cake3PluginTemplatePath = "${pluginOrThemeConfig.pluginPath}/${pluginOrThemeConfig.srcPath}/Template"
                    val cakeThreePluginTemplateDir = findRelativeFile(appRootDir, cake3PluginTemplatePath)
                    if (cakeThreePluginTemplateDir != null) {
                        val templatesDir = TemplatesDirWithPath(
                            templatesDir = CakeThreeTemplatesDir(cakeThreePluginTemplateDir),
                            templatesPath = cake3PluginTemplatePath
                        )
                        results.add(templatesDir)
                    }
                }
                is ThemeConfig -> {
                    val cakeThreeThemeTemplatePath = "${pluginOrThemeConfig.pluginPath}/src/Template"
                    val cakeThreeThemeTemplateDir = findRelativeFile(appRootDir, cakeThreeThemeTemplatePath)
                    if (cakeThreeThemeTemplateDir != null) {
                        val templatesDir = TemplatesDirWithPath(
                            templatesDir = CakeThreeTemplatesDir(cakeThreeThemeTemplateDir),
                            templatesPath = cakeThreeThemeTemplatePath
                        )
                        results.add(templatesDir)
                    }
                }
            }
        }
        if (hasCakeTwo) {
            when (pluginOrThemeConfig) {
                is ThemeConfig -> {
                    val cakeTwoThemeTemplateDir = findRelativeFile(appRootDir, pluginOrThemeConfig.pluginPath)
                    if (cakeTwoThemeTemplateDir != null) {
                        val templatesDir = TemplatesDirWithPath(
                            templatesDir = CakeTwoTemplatesDir(cakeTwoThemeTemplateDir),
                            templatesPath = pluginOrThemeConfig.pluginPath
                        )
                        results.add(templatesDir)
                    }
                }
                is PluginConfig -> {
                    val viewPath = "${pluginOrThemeConfig.pluginPath}/View"
                    val cakeTwoPluginTemplateDir = findRelativeFile(appRootDir, viewPath)
                    if (cakeTwoPluginTemplateDir != null) {
                        val templatesDir = TemplatesDirWithPath(
                            templatesDir = CakeTwoTemplatesDir(cakeTwoPluginTemplateDir),
                            templatesPath = viewPath
                        )
                        results.add(templatesDir)
                    }
                }
            }
        }
        results
    }.toList()

    return PluginAndThemeTemplatePaths(
        paths = resultPaths,
    )
}

fun rootDirectoryFromTemplatesDir(templatesDir: TemplatesDir): RootDirectory? {
    val rootVirtualFile =  when (templatesDir) {
        is CakeFourTemplatesDir -> {
            templatesDir.directory.parent ?: return null
        }
        is CakeThreeTemplatesDir, is CakeTwoTemplatesDir -> {
            templatesDir.directory.parent?.parent ?: return null
        }
    }
    return RootDirectory(rootVirtualFile)
}

fun assetDirectoryFromViewFile(
    project: Project,
    settings: Settings,
    virtualFile: VirtualFile
): AssetDirectory? {
    val templatesDir = templatesDirectoryOfViewFile(project, settings, virtualFile)
        ?: return null
    val startingDir = when (templatesDir) {
        is CakeTwoTemplatesDir ->
            templatesDir.directory.parent ?: return null
        is CakeFourTemplatesDir, is CakeThreeTemplatesDir -> {
            val rootDirectory = rootDirectoryFromTemplatesDir(templatesDir)
                ?: return null
            rootDirectory.directory
        }
    }
    val webroot = findRelativeFile(startingDir, "webroot") ?: return null
    return AssetDirectory(webroot)
}

/**
 * Gets the root directory from a view file.
 * For CakePHP 4+, this is the parent of the templates directory.
 * For CakePHP 3, this is the parent of the parent of the Template directory (parent of src).
 * For CakePHP 2, this is the parent of the View directory.
 */
fun rootDirectoryFromViewFile(
    project: Project,
    settings: Settings,
    virtualFile: VirtualFile
): RootDirectory? {
    val templatesDir = templatesDirectoryOfViewFile(project, settings, virtualFile)
        ?: return null
    return when (templatesDir) {
        is CakeTwoTemplatesDir ->
            templatesDir.directory.parent?.let { RootDirectory(it) }
        is CakeFourTemplatesDir, is CakeThreeTemplatesDir ->
            rootDirectoryFromTemplatesDir(templatesDir)
    }
}

/**
 * Check if the directory matches the cake2AppDirectory setting.
 * Handles both simple ("app") and nested ("src/app") paths.
 * For nested paths, verifies the directory hierarchy from bottom to top.
 */
private fun matchesCake2AppDirectory(directory: VirtualFile, settings: Settings): Boolean {
    return matchesNestedAppDirectory(directory, settings.cake2AppDirectory)
}

/**
 * Check if the directory matches the appDirectory setting (CakePHP 3+).
 * Handles both simple ("src") and nested ("foo/src") paths.
 */
private fun matchesAppDirectory(directory: VirtualFile, settings: Settings): Boolean {
    return matchesNestedAppDirectory(directory, settings.appDirectory)
}

/**
 * Check if a directory matches a potentially nested path.
 * For "src/app", verifies the directory is "app" and its parent is "src".
 */
private fun matchesNestedAppDirectory(directory: VirtualFile, appDirPath: String): Boolean {
    if (!appDirPath.contains("/")) {
        // Simple case: just compare the directory name
        return directory.name == appDirPath
    }
    // Nested case: verify the path from bottom to top
    val pathParts = appDirPath.split("/").reversed()
    var current: VirtualFile? = directory
    for (part in pathParts) {
        if (current == null || current.name != part) {
            return false
        }
        current = current.parent
    }
    return true
}

/**
 * Find a child directory by path, supporting nested paths like "foo/src".
 * Uses findRelativeFile which already handles path splitting.
 */
private fun findNestedChild(parent: VirtualFile, path: String): VirtualFile? {
    return findRelativeFile(parent, path)
}

private fun appOrSrcDirectoryFromSourceFile(
    settings: Settings,
    dir: VirtualFile?
): AppOrSrcDirectory? {
    var child: VirtualFile? = dir
    while (child != null) {
        if (settings.cake3Enabled) {
            val appDirChild = findNestedChild(child, settings.appDirectory)
            if (appDirChild != null) {
                return SrcDirectory(appDirChild)
            }
        }
        if (settings.cake2Enabled) {
            if (matchesCake2AppDirectory(child, settings)) {
                return AppDirectory(child)
            }
        }
        child = child.parent
    }
    return null
}


fun isCakeViewFile(project: Project, settings: Settings, file: PsiFile): Boolean {
    return if (templatesDirectoryOfViewFile(project, settings, file) != null)
        true
    else
        false
}

fun isCakeControllerFile(file: VirtualFile): Boolean {
   return file.nameWithoutExtension.endsWith("Controller")
}

fun isCakeControllerFile(file: PsiFile): Boolean {
    val virtualFile = file.virtualFile ?: return false
    return isCakeControllerFile(virtualFile)
}

data class ViewFilePathInfo(
    val templateDirPath: String, // relative to project root.
    val viewFilename: String,
)

fun viewFilePathInfoFromPath(viewFilePath: String): ViewFilePathInfo? {
    val pathPartsList = viewFilePath.split("/".toRegex())
    if (pathPartsList.size < 2) {
        return null
    }
    val lastElements = pathPartsList.takeLast(1)
    val templateDir = pathPartsList.dropLast(1)
    return ViewFilePathInfo(
        templateDirPath = templateDir.joinToString("/"),
        viewFilename = lastElements.first()
    )
}
