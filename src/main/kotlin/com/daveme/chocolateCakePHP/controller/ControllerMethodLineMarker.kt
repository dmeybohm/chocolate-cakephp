package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.Variable


class ControllerMethodLineMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? = null

    //
    // Add a Cake icon with a list of all the view files next to the action name.
    //
    private fun markerForAllViewFilesInAction(
        relatedLookupInfo: RelatedLookupInfo,
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

        val actionNames = actionNamesFromControllerMethod(method)
        return relatedItemLineMarkerInfo(actionNames, relatedLookupInfo, element)
    }

    //
    // Add a marker for a single $this->render() call near on the render identifier.
    //
    private fun markerForSingleRenderCallInAction(
        relatedLookupInfo: RelatedLookupInfo,
        element: PsiElement,
    ): LineMarkerInfo<*>? {
        if (element.firstChild != null) {
            return null
        }
        val variable = element.parent as? Variable ?: return null
        if (variable.name != "this") {
            return null
        }
        val methodReference = variable.parent as? MethodReference ?: return null
        val actionNames = actionNamesFromRenderCall(methodReference)
            ?: return null

        return relatedItemLineMarkerInfo(
            actionNames,
            relatedLookupInfo,
            element,
            useAltLabel = true
        )
    }

    private fun relatedItemLineMarkerInfo(
        actionNames: ActionNames,
        relatedLookupInfo: RelatedLookupInfo,
        element: PsiElement,
        useAltLabel: Boolean = false
    ): LineMarkerInfo<PsiElement>? {
        val settings = relatedLookupInfo.settings
        val topSourceDirectory = topSourceDirectoryFromSourceFile(
            settings,
            relatedLookupInfo.file
        ) ?: return null
        val templatesDirectory = templatesDirectoryFromTopSourceDirectory(
            settings,
            topSourceDirectory
        ) ?: return null

        val templatesDirWithPath = templatesDirWithPath(relatedLookupInfo.project, templatesDirectory)
            ?: return null
        val allViewPaths = allViewPathsFromController(
            relatedLookupInfo.controllerPath,
            templatesDirWithPath,
            settings,
            actionNames
        )
        val files = viewFilesFromAllViewPaths(
            project = relatedLookupInfo.project,
            templatesDirectory = templatesDirectory,
            allViewPaths = allViewPaths
        )

        return if (files.isEmpty()) {
            val emptyTargets = listOf<PsiFile>()

            NavigationGutterIconBuilder
                .create(AllIcons.Actions.AddFile)
                .setTargets(emptyTargets)
                .setTooltipText("Click to create view file")
                .createLineMarkerInfo(element, NavigateToViewPopupHandler(allViewPaths, emptyTargets, useAltLabel))
        } else {
            val filesList = files.toList()
            NavigationGutterIconBuilder
                .create(CakeIcons.LOGO_PNG)
                .setTooltipText("Click to navigate to view file, Ctrl-Click to create")
                .setTargets(filesList)
                .createLineMarkerInfo(element, NavigateToViewPopupHandler(allViewPaths, filesList, useAltLabel))
        }
    }

    private fun addLineMarkerUnique(
        collection: MutableCollection<in LineMarkerInfo<*>>,
        newMarker: LineMarkerInfo<*>?,
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
            val project = element.project
            val settings = Settings.getInstance(project)
            if (!settings.enabled) {
                return
            }
            val file = element.containingFile ?: continue
            val virtualFile = file.virtualFile ?: continue

            val controllerPath = controllerPathFromControllerFile(virtualFile) ?: continue
            val relatedLookupInfo = RelatedLookupInfo(
                project = project,
                file = file,
                settings = settings,
                controllerPath = controllerPath
            )

            val allViewFilesMarker = markerForAllViewFilesInAction(relatedLookupInfo, element)
            addLineMarkerUnique(result, allViewFilesMarker)

            val renderViewMarker = markerForSingleRenderCallInAction(relatedLookupInfo, element)
            addLineMarkerUnique(result, renderViewMarker)
        }
    }
}
