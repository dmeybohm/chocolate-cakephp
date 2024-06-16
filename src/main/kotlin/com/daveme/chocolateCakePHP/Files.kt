package com.daveme.chocolateCakePHP

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.io.IOException
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
    val pathPartsArray = pathPartsList.filter { it.isNotEmpty() }.toTypedArray()
    return VfsUtil.findRelativeFile(dir.virtualFile, *pathPartsArray)
}

fun pathRelativeToProject(project: Project, psiDirectory: PsiDirectory): String? {
    val projectVirtualFile = project.guessProjectDir() ?: return null
    val projectPsiDirectory = virtualFileToPsiDirectory(project, projectVirtualFile) ?: return null
    var dir = psiDirectory.parent
    val pathNames = mutableListOf(psiDirectory.name)
    while (dir != null && dir != projectPsiDirectory) {
        pathNames.add(dir.name)
        dir = dir.parent
    }
    pathNames.reverse()
    return pathNames.joinToString("/")
}

fun createDirectoriesIfMissing(relativePath: String): Boolean {
    try {
        VfsUtil.createDirectoryIfMissing(relativePath)
    } catch (e: IOException) {
        return false
    }

    return true
}
