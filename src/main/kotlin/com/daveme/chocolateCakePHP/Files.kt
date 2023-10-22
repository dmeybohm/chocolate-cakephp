package com.daveme.chocolateCakePHP

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.util.HashSet

fun virtualFileToPsiDirectory(project: Project, file: VirtualFile): PsiDirectory? {
    val psiManager = PsiManager.getInstance(project)
    return psiManager.findDirectory(file)
}

fun virtualFilesToPsiFiles(project: Project, files: Collection<VirtualFile>): Collection<PsiFile> {

    val psiFiles = HashSet<PsiFile>()
    val psiManager = PsiManager.getInstance(project)

    for (file in files) {
        val psiFile = psiManager.findFile(file)
        if (psiFile != null) {
            psiFiles.add(psiFile)
        }
    }

    return psiFiles
}

fun findRelativeFile(dir: PsiDirectory?, childPath: String): VirtualFile? {
    if (dir == null) {
        return null
    }
    val pathPartsList = childPath.split("/".toRegex())
    val pathPartsArray = pathPartsList.dropLastWhile { it.isEmpty() }.toTypedArray()
    return VfsUtil.findRelativeFile(dir.virtualFile, *pathPartsArray)
}
