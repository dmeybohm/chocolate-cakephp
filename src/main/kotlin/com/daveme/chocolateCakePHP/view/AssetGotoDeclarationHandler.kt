package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.AssetDirectory
import com.daveme.chocolateCakePHP.cake.assetDirectoryFromViewFile
import com.daveme.chocolateCakePHP.findRelativeFile
import com.daveme.chocolateCakePHP.virtualFilesToPsiFiles
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.jetbrains.annotations.Nls
import java.util.HashSet

class AssetGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        if (sourceElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        if (DumbService.getInstance(sourceElement.project).isDumb) {
            return PsiElement.EMPTY_ARRAY
        }
        val project = sourceElement.project
        val settings = Settings.getInstance(project)
        if (!settings.enabled) {
            return PsiElement.EMPTY_ARRAY
        }

        // Check if either pattern matches (string literal or array element)
        if (!AssetMethodPatterns.stringForGotoDeclaration.accepts(sourceElement.context)
            && !AssetMethodPatterns.arrayForGotoDeclaration.accepts(sourceElement.context)) {
            return PsiElement.EMPTY_ARRAY
        }

        // Use PsiTreeUtil to find MethodReference regardless of nesting level
        val stringLiteralArg = sourceElement.context as? StringLiteralExpression ?: return null
        val method = PsiTreeUtil.getParentOfType(stringLiteralArg, MethodReference::class.java) ?: return null

        // Get the parameter list and check position - only navigate in the first parameter
        val parameterList = PsiTreeUtil.getParentOfType(stringLiteralArg, ParameterList::class.java) ?: return null
        val parameters = parameterList.parameters
        val paramIndex = parameters.indexOfFirst { param ->
            PsiTreeUtil.isAncestor(param, stringLiteralArg, false)
        }

        // Only navigate for the first parameter (index 0)
        if (paramIndex != 0) {
            return PsiElement.EMPTY_ARRAY
        }

        // Don't navigate on empty strings
        if (stringLiteralArg.contents.isEmpty()) {
            return PsiElement.EMPTY_ARRAY
        }

        val assetDir = assetDirectoryFromViewFile(
            sourceElement.project,
            settings,
            sourceElement.containingFile.virtualFile
        ) ?: return null
        val virtualFile = extractAssetPathFromMethodCall(assetDir, method, stringLiteralArg)
            ?: return null

        val files = HashSet<VirtualFile>()
        files.add(virtualFile)
        return virtualFilesToPsiFiles(project, files).toTypedArray()
    }

    override fun getActionText(context: DataContext): @Nls(capitalization = Nls.Capitalization.Title) String? = null

    fun extractAssetPathFromMethodCall(
        assetDir: AssetDirectory,
        method: MethodReference,
        stringArg: StringLiteralExpression
    ): VirtualFile? {
        val prefix = when (method.name) {
            "css" -> "css"
            "script" -> "js"
            "image" -> "img"
            else -> return null
        }
        val extension = if (prefix == "img") "" else ".${prefix}"
        return findRelativeFile(assetDir.directory, "${prefix}/${stringArg.contents}${extension}")
    }
}
