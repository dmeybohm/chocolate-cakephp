package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.findNavigableControllerMethod
import com.daveme.chocolateCakePHP.cake.templatesDirectoryFromViewFile
import com.daveme.chocolateCakePHP.isAnyControllerClass
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.daveme.chocolateCakePHP.view.viewfileindex.elementAndPathFromMethodAndControllerName
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
        val pathParts = relativePath.split("/")
        if (pathParts.size <= 1) {
            return false
        }
        val potentialControllerName = pathParts[0]

        val filenameKey = ViewFileIndexService.canonicalizeFilenameToKey(relativePath, settings)
        val fileList = ViewFileIndexService.referencingElements(project, filenameKey)
        val viewFileName = virtualFile.nameWithoutExtension

        // Handle render calls:
        fileList.forEach { elementAndPath ->
            if (elementAndPath.path.isAnyControllerClass()) {
                val controllerKey = ViewVariableIndexService.controllerKeyFromElementAndPath(elementAndPath)
                    ?: return@forEach
                val variables = ViewVariableIndexService.referencingVariables(project, controllerKey)
                if (variables.contains(variable.name)) {
                    return true
                }
            } else {
                // todo look up view key
            }
        }

        // Handle implicit controller access:
        val controllerMethod = findNavigableControllerMethod(
            project,
            settings,
            templatesDir,
            potentialControllerName,
            viewFileName
        ) ?: return false

        val elementAndPath = elementAndPathFromMethodAndControllerName(
            controllerMethod,
            potentialControllerName,
        ) ?: return false
        val controllerKey = ViewVariableIndexService.controllerKeyFromElementAndPath(elementAndPath)
            ?: return false

        val variables = ViewVariableIndexService.referencingVariables(project, controllerKey)
        return variables.contains(variable.name)
    }

    override fun getSuppressActions(
        element: PsiElement?,
        toolId: String
    ): Array<SuppressQuickFix> {
        return arrayOf()
    }
}
