package com.daveme.chocolateCakePHP.controller

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent
import javax.swing.JList

class NavigateToCreatedFile : GutterIconNavigationHandler<PsiElement> {

    fun getPopupMenuActions(): ActionGroup {
        return DefaultActionGroup(
            object : AnAction("Create View File...") {
                override fun actionPerformed(e: AnActionEvent) {
                    System.out.println("Create something")
                }
            }
        );
    }

    override fun navigate(e: MouseEvent, elt: PsiElement) {
        val popup = JBPopupFactory.getInstance()
            .createListPopup(object : BaseListPopupStep<String>(
                "Create View File", "Create view file3"
            ) {
                override fun getTextFor(value: String?): String {
                    return "Create view file2"
                }

                override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
                    return super.onChosen(selectedValue, finalChoice)
                }
            });
        popup.show(RelativePoint(e))
    }
}