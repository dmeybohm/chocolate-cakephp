package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile

//
// This represents either:
//   - the "app" directory in CakePHP 2
//   - the "src" directory in CakePHP 3+
//   - the "plugins/MyPlugin/src" directory in CakePHP 3+
//
sealed interface TopSourceDirectory {
    val psiDirectory: PsiDirectory
}
sealed interface AppOrSrcDirectory : TopSourceDirectory
class PluginSrcDirectory(override val psiDirectory: PsiDirectory, val pluginDirName: String) : TopSourceDirectory
class AppDirectory(override val psiDirectory: PsiDirectory) : AppOrSrcDirectory
class SrcDirectory(override val psiDirectory: PsiDirectory) : AppOrSrcDirectory

sealed interface TemplatesDir {
    val dirName: String
    val psiDirectory: PsiDirectory
}

data class CakeFourTemplatesDir(override val dirName: String, override val psiDirectory: PsiDirectory): TemplatesDir
data class CakeThreeTemplatesDir(override val dirName: String, override val psiDirectory: PsiDirectory): TemplatesDir
data class CakeTwoTemplatesDir(override val dirName: String, override val psiDirectory: PsiDirectory): TemplatesDir


fun topSourceDirectoryFromControllerFile(settings: Settings, file: PsiFile): TopSourceDirectory? {
    val originalFile = file.originalFile
    return pluginSrcDirectoryFromControllerFile(settings, originalFile)
        ?: appOrSrcDirectoryFromControllerFile(settings, originalFile)
}

fun templatesDirectoryFromTopSourceDirectory(
    project: Project,
    settings: Settings,
    topDir: TopSourceDirectory
): TemplatesDir? {
    if (settings.cake3Enabled) {
        val projectDir = topDir.psiDirectory.parentDirectory
        val cakeFourViewDir = findRelativeFile(projectDir, "templates")
        if (cakeFourViewDir != null) {
            val dir = virtualFileToPsiDirectory(project, cakeFourViewDir)
            if (dir != null) {
                return CakeFourTemplatesDir("templates", dir)
            }
        }
        val cakeThreeViewDir = findRelativeFile(topDir.psiDirectory, "Template")
        if (cakeThreeViewDir != null) {
            val dir = virtualFileToPsiDirectory(project, cakeThreeViewDir)
            if (dir != null) {
                return CakeThreeTemplatesDir("Template", dir)
            }
        }
    }
    if (settings.cake2Enabled) {
        val cakeTwoViewDir = findRelativeFile(topDir.psiDirectory, "View")
        if (cakeTwoViewDir != null) {
            val dir = virtualFileToPsiDirectory(project, cakeTwoViewDir)
            if (dir != null) {
                return CakeTwoTemplatesDir("View", dir)
            }
        }
    }

    return null
}

fun templatesDirectoryFromViewFile(project: Project, settings: Settings, file: PsiFile): TemplatesDir? {
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

    val originalFile = file.originalFile
    var child: PsiDirectory? = originalFile.containingDirectory ?: return null
    var parent: PsiDirectory? = child?.parent
    val projectDir = project.guessProjectDir() ?: return null
    while (child != null && child != projectDir && child.name != settings.pluginPath) {
        if (hasCakeFour && parent?.name == "templates") {
            return CakeFourTemplatesDir(parent.name, parent)
        }
        else if (hasCakeThree && parent?.name == settings.appDirectory && child.name == "Template") {
            return CakeThreeTemplatesDir(child.name, child)
        }
        else if (hasCakeTwo && parent?.name == settings.cake2AppDirectory && child.name == "View") {
            return CakeTwoTemplatesDir(child.name, child)
        }
        child = parent
        parent = parent?.parent
    }

    return null
}

fun topSourceDirectoryFromTemplatesDirectory(templatesDir: TemplatesDir, project: Project, settings: Settings): TopSourceDirectory? {
    return when (templatesDir) {
        is CakeFourTemplatesDir, is CakeThreeTemplatesDir -> {
            pluginSrcDirectoryFromTemplatesDir(templatesDir, project, settings)
            ?: srcDirectoryFromTemplatesDir(templatesDir, project, settings)
        }
        is CakeTwoTemplatesDir -> {
            appDirectoryFromTemplatesDir(templatesDir, settings)
        }
    }
}

private fun pluginSrcDirectoryFromTemplatesDir(
    templatesDir: TemplatesDir,
    project: Project,
    settings: Settings
): TopSourceDirectory? {
    assert(templatesDir is CakeFourTemplatesDir || templatesDir is CakeThreeTemplatesDir)
    val parent = templatesDir.psiDirectory.parentDirectory ?: return null
    val grandparent = parent.parentDirectory ?: return null
    val srcFile = findRelativeFile(parent, "src") ?: return null
    if (!srcFile.isDirectory) {
        return null
    }
    val srcDir = virtualFileToPsiDirectory(project, srcFile) ?: return null
    return if (grandparent.name == settings.pluginPath) {
        PluginSrcDirectory(srcDir, parent.name)
    } else {
        null
    }
}

fun appDirectoryFromTemplatesDir(
    templatesDir: TemplatesDir,
    settings: Settings,
): TopSourceDirectory? {
    assert(templatesDir is CakeTwoTemplatesDir)
    val parent = templatesDir.psiDirectory.parentDirectory ?: return null
    if (settings.cake2Enabled) {
        if (parent.name == settings.cake2AppDirectory) {
            return AppDirectory(parent)
        }
    }
    return null
}

