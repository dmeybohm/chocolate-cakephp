package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Function
import com.intellij.util.NotNullFunction
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import java.util.*


data class RelatedLookupInfo(
    val project: Project,
    val settings: Settings,
    val controllerName: String,
    val file: PsiFile,
)

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

        // Collect $this->render("some_file") calls:
        val renderCalls = PsiTreeUtil.findChildrenOfAnyType(method, false, MethodReference::class.java)
                as Collection<MethodReference>
        val actionNames: List<String> = renderCalls.mapNotNull {
            if (it.name != "render") {
                return@mapNotNull null
            }
            val firstParameter = it.parameterList?.getParameter(0) as? StringLiteralExpression
                ?: return@mapNotNull null
            return@mapNotNull firstParameter.contents
        } + listOf(method.name)

        return relatedItemLineMarkerInfo(actionNames, relatedLookupInfo, element)
    }

    //
    // Add a marker for a single $this->render() call near on the render identifier.
    //
    private fun markerForSingleRenderCallInAction(
        relatedLookupInfo: RelatedLookupInfo,
        element: PsiElement
    ): LineMarkerInfo<*>? {
        if (element.firstChild != null) {
            return null
        }
        val variable = element.parent as? Variable ?: return null
        if (variable.name != "this") {
            return null
        }
        val methodReference = variable.parent as? MethodReference ?: return null
        if (methodReference.name != "render") {
            return null
        }
        val firstParameter = methodReference.parameterList?.getParameter(0) as? StringLiteralExpression
            ?: return null

        return relatedItemLineMarkerInfo(listOf(firstParameter.contents), relatedLookupInfo, element)
    }
    
    private fun relatedItemLineMarkerInfo(
        actionNames: List<String>,
        relatedLookupInfo: RelatedLookupInfo,
        element: PsiElement
    ): LineMarkerInfo<PsiElement>? {
        val settings = relatedLookupInfo.settings
        val pluginOrAppDir = topSourceDirectoryFromFile(settings, relatedLookupInfo.file)
            ?: return null

        val fileExtensions = relatedLookupInfo.settings.viewFileExtensions

        // Create one file for each of the file extensions that match the naming convention:
        val files = actionNames.map { controllerAction ->
            fileExtensions.mapNotNull { fileExtension ->
                templatePathToVirtualFile(
                    relatedLookupInfo.settings,
                    pluginOrAppDir,
                    relatedLookupInfo.controllerName,
                    fileExtension + "/" + controllerAction
                )
            } + listOfNotNull(
                templatePathToVirtualFile(
                    relatedLookupInfo.settings, pluginOrAppDir, relatedLookupInfo.controllerName, controllerAction
                )
            )
        }.flatMap { it }

        if (files.size == 0) {
            // todo handle cake2 vs cake3 vs cake4:
            val defaultViewFile = "${pluginOrAppDir.virtualFile.path}/Template/${relatedLookupInfo.controllerName}/${actionNames.last()}.${settings.cakeTemplateExtension}"

            val markerInfo = LineMarkerInfo(
                element,
                element.textRange,
                CakeIcons.LOGO,
                { e: PsiElement? -> "Click for actions" },  // Tooltip text
                NavigateToCreatedFile(),
                GutterIconRenderer.Alignment.CENTER,
                NameProvider(),
            )
            return markerInfo

//            val target = element.containingFile.virtualFile
//            val targets = virtualFilesToPsiFiles(relatedLookupInfo.project, arrayListOf(target))
//            return NavigationGutterIconBuilder
//                .create(
//                    CakeIcons.LOGO,
//                    ContainerUtil::createMaybeSingletonList,
//                    CustomGotoRelatedItemProvider(relatedLookupInfo.project, defaultViewFile)
//                )
//                .setTooltipText("Click to create corresponding view file")
//                .setTargets(targets)
//                .createLineMarkerInfo(element)
        } else {
            val targetFiles = virtualFilesToPsiFiles(relatedLookupInfo.project, files)
            return NavigationGutterIconBuilder
                .create(CakeIcons.LOGO)
                .setTooltipText("Click to navigate to view file")
                .setTargets(targetFiles)
                .createLineMarkerInfo(element)
        }
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
            val project = element.project
            val settings = Settings.getInstance(project)
            if (!settings.enabled) {
                return
            }
            val file = element.containingFile ?: continue
            val virtualFile = file.virtualFile ?: continue

            val relatedLookupInfo = RelatedLookupInfo(
                project = project,
                file = file,
                settings = settings,
                controllerName = virtualFile.nameWithoutExtension.controllerBaseName() ?: continue,
            )

            val allViewFilesMarker = markerForAllViewFilesInAction(relatedLookupInfo, element)
            addLineMarkerUnique(result, allViewFilesMarker)

            val renderViewMarker = markerForSingleRenderCallInAction(relatedLookupInfo, element)
            addLineMarkerUnique(result, renderViewMarker)
        }
    }

}

class CustomGotoRelatedItemProvider(val project: Project, val targetFilename: String) :
    NotNullFunction<PsiFile, MutableCollection<out GotoRelatedItem>> {
    override fun `fun`(element: PsiFile?): MutableCollection<out GotoRelatedItem> {
        val createFile = PsiFileFactory.getInstance(project)
            .createFileFromText(targetFilename, PhpFileType.INSTANCE, "<?php\n")
        return arrayListOf(GotoRelatedItem(createFile))
    }
}