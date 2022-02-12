package com.daveme.chocolateCakePHP

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.PhpClass

sealed class Cake(val viewDirectory: String, val elementTop: String) {
    abstract fun templatePath(settings: Settings, controllerName: String, controllerAction: String): String
    abstract fun elementPath(settings: Settings, elementPath: String): String
    abstract fun isCakeViewFile(settings: Settings, topDir: PsiDirectory?, file: PsiFile): Boolean
}

fun topSourceDirectoryFromFile(settings: Settings, file: PsiFile): PsiDirectory? {
    val originalFile = file.originalFile
    return pluginDirectoryFromFile(settings, originalFile)
        ?: appDirectoryFromFile(settings, originalFile)
        ?: templateDirectoryFromFile(settings, originalFile)
}

private fun templateDirectoryFromFile(settings: Settings, originalFile: PsiFile): PsiDirectory? {
    if (!settings.cake3Enabled) {
        return null
    }
    var dir: PsiDirectory? = originalFile.containingDirectory
    while (dir != null) {
        if (dir.name == "templates") {
            return dir
        }
        dir = dir.parent
    }
    return null
}

fun isCakeViewFile(settings: Settings, file: PsiFile): Boolean {
    if (!settings.enabled) {
        return false
    }

    val hasCakeThree = if (settings.cake3Enabled)
        file.name.endsWith(settings.cakeTemplateExtension)
    else
        false

    val hasCakeTwo = if (settings.cake2Enabled)
        file.name.endsWith(settings.cake2TemplateExtension)
    else
        false

    val topDir = topSourceDirectoryFromFile(settings, file)
    if (hasCakeThree) {
        if (CakeFour.isCakeViewFile(settings, topDir, file)) {
            return true
        }
        if (CakeThree.isCakeViewFile(settings, topDir, file)) {
            return true
        }
    }

    if (hasCakeTwo && CakeTwo.isCakeViewFile(settings, topDir, file)) {
        return true
    }

    return false
}

private fun pluginDirectoryFromFile(settings: Settings, file: PsiFile): PsiDirectory? {
    if (!settings.cake3Enabled) {
        return null
    }
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

private fun appDirectoryFromFile(settings: Settings, file: PsiFile): PsiDirectory? {
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

object CakeFour : Cake(viewDirectory = "templates", elementTop = "element") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
        "../$viewDirectory/$controllerName/$controllerAction.${settings.cakeTemplateExtension}"

    override fun elementPath(settings: Settings, elementPath: String): String =
        "../$viewDirectory/$elementTop/$elementPath.${settings.cakeTemplateExtension}"

    override fun isCakeViewFile(settings: Settings, topDir: PsiDirectory?, file: PsiFile): Boolean {
        return topDir?.name == viewDirectory
    }
}

object CakeThree : Cake(viewDirectory = "Template", elementTop = "Element") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
        "$viewDirectory/$controllerName/$controllerAction.${settings.cakeTemplateExtension}"

    override fun elementPath(settings: Settings, elementPath: String): String =
        "$viewDirectory/$elementTop/$elementPath.${settings.cakeTemplateExtension}"

    override fun isCakeViewFile(settings: Settings, topDir: PsiDirectory?, file: PsiFile): Boolean {
        var dir = file.originalFile.containingDirectory

        while (dir != null && dir != topDir) {
            if (settings.cake3Enabled) {
                if (dir.name == viewDirectory && dir.parent == topDir) {
                    return true
                }
            }
            dir = dir.parentDirectory
        }

        return false
    }
}

object CakeTwo : Cake(viewDirectory = "View", elementTop = "Elements") {
    override fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
            "${viewDirectory}/$controllerName/$controllerAction.${settings.cake2TemplateExtension}"

    override fun elementPath(settings: Settings, elementPath: String): String =
            "${viewDirectory}/${elementTop}/$elementPath.${settings.cake2TemplateExtension}"

    override fun isCakeViewFile(settings: Settings, topDir: PsiDirectory?, file: PsiFile): Boolean {
        var dir = file.originalFile.containingDirectory
        while (dir != null && dir != topDir) {
            if (dir.name == viewDirectory && dir.parent == topDir) {
                return true
            }
            dir = dir.parentDirectory
        }
        return false
    }

    fun isModelClass(classes: Collection<PhpClass>): Boolean {
        return classes.any { phpClass -> phpClass.superFQN == "\\AppModel" }
    }

}