package com.daveme.chocolateCakePHP.controller

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent

class NavigateToViewPopupHandler(
    val destinationPath: String,
    val targets: List<PsiFile>
) : GutterIconNavigationHandler<PsiElement> {

    class CreateViewFileActionGroup(val destinationPath: String) : ActionGroup() {
        override fun getChildren(e: AnActionEvent?): Array<AnAction> {
            return arrayOf(
                CreateViewFileAction(destinationPath, useCustomPath = false),
                CreateViewFileAction(destinationPath, useCustomPath = true)
            )
        }
    }

    override fun navigate(e: MouseEvent, elt: PsiElement?) {
        val context = DataManager.getInstance().getDataContext(e.component)
        val validTargets = validTargets()

        val point = RelativePoint(e)
        if (e.mouseButton == MouseButton.Left && !validTargets.isEmpty()) {
            val project = elt?.project ?: return
            navigateToDestination(project, validTargets, point)
        } else {
            val popup = JBPopupFactory.getInstance()
                .createActionGroupPopup(
                    "Create View File",
                    CreateViewFileActionGroup(destinationPath),
                    context,
                    JBPopupFactory.ActionSelectionAid.NUMBERING,
                    true,
                )

            popup.show(point)
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
                NavigationUtil.getPsiElementPopup(
                    files.toTypedArray(),
                    "Select Target To Navigate"
                ).show(point)
            }
        }
    }
}
