package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.cake.AllViewPaths
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent

fun showPsiFilePopup(
    files: List<PsiFile>,
    project: Project,
    point: RelativePoint
) {
    PsiTargetNavigator(
        files.sortedBy { it.virtualFile.path }.toTypedArray(),
    ).createPopup(project, title="Select Target to Navigate")
        .show(point)
}

fun showPsiFilePopupFromEditor(
    files: List<PsiFile>,
    project: Project,
    editor: Editor
) {
    PsiTargetNavigator(
        files.sortedBy { it.virtualFile.path }.toTypedArray(),
    ).createPopup(project, title="Select Target to Navigate")
        .showInBestPositionFor(editor)
}

fun showPsiElementPopupFromEditor(
    places: List<PsiElement>,
    project: Project,
    editor: Editor
) {
    PsiTargetNavigator(
        places.toTypedArray(),
    ).createPopup(project, title="Select Target to Navigate")
        .showInBestPositionFor(editor)
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
