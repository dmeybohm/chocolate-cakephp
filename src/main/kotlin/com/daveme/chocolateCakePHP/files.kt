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
    appOrPluginDir: PsiDirectory?,
    controllerName: String,
    controllerAction: String
): VirtualFile? {
    var relativeFile: VirtualFile? = null
    if (settings.cake3Enabled) {
        val cakeThreeTemplatePath = CakeThree.templatePath(settings, controllerName, controllerAction)
        relativeFile = findRelativeFile(appOrPluginDir, cakeThreeTemplatePath)
        if (relativeFile == null) {
            val cakeFourTemplatePath = CakeFour.templatePath(settings, controllerName, controllerAction)
            relativeFile = findRelativeFile(appOrPluginDir, cakeFourTemplatePath)
        }
    }
    if (relativeFile == null) {
        if (settings.cake2Enabled) {
            val cakeTwoTemplatePath = CakeTwo.templatePath(settings, controllerName, controllerAction)
            relativeFile = findRelativeFile(appOrPluginDir, cakeTwoTemplatePath)
        }
    }
    return relativeFile
}

fun elementPathToVirtualFile(
    settings: Settings,
    pluginOrAppDir: PsiDirectory?,
    elementPath: String
): VirtualFile? {
    var relativeFile: VirtualFile? = null
    if (settings.cake3Enabled) {
        val cakeThreeElementFilename = CakeThree.elementPath(settings, elementPath)
        relativeFile = findRelativeFile(pluginOrAppDir, cakeThreeElementFilename)
        if (relativeFile == null) {
            val cakeFourElementFilename = CakeFour.elementPath(settings, elementPath)
            relativeFile = findRelativeFile(pluginOrAppDir, cakeFourElementFilename)
        }
    }
    if (relativeFile == null) {
        if (settings.cake2Enabled) {
            val cakeTwoElementFilename = CakeTwo.elementPath(settings, elementPath)
            relativeFile = findRelativeFile(pluginOrAppDir, cakeTwoElementFilename)
        }
    }
    return relativeFile
}