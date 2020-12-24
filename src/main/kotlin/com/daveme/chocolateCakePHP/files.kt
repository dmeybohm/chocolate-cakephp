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

fun templatePathToVirtualFiles(
    settings: Settings,
    appDirectories: List<PsiDirectory>,
    controllerName: String,
    controllerAction: String
): List<VirtualFile> {
    val result = mutableListOf<VirtualFile>()
    if (settings.cake3Enabled) {
        val templatePath = CakeThree.templatePath(settings, controllerName, controllerAction)
        appDirectories.forEach { appDir ->
            val relativeFile = findRelativeFile(appDir, templatePath)
            if (relativeFile != null) {
                result.add(relativeFile)
            }
        }
    }
    if (settings.cake2Enabled) {
        val cakeTwoTemplatePath = CakeTwo.templatePath(settings, controllerName, controllerAction)
        appDirectories.forEach { appDir ->
            val relativeFile = findRelativeFile(appDir, cakeTwoTemplatePath)
            if (relativeFile != null) {
                result.add(relativeFile)
            }
        }
    }
    return result
}

fun elementPathToVirtualFiles(
    settings: Settings,
    appDirectories: List<PsiDirectory>,
    elementPath: String
): List<VirtualFile> {
    val result = mutableListOf<VirtualFile>()
    if (settings.cake3Enabled) {
        val cakeThreeElementFilename = CakeThree.elementPath(settings, elementPath)
        appDirectories.forEach { appDir ->
            val relativeFile = findRelativeFile(appDir, cakeThreeElementFilename)
            if (relativeFile != null) {
                result.add(relativeFile)
            }
        }
    }
    if (settings.cake2Enabled) {
        val cakeTwoElementFilename = CakeTwo.elementPath(settings, elementPath)
        appDirectories.forEach { appDir ->
            val relativeFile = findRelativeFile(appDir, cakeTwoElementFilename)
            if (relativeFile != null) {
                result.add(relativeFile)
            }
        }
    }
    return result
}