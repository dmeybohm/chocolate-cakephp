package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.cake.AllViewPaths
import com.daveme.chocolateCakePHP.createViewActionPopupFromAllViewPaths
import com.daveme.chocolateCakePHP.showPsiFilePopup
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.ide.DataManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent


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
