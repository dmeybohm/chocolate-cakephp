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

    private fun getRelatedFiles(file: PsiFile, controllerName: String, element: PsiElement): LineMarkerInfo<PsiElement>? {
        if (element !is Method) {
            return null
        }
        if (!element.access.isPublic) {
            return null
        }
        val nameIdentifier = element.nameIdentifier ?: return null
        val project = file.project
        val settings = Settings.getInstance(project)
        val pluginOrAppDir = pluginOrAppDirectoryFromFile(settings, file)
        val relativeFile = templatePathToVirtualFile(settings, pluginOrAppDir, controllerName, element.name)
                ?: return null

        val targetFile = virtualFileToPsiFile(project, relativeFile) ?: return null
        val targetElement = targetFile.firstChild

        return NavigationGutterIconBuilder
            .create(CakeIcons.LOGO)
            .setTarget(targetElement)
            .createLineMarkerInfo(nameIdentifier)
    }

    private fun addLineMarkerUnique(
        collection: MutableCollection<LineMarkerInfo<PsiElement>>,
        newMarker: LineMarkerInfo<PsiElement>?
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
        list: List<PsiElement>,
        collection: MutableCollection<LineMarkerInfo<PsiElement>>
    ) {
        for (element in list) {
            val settings = Settings.getInstance(element.project)
            if (!settings.enabled) {
                return
            }
            val file = element.containingFile ?: continue
            val virtualFile = file.virtualFile ?: continue
            val controllerName = virtualFile.nameWithoutExtension.controllerBaseName() ?: continue
            val info = getRelatedFiles(file, controllerName, element)
            addLineMarkerUnique(collection, info)
        }
    }
}
