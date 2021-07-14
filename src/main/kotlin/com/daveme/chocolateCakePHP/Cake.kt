package com.daveme.chocolateCakePHP

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

sealed class Cake(val viewDirectory: String, val elementTop: String) {
    abstract fun templatePath(settings: Settings, controllerName: String, controllerAction: String): String
    abstract fun elementPath(settings: Settings, elementPath: String): String
}

fun pluginOrAppDirectoryFromFile(settings: Settings, file: PsiFile): PsiDirectory? {
    val originalFile = file.originalFile
    val pluginDir = pluginDirectoryFromFile(settings, originalFile)
        ?: return appDirectoryFromFile(settings, originalFile)
    return pluginDir
}

fun isCakeViewFile(settings: Settings, file: PsiFile): Boolean {
    val hasExtension = file.name.endsWith(settings.cake2TemplateExtension) ||
           file.name.endsWith(settings.cakeTemplateExtension)
    if (!hasExtension) {
        return false
    }
    val originalFile = file.originalFile
    val topDir = pluginOrAppDirectoryFromFile(settings, originalFile)
    var dir = originalFile.containingDirectory
    var maxDirs = 6;
    while (dir != null && dir != topDir) {
        if (dir.name == "View" || dir.name == "Template") {
            return true
        }
        dir = dir.parentDirectory
        maxDirs--
    }
    return false
}

fun pluginDirectoryFromFile(settings: Settings, file: PsiFile): PsiDirectory? {
    var first: PsiDirectory? = file.containingDirectory
    var second = first?.parent
    var third = second?.parent
    while (third != null && third.name != settings.pluginPath) {
        first = second
        second = third
        third = third.parent
    }
    if (third?.name == settings.pluginPath) {
        return first
    }
    return null
}

fun appDirectoryFromFile(settings: Settings, file: PsiFile): PsiDirectory? {
    var dir: PsiDirectory? = file.containingDirectory
    while (dir != null) {
        if (settings.cake3Enabled) {
            if (dir.name == settings.appDirectory) {
                return dir
            }
        }
        if (settings.cake2Enabled) {
            if (dir.name == settings.cake2AppDirectory) {
                return dir
            }
        }
        dir = dir.parent
    }
    return null
}

object CakeThree : Cake(viewDirectory = "Template", elementTop = "Element") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
        "$viewDirectory/$controllerName/$controllerAction.${settings.cakeTemplateExtension}"

    override fun elementPath(settings: Settings, elementPath: String): String =
        "$viewDirectory/$elementTop/$elementPath.${settings.cakeTemplateExtension}"
}

object CakeTwo : Cake(viewDirectory = "View", elementTop = "Elements") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
            "${viewDirectory}/$controllerName/$controllerAction.${settings.cake2TemplateExtension}"

    override fun elementPath(settings: Settings, elementPath: String): String =
            "${viewDirectory}/${elementTop}/$elementPath.${settings.cake2TemplateExtension}"
}