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

        val filenameKey = ViewFileIndexService.canonicalizeFilenameToKey(templatesDir, settings, relativePath)
        val fileList = ViewFileIndexService.referencingElements(project, filenameKey)

        // Handle render call linkages (all files with `$this->render` and the variable defined
        // either with $this->set() in controllers or assignments in view files):
        try {
            fileList.forEach { elementAndPath ->
                if (elementAndPath.nameWithoutExtension.isAnyControllerClass()) {
                    val controllerKey = ViewVariableIndexService.controllerKeyFromElementAndPath(elementAndPath)
                        ?: return@forEach
                    if (ViewVariableIndexService.variableIsSetByController(project, controllerKey, variable.name)) {
                        return true
                    }
                } else {
                    val viewKey = ViewVariableIndexService.viewKeyFromElementAndPath(elementAndPath)
                    if (ViewVariableIndexService.variableIsSetForView(project, viewKey, variable.name)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            println("Exception: ${e.message}")
            return false
        }

        return false
    }

    override fun getSuppressActions(
        element: PsiElement?,
        toolId: String
    ): Array<SuppressQuickFix> {
        return arrayOf()
    }
}
