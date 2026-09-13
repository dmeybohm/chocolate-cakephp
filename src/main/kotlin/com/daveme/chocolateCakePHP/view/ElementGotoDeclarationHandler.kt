package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ElementGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(psiElement: PsiElement?, i: Int, editor: Editor): Array<PsiElement>? {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        if (DumbService.getInstance(psiElement.project).isDumb) {
            return PsiElement.EMPTY_ARRAY
        }
        val project = psiElement.project
        val settings = Settings.getInstance(project)
        if (!settings.enabled) {
            return PsiElement.EMPTY_ARRAY
        }

        // Pattern: $this->element('name')
        // The literal may sit inside a ternary / match / parentheses in the first parameter.
        val stringLiteral = psiElement.context as? StringLiteralExpression ?: return PsiElement.EMPTY_ARRAY
        val argument = templateArgumentFromLiteral(stringLiteral) ?: return PsiElement.EMPTY_ARRAY
        val parameterList = argument.parent as? ParameterList ?: return PsiElement.EMPTY_ARRAY
        val methodRef = parameterList.parent as? MethodReference ?: return PsiElement.EMPTY_ARRAY
        if (!ElementMethodPattern.accepts(methodRef, ProcessingContext())) {
            return PsiElement.EMPTY_ARRAY
        }
        if (parameterList.parameters.getOrNull(0) != argument) {
            return PsiElement.EMPTY_ARRAY
        }

        // Navigate to the branch that was clicked, not every branch
        val contents = stringLiteral.contents
        val containingFile = psiElement.containingFile

        val topSourceDirectory = topSourceDirectoryFromSourceFile(settings, containingFile)
            ?: return PsiElement.EMPTY_ARRAY
        val allTemplatesPaths = allTemplatePathsFromTopSourceDirectory(project, settings, topSourceDirectory)
            ?: return PsiElement.EMPTY_ARRAY

        // Parse plugin prefix from element path
        val result = parseAndLookupPlugin(contents, settings)
        val allViewPaths = when (result) {
            is PluginLookupResult.PluginFound -> {
                // Plugin-prefixed element: look only in the plugin's template directory
                allViewPathsFromPluginElementPath(
                    allTemplatesPaths,
                    settings,
                    result.resourcePath,
                    result.pluginConfig
                )
            }
            is PluginLookupResult.NoPlugin -> {
                // No plugin prefix or unrecognized: search all template paths
                allViewPathsFromElementPath(allTemplatesPaths, settings, result.originalPath)
            }
        }

        val resolution = TemplatePathResolution(allViewPaths, result)
        return resolution.toFiles(project, allTemplatesPaths).toTypedArray()
    }

    override fun getActionText(dataContext: DataContext): String? = null
}
