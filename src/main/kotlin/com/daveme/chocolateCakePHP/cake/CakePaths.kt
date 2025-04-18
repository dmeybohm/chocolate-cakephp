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
    val allPaths: List<TemplatesDirWithPath>
        get() = mainTemplatePaths + pluginAndThemeTemplatePaths.paths
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

    val projectDir = project.guessProjectDir()
        ?: return null

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
        project = project,
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
        else if (hasCakeThree && parent?.name == settings.appDirectory && child.name == "Template") {
            return CakeThreeTemplatesDir(child)
        }
        else if (hasCakeTwo) {
            if (parent?.name == settings.cake2AppDirectory && child.name == "View") {
                return CakeTwoTemplatesDir(child)
            }
            // plugins:
            if (child.name == "View") {
                val grandparent = parent?.parent
                val greatGrandparent = grandparent?.parent
                if (greatGrandparent != null && greatGrandparent.name == settings.cake2AppDirectory) {
                    return CakeTwoTemplatesDir(child)
                }
            }
            // themes:
            if (child.name == "Themed") {
                val grandparent = parent?.parent
                if (grandparent != null && grandparent.name == settings.cake2AppDirectory) {
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
    project: Project,
    settings: Settings,
): PluginAndThemeTemplatePaths {
    if (!settings.enabled) {
        return PluginAndThemeTemplatePaths()
    }

    val hasCakeTwo = settings.cake2Enabled
    val hasCakeThree = settings.cake3Enabled
    val projectDir = project.guessProjectDir()
        ?: return PluginAndThemeTemplatePaths()

    // todo: optimize the filesystem traversals here, as they are doing extra work
    val resultPaths = settings.pluginAndThemeConfigs.flatMap { pluginOrThemeConfig ->
        val results = mutableListOf<TemplatesDirWithPath>()
        if (hasCakeThree) {
            val cakeFourTemplatesPath = "${pluginOrThemeConfig.pluginPath}/templates"
            val cakeFourTemplateDir = projectDir.findFileByRelativePath(cakeFourTemplatesPath)
            if (cakeFourTemplateDir != null) {
                val path = pathRelativeToProjectRoot(projectDir, cakeFourTemplateDir)
                val templatesDir = TemplatesDirWithPath(
                    templatesDir = CakeFourTemplatesDir(cakeFourTemplateDir),
                    templatesPath = path
                )
                results.add(templatesDir)
            }
            when (pluginOrThemeConfig) {
                is PluginConfig -> {
                    val cake3PluginTemplatePath = "${pluginOrThemeConfig.pluginPath}/${pluginOrThemeConfig.srcPath}/Template"
                    val cakeThreePluginTemplateDir = projectDir.findFileByRelativePath(cake3PluginTemplatePath)
                    if (cakeThreePluginTemplateDir != null) {
                        val path = pathRelativeToProjectRoot(projectDir, cakeThreePluginTemplateDir)
                        val templatesDir = TemplatesDirWithPath(
                            templatesDir = CakeThreeTemplatesDir(cakeThreePluginTemplateDir),
                            templatesPath = path
                        )
                        results.add(templatesDir)
                    }
                }
                is ThemeConfig -> {
                    val cakeThreeThemeTemplateDir = projectDir.findFileByRelativePath("${pluginOrThemeConfig.pluginPath}/src/Template")
                    if (cakeThreeThemeTemplateDir != null) {
                        val path = pathRelativeToProjectRoot(projectDir, cakeThreeThemeTemplateDir)
                        val templatesDir = TemplatesDirWithPath(
                            templatesDir = CakeThreeTemplatesDir(cakeThreeThemeTemplateDir),
                            templatesPath = path
                        )
                        results.add(templatesDir)
                    }
                }
            }
        }
        if (hasCakeTwo) {
            when (pluginOrThemeConfig) {
                is ThemeConfig -> {
                    val cakeTwoThemeTemplateDir = projectDir.findFileByRelativePath(pluginOrThemeConfig.pluginPath)
                    if (cakeTwoThemeTemplateDir != null) {
                        val path = pathRelativeToProjectRoot(projectDir, cakeTwoThemeTemplateDir)
                        val templatesDir = TemplatesDirWithPath(
                            templatesDir = CakeTwoTemplatesDir(cakeTwoThemeTemplateDir),
                            templatesPath = path
                        )
                        results.add(templatesDir)
                    }
                }
                is PluginConfig -> {
                    val viewPath = "${pluginOrThemeConfig.pluginPath}/View"
                    val cakeTwoPluginTemplateDir = projectDir.findFileByRelativePath(viewPath)
                    if (cakeTwoPluginTemplateDir != null) {
                        val path = pathRelativeToProjectRoot(projectDir, cakeTwoPluginTemplateDir)
                        val templatesDir = TemplatesDirWithPath(
                            templatesDir = CakeTwoTemplatesDir(cakeTwoPluginTemplateDir),
                            templatesPath = path
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

private fun appOrSrcDirectoryFromSourceFile(
    settings: Settings,
    dir: VirtualFile?
): AppOrSrcDirectory? {
    var child: VirtualFile? = dir
    while (child != null) {
        if (settings.cake3Enabled) {
            val appDirChild = child.findChild(settings.appDirectory)
            if (appDirChild != null) {
                return SrcDirectory(appDirChild)
            }
        }
        if (settings.cake2Enabled) {
            if (child.name == settings.cake2AppDirectory) {
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
