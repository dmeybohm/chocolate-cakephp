package com.daveme.intellij.chocolateCakePHP.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory

fun findRelativeFile(dir: PsiDirectory?, childPath: String): VirtualFile? {
    return if (dir == null) {
        null
    } else com.intellij.openapi.vfs.VfsUtil.findRelativeFile(dir.virtualFile, *childPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
}