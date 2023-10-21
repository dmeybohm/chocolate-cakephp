package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
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
class PluginDirectory(override val psiDirectory: PsiDirectory) : TopSourceDirectory
class AppDirectory(override val psiDirectory: PsiDirectory) : AppOrSrcDirectory
class SrcDirectory(override val psiDirectory: PsiDirectory) : AppOrSrcDirectory
class TopLevelTemplatesDirectory(override val psiDirectory: PsiDirectory) : TopSourceDirectory

fun topSourceDirectoryFromFile(settings: Settings, file: PsiFile): TopSourceDirectory? {
    val originalFile = file.originalFile
    return pluginDirectoryFromFile(settings, originalFile)
        ?: appOrSrcDirectoryFromFile(settings, originalFile)
        ?: templateDirectoryFromFile(settings, originalFile)
}

private fun pluginDirectoryFromFile(settings: Settings, file: PsiFile): PluginDirectory? {
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
        return PluginDirectory(first!!)
    }
    return null
}

private fun templateDirectoryFromFile(settings: Settings, originalFile: PsiFile): TopLevelTemplatesDirectory? {
    if (!settings.cake3Enabled) {
        return null
    }
    var dir: PsiDirectory? = originalFile.containingDirectory
    while (dir != null) {
        if (dir.name == "templates") {
            return TopLevelTemplatesDirectory(dir)
        }
        dir = dir.parent
    }
    return null
}

private fun appOrSrcDirectoryFromFile(settings: Settings, file: PsiFile): AppOrSrcDirectory? {
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

