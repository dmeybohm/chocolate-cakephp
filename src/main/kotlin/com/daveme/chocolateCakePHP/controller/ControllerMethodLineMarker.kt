package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.CakeIcons
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.Method

class ControllerMethodLineMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? = null

    private fun getRelatedFiles(file: PsiFile, controllerName: String, element: PsiElement): List<LineMarkerInfo<*>> {
        val result = mutableListOf<LineMarkerInfo<*>>()
        if (element !is Method) {
            return result
        }
        if (!element.access.isPublic) {
            return result
        }
        val nameIdentifier = element.nameIdentifier ?: return result
        val project = file.project
        val settings = Settings.getInstance(project)
        val appDirectories = appDirectories(settings, file)
        val relativeFiles = templatePathToVirtualFiles(settings, appDirectories, controllerName, element.name)
        if (relativeFiles.isEmpty()) {
            return result
        }

        val targetFiles = virtualFilesToPsiFiles(project, relativeFiles)
        targetFiles.forEach { targetFile ->
            val targetElement = targetFile.firstChild

            result.add(NavigationGutterIconBuilder
                .create(CakeIcons.LOGO)
                .setTarget(targetElement)
                .createLineMarkerInfo(nameIdentifier)
            )
        }
        return result
    }

    private fun addLineMarkerUnique(
        collection: MutableCollection<LineMarkerInfo<*>>,
        newMarkers: List<LineMarkerInfo<*>>
    ) {
        var result = newMarkers
        if (newMarkers.isEmpty()) {
            return
        }
        for (lineMarkerInfo in collection) {
            val markerElement = lineMarkerInfo as? LineMarkerInfo<*> ?: continue
            val element = markerElement.element ?: continue
            result = result.filter {  element != it.element }
        }
        collection.addAll(newMarkers)
    }

    override fun collectSlowLineMarkers(
        elements: MutableList<PsiElement>,
        result: MutableCollection<LineMarkerInfo<*>>
    ) {
        for (element in elements) {
            val settings = Settings.getInstance(element.project)
            if (!settings.enabled) {
                return
            }
            val file = element.containingFile ?: continue
            val virtualFile = file.virtualFile ?: continue
            val controllerName = virtualFile.nameWithoutExtension.controllerBaseName() ?: continue
            val markers = getRelatedFiles(file, controllerName, element)
            addLineMarkerUnique(result, markers)
        }
    }

}
