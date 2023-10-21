package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.camelCaseToUnderscore
import com.daveme.chocolateCakePHP.findRelativeFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

sealed class CakeView(val viewDirectory: String, val elementTop: String) {
    abstract fun templatePath(settings: Settings, controllerName: String, controllerAction: String): String
    abstract fun elementPath(settings: Settings, elementPath: String): String
    abstract fun isCakeViewFile(settings: Settings, topDir: TopSourceDirectory, file: PsiFile): Boolean
}

fun isCakeViewFile(settings: Settings, file: PsiFile): Boolean {
    if (!settings.enabled) {
        return false
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

    val topDir = topSourceDirectoryFromFile(settings, file) ?: return false

    if (hasCakeFour && CakeFour.isCakeViewFile(settings, topDir, file)) {
        return true
    }
    if (hasCakeThree && CakeThree.isCakeViewFile(settings, topDir, file)) {
        return true
    }
    if (hasCakeTwo && CakeTwo.isCakeViewFile(settings, topDir, file)) {
        return true
    }

    return false
}

object CakeFour : CakeView(viewDirectory = "templates", elementTop = "element") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
        "../$viewDirectory/$controllerName/$controllerAction.php"

    override fun elementPath(settings: Settings, elementPath: String): String =
        "../$viewDirectory/$elementTop/$elementPath.php"

    override fun isCakeViewFile(settings: Settings, topDir: TopSourceDirectory, file: PsiFile): Boolean {
        return settings.cake3Enabled && topDir is TopLevelTemplatesDirectory
    }
}

object CakeThree : CakeView(viewDirectory = "Template", elementTop = "Element") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
        "$viewDirectory/$controllerName/$controllerAction.${settings.cakeTemplateExtension}"

    override fun elementPath(settings: Settings, elementPath: String): String =
        "$viewDirectory/$elementTop/$elementPath.${settings.cakeTemplateExtension}"

    override fun isCakeViewFile(settings: Settings, topDir: TopSourceDirectory, file: PsiFile): Boolean {
        if (!settings.cake3Enabled) {
            return false
        }
        when (topDir) {
            is AppOrSrcDirectory, is PluginDirectory -> {
                var dir = file.originalFile.containingDirectory
                while (dir != null && dir != topDir.psiDirectory) {
                    if (settings.cake3Enabled) {
                        if (dir.name == viewDirectory && dir.parent == topDir.psiDirectory) {
                            return true
                        }
                    }
                    dir = dir.parentDirectory
                }

                return false
            }
            else -> {
                return false
            }
        }
    }
}

object CakeTwo : CakeView(viewDirectory = "View", elementTop = "Elements") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
            "${viewDirectory}/$controllerName/$controllerAction.${settings.cake2TemplateExtension}"

    override fun elementPath(settings: Settings, elementPath: String): String =
            "${viewDirectory}/${elementTop}/$elementPath.${settings.cake2TemplateExtension}"

    override fun isCakeViewFile(settings: Settings, topDir: TopSourceDirectory, file: PsiFile): Boolean {
        var dir = file.originalFile.containingDirectory
        while (dir != null && dir != topDir.psiDirectory) {
            if (dir.name == viewDirectory && dir.parent == topDir.psiDirectory) {
                return true
            }
            dir = dir.parentDirectory
        }
        return false
    }
}

fun templatePathToVirtualFile(
    settings: Settings,
    topDir: TopSourceDirectory,
    controllerName: String,
    controllerAction: String
): VirtualFile? {
    var relativeFile: VirtualFile? = null
    val directory = topDir.psiDirectory
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
    topSourceDirectory: TopSourceDirectory,
    elementPath: String
): VirtualFile? {
    var relativeFile: VirtualFile? = null
    val directory = topSourceDirectory.psiDirectory
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

