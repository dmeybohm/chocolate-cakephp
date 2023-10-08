package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.CakeIcons
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.PsiElement;

import javax.swing.*;

class CreateViewFileIconRenderer(val element: PsiElement) : GutterIconRenderer() {

    @Override
    override fun getIcon(): Icon {
        return CakeIcons.LOGO
    }

    override fun getPopupMenuActions(): ActionGroup {
        return DefaultActionGroup(
                object : AnAction("Create View File...") {
                    override fun actionPerformed(e: AnActionEvent) {
                        System.out.println("Create something")
                    }
                }
            );
    }

    override fun getClickAction(): AnAction {
        return object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                val popup = JBPopupFactory.getInstance()
                    .createActionGroupPopup(
                        "Actions",
                        getPopupMenuActions(),
                        e.getDataContext(),
                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                        true
                    );
                popup.showInBestPositionFor(e.getDataContext());
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        val otherAsMe = other as? CreateViewFileIconRenderer ?: return false
        if (otherAsMe.element != element) return false
        return false
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }
}