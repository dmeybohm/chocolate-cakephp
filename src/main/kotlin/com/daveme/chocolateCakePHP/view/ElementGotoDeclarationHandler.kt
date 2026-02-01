package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
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

        val stringLiteralPattern = psiElement(StringLiteralExpression::class.java)
            .withParent(
                psiElement(ParameterList::class.java)
                    .withParent(
                        psiElement(MethodReference::class.java)
                            .with(ElementMethodPattern)
                    )
            )
        if (!stringLiteralPattern.accepts(psiElement.context)) {
            return PsiElement.EMPTY_ARRAY
        }

        val contents = (psiElement.context as? StringLiteralExpression)?.contents
            ?: return PsiElement.EMPTY_ARRAY
        val containingFile = psiElement.containingFile

        val topSourceDirectory = topSourceDirectoryFromSourceFile(settings, containingFile)
            ?: return PsiElement.EMPTY_ARRAY
        val allTemplatesPaths = allTemplatePathsFromTopSourceDirectory(project, settings, topSourceDirectory)
            ?: return PsiElement.EMPTY_ARRAY

        // Parse plugin prefix from element path
        val pluginResourcePath = parsePluginResourcePath(contents)

        val allViewPaths = if (pluginResourcePath.pluginName != null) {
            // Plugin-prefixed element: look only in the plugin's template directory
            val pluginConfig = settings.findPluginConfigByName(pluginResourcePath.pluginName)
                ?: return PsiElement.EMPTY_ARRAY
            allViewPathsFromPluginElementPath(
                allTemplatesPaths,
                settings,
                pluginResourcePath.resourcePath,
                pluginConfig
            )
        } else {
            // No plugin prefix: search all template paths as before
            allViewPathsFromElementPath(allTemplatesPaths, settings, contents)
        }

        val files = viewFilesFromAllViewPaths(project, allTemplatesPaths, allViewPaths)

        return files.toTypedArray()
    }

    override fun getActionText(dataContext: DataContext): String? = null
}