private fun srcDirectoryFromTemplatesDir(
    templatesDir: TemplatesDir,
    project: Project,
    settings: Settings
): TopSourceDirectory? {
    assert(templatesDir is CakeFourTemplatesDir || templatesDir is CakeThreeTemplatesDir)
    when (templatesDir) {
        is CakeFourTemplatesDir -> {
            val parent = templatesDir.psiDirectory.parentDirectory ?: return null
            val virtualFile = findRelativeFile(parent, settings.appDirectory)
                ?: return null
            val psiDir = virtualFileToPsiDirectory(project, virtualFile) ?: return null
            if (psiDir.name == settings.appDirectory)
                return SrcDirectory(psiDir)
            else
                return null
        }
        is CakeThreeTemplatesDir -> {
            val parent = templatesDir.psiDirectory.parentDirectory ?: return null
            if (parent.name == settings.appDirectory)
                return SrcDirectory(parent)
            else
                return null
        }
        else -> return null
    }
}

fun pluginSrcDirectoryFromControllerFile(
    settings: Settings,
    file: PsiFile
): PluginSrcDirectory? {
    if (!settings.cake3Enabled) {
        return null
    }
    var child: PsiDirectory? = file.containingDirectory
    var parent = child?.parent
    var grandparent = parent?.parent
    while (grandparent != null && grandparent.name != settings.pluginPath) {
        child = parent
        parent = grandparent
        grandparent = grandparent.parent
    }
    if (grandparent?.name == settings.pluginPath) {
        return PluginSrcDirectory(child!!, parent!!.name)
    }
    return null
}

fun appOrSrcDirectoryFromControllerFile(
    settings: Settings,
    file: PsiFile
): AppOrSrcDirectory? {
    var dir: PsiDirectory? = file.containingDirectory
    while (dir != null) {
        if (settings.cake3Enabled) {
            if (dir.name == settings.appDirectory) {
                return SrcDirectory(dir)
            }
        }
        if (settings.cake2Enabled) {
            if (dir.name == settings.cake2AppDirectory) {
                return AppDirectory(dir)
            }
        }
        dir = dir.parent
    }
    return null
}


sealed class CakeView(val elementTop: String) {
    abstract fun templatePath(settings: Settings, controllerName: String, controllerAction: String): String
    abstract fun elementPath(settings: Settings, elementPath: String): String
}

fun isCakeViewFile(project: Project, settings: Settings, file: PsiFile): Boolean {
    return if (templatesDirectoryFromViewFile(project, settings, file) != null)
        true
    else
        false
}

fun isCakeControllerFile(file: PsiFile): Boolean {
    return file.virtualFile.nameWithoutExtension.endsWith("Controller")
}

object CakeFour : CakeView(elementTop = "element") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
        "${controllerName}/${controllerAction}.php"

    override fun elementPath(settings: Settings, elementPath: String): String =
        "${elementTop}/${elementPath}.php"
}

object CakeThree : CakeView(elementTop = "Element") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
        "${controllerName}/${controllerAction}.${settings.cakeTemplateExtension}"

    override fun elementPath(settings: Settings, elementPath: String): String =
        "${elementTop}/${elementPath}.${settings.cakeTemplateExtension}"
}

object CakeTwo : CakeView(elementTop = "Elements") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
        "${controllerName}/$controllerAction.${settings.cake2TemplateExtension}"

    override fun elementPath(settings: Settings, elementPath: String): String =
        "${elementTop}/$elementPath.${settings.cake2TemplateExtension}"
}

fun templatePathToVirtualFile(
    settings: Settings,
    templatesDir: TemplatesDir,
    controllerName: String,
    controllerAction: String
): VirtualFile? {
    var relativeFile: VirtualFile? = null
    val directory = templatesDir.psiDirectory
    if (settings.cake3Enabled) {
        val underscored = controllerAction.camelCaseToUnderscore()
        val cakeThreeTemplatePath = CakeThree.templatePath(settings, controllerName, underscored)
        relativeFile = findRelativeFile(directory, cakeThreeTemplatePath)
        if (relativeFile == null) {
            val cakeFourTemplatePath = CakeFour.templatePath(settings, controllerName, underscored)
            relativeFile = findRelativeFile(directory, cakeFourTemplatePath)
        }
    }
    if (relativeFile == null) {
        if (settings.cake2Enabled) {
            val cakeTwoTemplatePath = CakeTwo.templatePath(settings, controllerName, controllerAction)
            relativeFile = findRelativeFile(directory, cakeTwoTemplatePath)
        }
    }
    return relativeFile
}

fun elementPathToVirtualFile(
    settings: Settings,
    templatesDir: TemplatesDir,
    elementPath: String
): VirtualFile? {
    var relativeFile: VirtualFile? = null
    val directory = templatesDir.psiDirectory
    if (settings.cake3Enabled) {
        val cakeThreeElementFilename = CakeThree.elementPath(settings, elementPath)
        relativeFile = findRelativeFile(directory, cakeThreeElementFilename)
       if (relativeFile == null) {
            val cakeFourElementFilename = CakeFour.elementPath(settings, elementPath)
            relativeFile = findRelativeFile(directory, cakeFourElementFilename)
        }
    }
    if (relativeFile == null) {
        if (settings.cake2Enabled) {
            val cakeTwoElementFilename = CakeTwo.elementPath(settings, elementPath)
            relativeFile = findRelativeFile(directory, cakeTwoElementFilename)
        }
    }
    return relativeFile
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
