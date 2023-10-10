package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.CakeIcons
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.Method

class ControllerMethodLineMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? = null

    private fun getRelatedFiles(file: PsiFile, controllerName: String, element: PsiElement): LineMarkerInfo<*>? {
        if (element !is Method) {
            return null
        }
        if (!element.access.isPublic) {
            return null
        }
        val nameIdentifier = element.nameIdentifier ?: return null
        val project = file.project
        val settings = Settings.getInstance(project)
        val pluginOrAppDir = topSourceDirectoryFromFile(settings, file)
        val controllerAction = element.name
        val fileExtensions = settings.viewFileExtensions

        // Create one file for each of the file extensions:
        val relativeFiles = fileExtensions.mapNotNull { fileExtension ->
            templatePathToVirtualFile(settings, pluginOrAppDir, controllerName, fileExtension + "/" + controllerAction)
        } + listOfNotNull(
            templatePathToVirtualFile(settings, pluginOrAppDir, controllerName, controllerAction)
        )
        if (relativeFiles.size == 0) {
            return null
        }

        val targetFiles = virtualFilesToPsiFiles(project, relativeFiles)
        return NavigationGutterIconBuilder
            .create(CakeIcons.LOGO)
            .setTooltipText("Click to navigate to view file")
            .setTargets(targetFiles)
            .createLineMarkerInfo(nameIdentifier)
    }

    private fun addLineMarkerUnique(
        collection: MutableCollection<in LineMarkerInfo<*>>,
        newMarker: LineMarkerInfo<*>?
    ) {
        if (newMarker == null) {
            return
        }
        for (lineMarkerInfo in collection) {
            val markerElement = lineMarkerInfo as? LineMarkerInfo<*> ?: continue
            val element = markerElement.element ?: return
            val otherElement = newMarker.element
            if (element == otherElement) {
                return
            }
        }
        collection.add(newMarker)
    }

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        for (element in elements) {
            val settings = Settings.getInstance(element.project)
            if (!settings.enabled) {
                return
            }
            val file = element.containingFile ?: continue
            val virtualFile = file.virtualFile ?: continue
            val controllerName = virtualFile.nameWithoutExtension.controllerBaseName() ?: continue
            val info = getRelatedFiles(file, controllerName, element)
            addLineMarkerUnique(result, info)
        }
    }

}
