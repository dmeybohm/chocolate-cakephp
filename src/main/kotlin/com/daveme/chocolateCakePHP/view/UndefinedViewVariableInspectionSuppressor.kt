package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.PhpFilesModificationTracker
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableCache
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
        val templatesDir = templatesDirectoryOfViewFile(project, settings, psiFile) ?: return false
        val templateDirVirtualFile = templatesDir.directory
        val relativePath = VfsUtil.getRelativePath(virtualFile, templateDirVirtualFile) ?: return false

        try {
            val filenameKey = ViewFileIndexService.canonicalizeFilenameToKey(templatesDir, settings, relativePath)

            // Use cache for variable lookup
            val cache = project.getService(ViewVariableCache::class.java)
            val phpTracker = project.getService(PhpFilesModificationTracker::class.java)

            // Arm the tracker now that we know this is a CakePHP project
            // This is done lazily to avoid overhead in non-CakePHP projects
            phpTracker.ensureArmed()

            return cache.isVariableDefined(
                psiFile,
                filenameKey,
                variable.name,
                phpTracker
            ) { key, varName ->
                // Lookup function called only on cache miss
                val resultType = ViewVariableIndexService.lookupVariableTypeFromViewPathInSmartReadAction(
                    project,
                    settings,
                    key,
                    varName
                )
                resultType.types.size > 0
            }
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
