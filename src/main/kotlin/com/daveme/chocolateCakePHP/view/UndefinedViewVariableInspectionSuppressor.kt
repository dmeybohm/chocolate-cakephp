package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeViewFile
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement

class UndefinedViewVariableInspectionSuppressor : InspectionSuppressor
{
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != "PhpUndefinedVariableInspection") {
            return false
        }
        val project = element.project
        val settings = Settings.getInstance(project)
        val isViewFile = isCakeViewFile(project, settings, element.containingFile)

        return isViewFile
    }

    override fun getSuppressActions(
        element: PsiElement?,
        toolId: String
    ): Array<SuppressQuickFix> {
        return arrayOf()
    }
}
