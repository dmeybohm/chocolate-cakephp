package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.Variable


class ControllerMethodLineMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? = null

    //
    // Add a Cake icon with a list of all the view files next to the action name.
    //
    private fun markerForAllViewFilesInAction(
        project: Project,
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
        return relatedItemLineMarkerInfo(project, actionNames, relatedLookupInfo, element)
    }

    //
    // Add a marker for a single $this->render() call near on the render identifier.
    //
    private fun markerForSingleRenderCallInAction(
        project: Project,
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
            project,
            actionNames,
            relatedLookupInfo,
            element,
            useAltLabel = true
        )
    }

    //
    // Add a marker for a single $this->viewBuilder()->setTemplate() call near the setTemplate identifier.
    //
    private fun markerForSingleViewBuilderCallInAction(
        project: Project,
        relatedLookupInfo: RelatedLookupInfo,
        element: PsiElement,
    ): LineMarkerInfo<*>? {
        if (element.firstChild != null) {
            return null
        }

        // Check if this element is the "this" variable (following the same pattern as render())
        val variable = element.parent as? Variable ?: return null
        if (variable.name != "this") {
            return null
        }

        // Get the viewBuilder() method reference
        // The variable's parent should be a MethodReference
        val viewBuilderMethodRef = variable.parent as? MethodReference ?: return null
        if (viewBuilderMethodRef.name != "viewBuilder") {
            return null
        }

        // Check if there's a setTemplate call in the chain
        // For simple calls: $this->viewBuilder()->setTemplate()
        // For chained calls: $this->viewBuilder()->setTemplatePath()->setTemplate()
        val setTemplateRef = findSetTemplateInChain(viewBuilderMethodRef) ?: return null

        // Get ActionNames for this specific setTemplate call
        val actionNames = actionNamesFromViewBuilderCall(setTemplateRef)
            ?: return null

        return relatedItemLineMarkerInfo(
            project,
            actionNames,
            relatedLookupInfo,
            element,
            useAltLabel = true
        )
    }

    /**
     * Find a setTemplate() call in a method chain starting from viewBuilder().
     * Returns null if no setTemplate is found in the chain.
     */
    private fun findSetTemplateInChain(viewBuilderRef: MethodReference): MethodReference? {
        // Find methods that have viewBuilder or a method in its chain as their classReference
        var currentRef : MethodReference? = viewBuilderRef

        while (currentRef is MethodReference) {
            if (currentRef.name == "setTemplate") {
                return currentRef
            }
            currentRef = currentRef.parent as? MethodReference
        }
        return null
    }

    /**
     * Find the outermost MethodReference in a chain.
     * For: $this->viewBuilder()->setTemplate('x'), returns setTemplate reference
     * For: $this->viewBuilder()->setTemplatePath('y')->setTemplate('x'), returns setTemplate reference
     *
     * Method chaining in PSI works via classReference, not parent-child:
     * - setTemplate.classReference = setTemplatePath
     * - setTemplatePath.classReference = viewBuilder
     */
    private fun findOutermostMethodReference(startRef: MethodReference): MethodReference? {
        var current = startRef

        // Find which MethodReferences in the same scope have this as their classReference
        val containingMethod = PsiTreeUtil.getParentOfType(startRef, Method::class.java) ?: return startRef
        val allMethodRefs = PsiTreeUtil.findChildrenOfType(containingMethod, MethodReference::class.java)

        // Keep finding method references that have the current one as their classReference
        while (true) {
            val nextRef = allMethodRefs.find { it.classReference == current }
            if (nextRef != null) {
                current = nextRef
            } else {
                break
            }
        }

        return current
    }

    private fun relatedItemLineMarkerInfo(
        project: Project,
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
        val allTemplatePaths = allTemplatePathsFromTopSourceDirectory(
            project,
            settings,
            topSourceDirectory
        ) ?: return null
        val allViewPaths = allViewPathsFromController(
            relatedLookupInfo.controllerPath,
            allTemplatePaths,
            settings,
            actionNames
        )
        val files = viewFilesFromAllViewPaths(
            project = relatedLookupInfo.project,
            allTemplatesPaths = allTemplatePaths,
            allViewPaths = allViewPaths,
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

            val allViewFilesMarker = markerForAllViewFilesInAction(project, relatedLookupInfo, element)
            addLineMarkerUnique(result, allViewFilesMarker)

            val renderViewMarker = markerForSingleRenderCallInAction(project, relatedLookupInfo, element)
            addLineMarkerUnique(result, renderViewMarker)

            // Add line markers for ViewBuilder setTemplate calls
            val viewBuilderMarker = markerForSingleViewBuilderCallInAction(project, relatedLookupInfo, element)
            addLineMarkerUnique(result, viewBuilderMarker)
        }
    }
}
