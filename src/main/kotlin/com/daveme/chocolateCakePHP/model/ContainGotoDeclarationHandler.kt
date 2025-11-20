package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.getPossibleTableClasses
import com.daveme.chocolateCakePHP.virtualFilesToPsiFiles
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.jetbrains.annotations.Nls

class ContainGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        if (sourceElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        val project = sourceElement.project
        val settings = Settings.getInstance(project)
        if (!settings.cake3Enabled) {
            return PsiElement.EMPTY_ARRAY
        }

        // Check if either pattern matches (string literal or array element)
        if (!ContainMethodPatterns.stringForGotoDeclaration.accepts(sourceElement.context)
            && !ContainMethodPatterns.arrayForGotoDeclaration.accepts(sourceElement.context)) {
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

        // Extract table name, handling nested associations
        // "Authors.Addresses" → "Authors"
        // "Authors" → "Authors"
        val tableName = stringLiteralArg.contents.split(".", limit = 2)[0]

        if (tableName.isEmpty()) {
            return PsiElement.EMPTY_ARRAY
        }

        // Find the Table class
        val phpIndex = PhpIndex.getInstance(project)
        val tableClasses = phpIndex.getPossibleTableClasses(settings, tableName)

        if (tableClasses.isEmpty()) {
            return PsiElement.EMPTY_ARRAY
        }

        // Convert to PsiElements
        val virtualFiles = tableClasses.mapNotNull { it.containingFile?.virtualFile }.toSet()
        return virtualFilesToPsiFiles(project, virtualFiles).toTypedArray()
    }

    override fun getActionText(context: DataContext): @Nls(capitalization = Nls.Capitalization.Title) String? = null
}
