package com.daveme.chocolateCakePHP

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.io.IOException
import java.util.HashSet

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

fun findRelativeFile(dir: VirtualFile?, childPath: String): VirtualFile? {
    if (dir == null) {
        return null
    }
    val pathPartsList = childPath.split("/".toRegex())
    val pathPartsArray = pathPartsList.filter { it.isNotEmpty() }.toTypedArray()
    return VfsUtil.findRelativeFile(dir, *pathPartsArray)
}

fun pathRelativeToProject(project: Project, virtualFile: VirtualFile): String? {
    val projectVirtualFile = project.guessProjectDir() ?: return null
    var dir : VirtualFile? = virtualFile.parent
    val pathNames = mutableListOf(virtualFile.name)
    while (dir != null && dir != projectVirtualFile) {
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
