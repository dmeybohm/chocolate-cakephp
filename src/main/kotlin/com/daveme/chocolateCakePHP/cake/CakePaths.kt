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
//   - the "plugins/MyPlugin/src" directory in CakePHP 4+
//
sealed interface TopSourceDirectory {
    val directory: VirtualFile
}
sealed interface AppOrSrcDirectory : TopSourceDirectory
class PluginSrcDirectory(override val directory: VirtualFile, val pluginDirName: String) : TopSourceDirectory
class AppDirectory(override val directory: VirtualFile) : AppOrSrcDirectory
class SrcDirectory(override val directory: VirtualFile) : AppOrSrcDirectory

sealed interface TemplatesDir {
    val directory: VirtualFile
}

data class CakeFourTemplatesDir(override val directory: VirtualFile): TemplatesDir
data class CakeThreeTemplatesDir(override val directory: VirtualFile): TemplatesDir
data class CakeTwoTemplatesDir(override val directory: VirtualFile): TemplatesDir

fun topSourceDirectoryFromSourceFile(settings: Settings, file: PsiFile): TopSourceDirectory? {
    val originalFile = file.virtualFile.parent
    return pluginSrcDirectoryFromSourceFile(settings, originalFile)
        ?: appOrSrcDirectoryFromSourceFile(settings, originalFile)
}

fun templatesDirectoryFromTopSourceDirectory(
    settings: Settings,
    topDir: TopSourceDirectory
): TemplatesDir? {
    if (settings.cake3Enabled) {
        val projectDir = topDir.directory.parent
        val cakeFourViewDir = findRelativeFile(projectDir, "templates")
        if (cakeFourViewDir != null) {
            return CakeFourTemplatesDir(cakeFourViewDir)
        }
        val cakeThreeViewDir = findRelativeFile(topDir.directory, "Template")
        if (cakeThreeViewDir != null) {
            return CakeThreeTemplatesDir(cakeThreeViewDir)
        }
    }
    if (settings.cake2Enabled) {
        val cakeTwoViewDir = findRelativeFile(topDir.directory, "View")
        if (cakeTwoViewDir != null) {
            return CakeTwoTemplatesDir(cakeTwoViewDir)
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

    val originalFile = file.originalFile.virtualFile ?: return null
    var child: VirtualFile? = originalFile.parent ?: return null
    var parent: VirtualFile? = child?.parent
    val projectDir = project.guessProjectDir() ?: return null
    while (child != null && child != projectDir && child.name != settings.pluginPath) {
        if (hasCakeFour && parent?.name == "templates") {
            return CakeFourTemplatesDir(parent)
        }
        else if (hasCakeThree && parent?.name == settings.appDirectory && child.name == "Template") {
            return CakeThreeTemplatesDir(child)
        }
        else if (hasCakeTwo && parent?.name == settings.cake2AppDirectory && child.name == "View") {
            return CakeTwoTemplatesDir(child)
        }
        child = parent
        parent = parent?.parent
    }

    return null
}

// NOTE: only call this with CakeThreeTemplatesDir or CakeFourTemplatesDir
private fun pluginSrcDirectoryFromTemplatesDir(
    templatesDir: TemplatesDir,
    settings: Settings
): TopSourceDirectory? {
    assert(templatesDir is CakeFourTemplatesDir || templatesDir is CakeThreeTemplatesDir)
    val parent = templatesDir.directory.parent ?: return null
    val grandparent = parent.parent ?: return null
    val srcFile = findRelativeFile(parent, "src") ?: return null
    if (!srcFile.isDirectory) {
        return null
    }
    return if (grandparent.name == settings.pluginPath) {
        PluginSrcDirectory(srcFile, parent.name)
    } else {
        null
    }
}

fun appDirectoryFromTemplatesDir(
    templatesDir: TemplatesDir,
    settings: Settings,
): TopSourceDirectory? {
    assert(templatesDir is CakeTwoTemplatesDir)
    val parent = templatesDir.directory.parent ?: return null
    if (settings.cake2Enabled) {
        if (parent.name == settings.cake2AppDirectory) {
            return AppDirectory(parent)
        }
    }
    return null
}

private fun srcDirectoryFromTemplatesDir(
    templatesDir: TemplatesDir,
    settings: Settings
): TopSourceDirectory? {
    assert(templatesDir is CakeFourTemplatesDir || templatesDir is CakeThreeTemplatesDir)
    when (templatesDir) {
        is CakeFourTemplatesDir -> {
            val parent = templatesDir.directory.parent ?: return null
            val virtualFile = findRelativeFile(parent, settings.appDirectory)
                ?: return null
            if (virtualFile.name == settings.appDirectory)
                return SrcDirectory(virtualFile)
            else
                return null
        }
        is CakeThreeTemplatesDir -> {
            val parent = templatesDir.directory.parent ?: return null
            if (parent.name == settings.appDirectory)
                return SrcDirectory(parent)
            else
                return null
        }
        else -> return null
    }
}

private fun pluginSrcDirectoryFromSourceFile(
    settings: Settings,
    dir: VirtualFile?
): PluginSrcDirectory? {
    if (!settings.cake3Enabled) {
        return null
    }
    var child: VirtualFile? = dir
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

private fun appOrSrcDirectoryFromSourceFile(
    settings: Settings,
    dir: VirtualFile?
): AppOrSrcDirectory? {
    var child: VirtualFile? = dir
    while (child != null) {
        if (settings.cake3Enabled) {
            if (child.name == settings.appDirectory) {
                return SrcDirectory(child)
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


@Deprecated("Use TemplatesDir and getViewFilename instead")
sealed class CakeView(val elementTop: String) {
    abstract fun elementPath(settings: Settings, elementPath: String): String
}

fun isCakeViewFile(project: Project, settings: Settings, file: PsiFile): Boolean {
    return if (templatesDirectoryFromViewFile(project, settings, file) != null)
        true
    else
        false
}

fun isCakeControllerFile(file: VirtualFile): Boolean {
   return file.nameWithoutExtension.endsWith("Controller")
}

fun isCakeControllerFile(file: PsiFile): Boolean {
    return isCakeControllerFile(file.virtualFile)
}

@Deprecated("Use TemplatesDir and getViewFilename instead")
object CakeFour : CakeView(elementTop = "element") {

    override fun elementPath(settings: Settings, elementPath: String): String =
        "${elementTop}/${elementPath}.php"
}

@Deprecated("Use TemplatesDir and getViewFilename instead")
object CakeThree : CakeView(elementTop = "Element") {

    override fun elementPath(settings: Settings, elementPath: String): String =
        "${elementTop}/${elementPath}.${settings.cakeTemplateExtension}"
}

@Deprecated("Use TemplatesDir and getViewFilename instead")
object CakeTwo : CakeView(elementTop = "Elements") {

    override fun elementPath(settings: Settings, elementPath: String): String =
        "${elementTop}/$elementPath.${settings.cake2TemplateExtension}"
}

@Deprecated("Rewrite this to use AllViewPaths instead")
fun elementPathToVirtualFile(
    settings: Settings,
    templatesDir: TemplatesDir,
    elementPath: String
): VirtualFile? {
    var relativeFile: VirtualFile? = null
    val directory = templatesDir.directory
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
