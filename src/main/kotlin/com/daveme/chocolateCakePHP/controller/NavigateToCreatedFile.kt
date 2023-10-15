package com.daveme.chocolateCakePHP.controller

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent


class NavigateToCreatedFile : GutterIconNavigationHandler<PsiElement> {
    override fun navigate(e: MouseEvent, elt: PsiElement?) {
        val popup = JBPopupFactory.getInstance()
            .createConfirmation("Create view file", "Create view file", "Cancel",
                Runnable() {
                   System.out.println("Running....")
                },
                0
            )

//        val step = BaseListPopupStep("Create view file", listOf("Create view file"))
//        val popup = JBPopupFactory.getInstance()
//            .createListPopup(step)
//            .addListSelectionListener(object {
//                k
//            })

        popup.show(RelativePoint(e));
    }
}
