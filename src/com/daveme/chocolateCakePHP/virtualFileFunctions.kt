package com.daveme.chocolateCakePHP

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
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

fun findRelativeFile(dir: PsiDirectory?, childPath: String): VirtualFile? {
    if (dir == null) {
        return null
    }
    val pathPartsList = childPath.split("/".toRegex())
    val pathPartsArray = pathPartsList.dropLastWhile { it.isEmpty() }.toTypedArray()
    return VfsUtil.findRelativeFile(dir.virtualFile, *pathPartsArray)
}

fun templatePathToVirtualFile(
    settings: Settings,
    appDir: PsiDirectory?,
    controllerName: String,
    controllerAction: String
): VirtualFile? {
    val templatePath = CakeThree.templatePath(settings, controllerName, controllerAction)
    var relativeFile = findRelativeFile(appDir, templatePath)
    if (relativeFile == null) {
        val cakeTwoTemplatePath = CakeTwo.templatePath(settings, controllerName, controllerAction)
        relativeFile = findRelativeFile(appDir, cakeTwoTemplatePath)
    }
    return relativeFile
}

fun elementPathToVirtualFile(
    settings: Settings,
    appDir: PsiDirectory?,
    elementPath: String
): VirtualFile? {
    val cakeThreeElementFilename = CakeThree.elementPath(settings, elementPath)
    var relativeFile = findRelativeFile(appDir, cakeThreeElementFilename)
    if (relativeFile == null) {
        val cakeTwoElementFilename = CakeTwo.elementPath(settings, elementPath)
        relativeFile = findRelativeFile(appDir, cakeTwoElementFilename)
    }
    return relativeFile
}