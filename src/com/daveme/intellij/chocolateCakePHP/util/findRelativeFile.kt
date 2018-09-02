package com.daveme.intellij.chocolateCakePHP.util

import com.intellij.openapi.vfs.VfsUtil.findRelativeFile as vfsUtilFindRelativeFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory

fun findRelativeFile(dir: PsiDirectory?, childPath: String): VirtualFile? {
    if (dir == null) {
        return null
    }
    val pathComponents = childPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    return vfsUtilFindRelativeFile(dir.virtualFile, *pathComponents)
}