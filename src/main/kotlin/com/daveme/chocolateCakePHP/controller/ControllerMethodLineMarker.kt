package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.CakeIcons
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ControllerMethodLineMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? = null

    private fun calculateRelatedFiles(
        file: PsiFile,
        controllerName: String,
        element: PsiElement
    ): LineMarkerInfo<*>? {
        if (element.firstChild != null) {
            return null
        }
        val method = element.parent
        if (method !is Method) {
            return null
        }
        if (method.nameIdentifier != element) {
            return null
        }
        if (!method.access.isPublic) {
            return null
        }
        val project = file.project
        val settings = Settings.getInstance(project)
        val pluginOrAppDir = topSourceDirectoryFromFile(settings, file)

        // Collect $this->render("some_file") calls:
        val renderCalls = PsiTreeUtil.findChildrenOfAnyType(method, MethodReference::class.java)
                as Collection<MethodReference>
        val actionNames: List<String> = renderCalls.mapNotNull {
            if (it.name != "render") {
                return@mapNotNull null
            }
            val firstParameter = it.parameterList?.getParameter(0) as? StringLiteralExpression
                ?: return@mapNotNull null
            return@mapNotNull firstParameter.contents
        } + listOf(method.name)

        val fileExtensions = settings.viewFileExtensions

        // Create one file for each of the file extensions that match the naming convention:
        val files = actionNames.map { controllerAction ->
            fileExtensions.mapNotNull { fileExtension ->
                templatePathToVirtualFile(
                    settings,
                    pluginOrAppDir,
                    controllerName,
                    fileExtension + "/" + controllerAction
                )
            } + listOfNotNull(
                templatePathToVirtualFile(settings, pluginOrAppDir, controllerName, controllerAction)
            )
        }.flatMap { it -> it }

        if (files.size == 0) {
            return null
        }

        val targetFiles = virtualFilesToPsiFiles(project, files)
        return NavigationGutterIconBuilder
            .create(CakeIcons.LOGO)
            .setTooltipText("Click to navigate to view file")
            .setTargets(targetFiles)
            .createLineMarkerInfo(element)
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

            // Add files that match by the name of the action:
            val relatedFiles = calculateRelatedFiles(file, controllerName, element)
            addLineMarkerUnique(result, relatedFiles)
        }
    }

}
