package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.cake.AllViewPaths
import com.daveme.chocolateCakePHP.cake.CakeIcons
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.PhpPresentationUtil
import com.jetbrains.php.lang.psi.elements.Method
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.SwingUtilities


class CakePhpNavigationPresentationProvider : PsiTargetPresentationRenderer<PsiElement>() {
    override fun getContainerText(element: PsiElement): String? {
        val file = element.containingFile
        if (file != null) {
            val virtualFile = file.virtualFile
            if (virtualFile != null)
                return PhpPresentationUtil.getPresentablePathForFile(virtualFile, element.project) //virtualFile.presentableName
        }
        return super.getContainerText(element)
    }

    override fun getElementText(element: PsiElement): String {
        val file = element.containingFile
        if (file != null) {
            val virtualFile = file.virtualFile
            if (virtualFile != null) {
                val path = virtualFile.path
                if (path.contains("/Controller/")) {
                    // Get containing method if call is inside a controller:
                    val method = PsiTreeUtil.getParentOfType(element, Method::class.java)
                    if (method != null) {
                        return super.getElementText(method)
                    }
                }
            }
        }
        return super.getElementText(element)
    }

    override fun getIcon(element: PsiElement): Icon {
        val path = element.containingFile.virtualFile?.path
        return if (path == null) {
            PhpIcons.FUNCTION
        } else if (path.contains("/Controller/")) {
            CakeIcons.LOGO_SVG
        } else {
            PhpIcons.PHP_FILE
        }
    }
}

fun MouseEvent.getScreenPoint(): Point? {
    // Convert the point relative to the component to a screen point
    val point = this.point
    val component = this.component
    return if (component != null) {
        SwingUtilities.convertPointToScreen(point, component)
        point
    } else {
        null
    }
}

fun showPsiFilePopup(
    files: List<PsiFile>,
    project: Project,
    point: RelativePoint
) {
    val elements = files
        .sortedBy { it.virtualFile.path }
        .toTypedArray<PsiElement>()
    PsiTargetNavigator(elements)
        .presentationProvider(CakePhpNavigationPresentationProvider())
        .createPopup(project, title="Select Target to Navigate")
        .show(point)
}

fun showPsiFilePopupFromEditor(
    files: List<PsiFile>,
    project: Project,
    editor: Editor
) {
    val elements = files
        .sortedBy { it.virtualFile.path }
        .toTypedArray<PsiElement>()
    PsiTargetNavigator(elements)
        .presentationProvider(CakePhpNavigationPresentationProvider())
        .createPopup(project, title="Select Target to Navigate")
        .showInBestPositionFor(editor)
}

fun showPsiElementPopupFromEditor(
    places: List<PsiElement>,
    project: Project,
    editor: Editor,
    relativePoint: RelativePoint?
) {
    val popup = PsiTargetNavigator(places.toTypedArray())
        .presentationProvider(CakePhpNavigationPresentationProvider())
        .createPopup(project, title="Select Target to Navigate")
    if (relativePoint != null) {
        popup.show(relativePoint)
    } else {
        popup.showInBestPositionFor(editor)
    }
}

fun createViewActionPopupFromAllViewPaths(
    allViewPaths: AllViewPaths,
    useAltLabel: Boolean = false,
): DefaultActionGroup {
    val defaultActionGroup = DefaultActionGroup()
    val defaultOptionTitle = if (useAltLabel)
        "Create ${allViewPaths.defaultViewPath.altLabel}"
    else
        "Create ${allViewPaths.defaultViewPath.label}"
    defaultActionGroup.add(CreateViewFileAction(
        title = defaultOptionTitle,
        destinationPath = allViewPaths.defaultViewPath.fullPath,
        allowEdit = false
    ))
    allViewPaths.otherViewPaths.map { otherViewPath ->
        defaultActionGroup.add(CreateViewFileAction(
            title = "Create ${otherViewPath.label}",
            destinationPath = otherViewPath.fullPath,
            allowEdit = false
        ))
    }
    defaultActionGroup.addSeparator()
    defaultActionGroup.add(CreateViewFileAction(
        title = "Create Custom View File",
        destinationPath = allViewPaths.defaultViewPath.fullPath,
        allowEdit = true
    ))
    allViewPaths.dataViewPaths.map { dataViewPath ->
        defaultActionGroup.add(CreateViewFileAction(
            title = "Create ${dataViewPath.label} View File",
            destinationPath = dataViewPath.fullPath,
            allowEdit = false
        ))
    }
    return defaultActionGroup
}

class NavigateToViewPopupHandler(
    val allViewPaths: AllViewPaths,
    val targets: List<PsiFile>,
    val useAltLabel: Boolean = false,
) : GutterIconNavigationHandler<PsiElement> {

    override fun navigate(e: MouseEvent, elt: PsiElement?) {
        val context = DataManager.getInstance().getDataContext(e.component)
        val validTargets = validTargets()

        val point = RelativePoint(e)

        if (e.mouseButton == MouseButton.Left) {
            val project = elt?.project ?: return
            val hasTargets = !validTargets.isEmpty()
            if (e.isControlDown || !hasTargets) {
                val popup = JBPopupFactory.getInstance()
                    .createActionGroupPopup(
                        "Create View File",
                        createViewActionPopupFromAllViewPaths(allViewPaths, useAltLabel),
                        context,
                        JBPopupFactory.ActionSelectionAid.NUMBERING,
                        true,
                    )
                popup.show(point)
            } else if (hasTargets) {
                navigateToDestination(project, validTargets, point)
            }
        }
    }

    private fun validTargets(): List<PsiFile> {
        return targets.filter { it.isValid }
    }

    private fun navigateToDestination(
        project: Project,
        files: List<PsiFile>,
        point: RelativePoint
    ) {
        when (files.size) {
            1 -> {
                val first = files.first().virtualFile
                FileEditorManager.getInstance(project).openFile(first, true)
            }
            else -> {
                showPsiFilePopup(files, project, point)
            }
        }
    }
}
