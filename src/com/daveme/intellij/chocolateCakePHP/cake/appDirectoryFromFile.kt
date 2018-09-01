package com.daveme.intellij.chocolateCakePHP.cake

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile

fun appDirectoryFromFile(file: PsiFile): PsiDirectory? {
    var dir: PsiDirectory? = file.containingDirectory
    // @todo determine what happens here when app directory doesn't exist
    while (dir != null) {
        if (dir.name == "app") {
            return dir
        }
        dir = dir.parent
    }
    return null
}
