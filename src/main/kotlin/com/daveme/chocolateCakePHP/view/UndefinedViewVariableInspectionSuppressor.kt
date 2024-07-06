package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.templatesDirectoryFromViewFile
import com.daveme.chocolateCakePHP.isAnyControllerClass
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndexService
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.Variable


class UndefinedViewVariableInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != "PhpUndefinedVariableInspection") {
            return false
        }
        val variable = element as? Variable ?: return false
        val project = element.project
        val settings = Settings.getInstance(project)
        if (!settings.enabled) return false
        return findVariable(project, settings, variable)
    }

    private fun findVariable(
        project: Project,
        settings: Settings,
        variable: Variable
    ): Boolean {
        val psiFile = variable.containingFile
        val virtualFile = psiFile?.virtualFile ?: return false
        val templatesDir = templatesDirectoryFromViewFile(project, settings, psiFile) ?: return false
        val templateDirVirtualFile = templatesDir.psiDirectory.virtualFile
        val relativePath = VfsUtil.getRelativePath(virtualFile, templateDirVirtualFile) ?: return false

        try {
            val resultType = ViewVariableIndexService.lookupVariableTypeFromViewPath(
                project,
                settings,
                templatesDir,
                relativePath,
                variable.name
            )
            return resultType.types.size > 0
        } catch (e: Exception) {
            println("Exception: ${e.message}")
            return false
        }
    }

    override fun getSuppressActions(
        element: PsiElement?,
        toolId: String
    ): Array<SuppressQuickFix> {
        return arrayOf()
    }
}
