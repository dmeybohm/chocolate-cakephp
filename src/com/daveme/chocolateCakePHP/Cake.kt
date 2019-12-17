package com.daveme.chocolateCakePHP

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile

sealed class Cake(val viewDirectory: String, val elementTop: String) {
    fun templatePath(settings: Settings, controllerName: String, controllerAction: String) =
        "$viewDirectory/$controllerName/$controllerAction.${settings.cakeTemplateExtension}"

    fun elementPath(settings: Settings, elementPath: String): String =
        "$viewDirectory/$elementTop/$elementPath.${settings.cakeTemplateExtension}"
}

fun appDirectoryFromFile(settings: Settings, file: PsiFile): PsiDirectory? {
    var dir: PsiDirectory? = file.containingDirectory
    // @todo determine what happens here when app directory doesn't exist
    while (dir != null) {
        if (dir.name == settings.appDirectory || dir.name == "app") {
            return dir
        }
        dir = dir.parent
    }
    return null
}

object CakeThree : Cake(viewDirectory = "Template", elementTop = "Element")
object CakeTwo : Cake(viewDirectory = "View", elementTop = "Elements")