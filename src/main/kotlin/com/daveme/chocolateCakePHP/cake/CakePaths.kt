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
//   - the "templates" directory in CakePHP 4+
//   - the "plugins/MyPlugin/src" directory in CakePHP 3+
//
sealed interface TopSourceDirectory {
    val psiDirectory: PsiDirectory
}
sealed interface AppOrSrcDirectory : TopSourceDirectory
class PluginSrcDirectory(override val psiDirectory: PsiDirectory, val pluginDirName: String) : TopSourceDirectory
class AppDirectory(override val psiDirectory: PsiDirectory) : AppOrSrcDirectory
class SrcDirectory(override val psiDirectory: PsiDirectory) : AppOrSrcDirectory

data class TemplatesDir(val dirName: String, val psiDirectory: PsiDirectory)

fun topSourceDirectoryFromControllerFile(settings: Settings, file: PsiFile): TopSourceDirectory? {
    val originalFile = file.originalFile
    return pluginSrcDirectoryFromControllerFile(settings, originalFile)
        ?: appOrSrcDirectoryFromControllerFile(settings, originalFile)
}

fun templatesDirFromControllerFile(project: Project, settings: Settings, file: PsiFile): TemplatesDir? {
    val topDir = topSourceDirectoryFromControllerFile(settings, file) ?: return null
    return templatesDirectoryFromTopSourceDirectory(project, settings, topDir)
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
                return TemplatesDir("templates", dir)
            }
        }
        val cakeThreeViewDir = findRelativeFile(topDir.psiDirectory, "Template")
        if (cakeThreeViewDir != null) {
            val dir = virtualFileToPsiDirectory(project, cakeThreeViewDir)
            if (dir != null) {
                return TemplatesDir("Template", dir)
            }
        }
    }
    if (settings.cake2Enabled) {
        val cakeTwoViewDir = findRelativeFile(topDir.psiDirectory, "View")
        if (cakeTwoViewDir != null) {
            val dir = virtualFileToPsiDirectory(project, cakeTwoViewDir)
            if (dir != null) {
                return TemplatesDir("View", dir)
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
            return TemplatesDir(parent.name, parent)
        }
        else if (hasCakeThree && parent?.name == settings.appDirectory && child.name == "Template") {
            return TemplatesDir(child.name, child)
        }
        else if (hasCakeTwo && parent?.name == settings.cake2AppDirectory && child.name == "View") {
            return TemplatesDir(child.name, child)
        }
        child = parent
        parent = parent?.parent
    }

    return null
}

fun actionNameToViewFilename(
    templatesDirectory: TemplatesDir,
    settings: Settings,
    actionName: String
): String {
    return when (templatesDirectory.dirName) {
        "Template" -> "${actionName.camelCaseToUnderscore()}.${settings.cakeTemplateExtension}"
        "View" -> "${actionName}.${settings.cake2TemplateExtension}"
        else -> "${actionName.camelCaseToUnderscore()}.php" // cake 4+
    }
}

private fun pluginSrcDirectoryFromControllerFile(settings: Settings, file: PsiFile): PluginSrcDirectory? {
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

private fun appOrSrcDirectoryFromControllerFile(settings: Settings, file: PsiFile): AppOrSrcDirectory? {
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

sealed class CakeView(val viewDirectory: String, val elementTop: String) {
    abstract fun templatePath(settings: Settings, controllerName: String, controllerAction: String): String
    abstract fun elementPath(settings: Settings, elementPath: String): String
}

fun isCakeViewFile(project: Project, settings: Settings, file: PsiFile): Boolean {
    return if (templatesDirectoryFromViewFile(project, settings, file) != null) true else false
}

object CakeFour : CakeView(viewDirectory = "templates", elementTop = "element") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
        "${controllerName}/${controllerAction}.php"

    override fun elementPath(settings: Settings, elementPath: String): String =
        "${elementTop}/${elementPath}.php"
}

object CakeThree : CakeView(viewDirectory = "Template", elementTop = "Element") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
        "${controllerName}/${controllerAction}.${settings.cakeTemplateExtension}"

    override fun elementPath(settings: Settings, elementPath: String): String =
        "${elementTop}/${elementPath}.${settings.cakeTemplateExtension}"
}

object CakeTwo : CakeView(viewDirectory = "View", elementTop = "Elements") {
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