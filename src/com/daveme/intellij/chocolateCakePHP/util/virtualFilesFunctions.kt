package com.daveme.intellij.chocolateCakePHP.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.util.HashSet

fun virtualFileToPsiFile(project: Project, file: VirtualFile): PsiFile? {
    val psiManager = PsiManager.getInstance(project)
    return psiManager.findFile(file)
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