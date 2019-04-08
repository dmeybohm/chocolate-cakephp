package com.daveme.chocolateCakePHP.navigation

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.appDirectoryFromFile
import com.daveme.chocolateCakePHP.icons.CakeIcons
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.Method

import com.daveme.chocolateCakePHP.cake.controllerBaseName
import com.daveme.chocolateCakePHP.findRelativeFile
import com.daveme.chocolateCakePHP.virtualFileToPsiFile

class ControllerMethodLineMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? = null

    private fun getRelatedFiles(file: PsiFile, controllerName: String, element: PsiElement): LineMarkerInfo<*>? {
        if (element !is Method) {
            return null
        }
        if (!element.access.isPublic) {
            return null
        }
        val project = file.project
        val settings = Settings.getInstance(project)
        val appDir = appDirectoryFromFile(settings, file)
        val templatePath = "View/$controllerName/${element.name}.ctp"
        val relativeFile = findRelativeFile(appDir, templatePath) ?: return null

        val targetFile = virtualFileToPsiFile(project, relativeFile) ?: return null
        val targetElement = targetFile.firstChild

        return NavigationGutterIconBuilder
            .create(CakeIcons.LOGO)
            .setTarget(targetElement)
            .createLineMarkerInfo(element)
    }

    private fun addLineMarkerUnique(collection: MutableCollection<LineMarkerInfo<*>>, newMarker: LineMarkerInfo<*>?) {
        if (newMarker == null) {
            return
        }
        for (lineMarkerInfo in collection) {
            val element = lineMarkerInfo.element ?: return
            val otherElement = newMarker.element
            if (element == otherElement) {
                return
            }
        }
        collection.add(newMarker)
    }

    override fun collectSlowLineMarkers(list: List<PsiElement>, collection: MutableCollection<LineMarkerInfo<*>>) {
        for (element in list) {
            val file = element.containingFile ?: continue
            val virtualFile = file.virtualFile ?: continue
            val controllerName = controllerBaseName(virtualFile.nameWithoutExtension) ?: continue
            val info = getRelatedFiles(file, controllerName, element)
            addLineMarkerUnique(collection, info)
        }
    }
}
