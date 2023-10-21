package com.daveme.chocolateCakePHP.controller

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent

class ShowCreateViewFilePopup(val destinationPath: String) : GutterIconNavigationHandler<PsiElement> {

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
        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Create View File",
                CreateViewFileActionGroup(destinationPath),
                context,
                JBPopupFactory.ActionSelectionAid.NUMBERING,
                true,
            )

        popup.show(RelativePoint(e))
    }

}
