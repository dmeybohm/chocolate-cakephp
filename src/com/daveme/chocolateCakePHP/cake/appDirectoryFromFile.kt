package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile

fun appDirectoryFromFile(settings: Settings, file: PsiFile): PsiDirectory? {
    var dir: PsiDirectory? = file.containingDirectory
    // @todo determine what happens here when app directory doesn't exist
    while (dir != null) {
        if (dir.name == settings.appDirectory) {
            return dir
        }
        dir = dir.parent
    }
    return null
}
