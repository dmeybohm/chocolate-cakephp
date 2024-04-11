package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeViewFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

class NavigateToController : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        val project = e.project ?: return

        val settings = Settings.getInstance(project)
        if (!settings.enabled) {
            return
        }

        val psiFile = getPsiFile(project, e) ?: return
        val enabled = isCakeViewFile(project, settings, psiFile)
        e.presentation.isEnabledAndVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        println("do it")
    }

    private fun getPsiFile(project: Project, e: AnActionEvent): PsiFile? {
        val virtualFile = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile)
    }
}
