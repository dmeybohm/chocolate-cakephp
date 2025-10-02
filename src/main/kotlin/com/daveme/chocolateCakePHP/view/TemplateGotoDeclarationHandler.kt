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

class TemplateGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        psiElement: PsiElement?,
        i: Int,
        editor: Editor
    ): Array<PsiElement>? {
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
                            .with(RenderMethodPattern)
                    )
            )
        if (!stringLiteralPattern.accepts(psiElement.context)) {
            return PsiElement.EMPTY_ARRAY
        }
        val containingFile = psiElement.containingFile
        val virtualFile = containingFile.virtualFile
        val controllerPath = controllerPathFromControllerFile(virtualFile)
            ?: return PsiElement.EMPTY_ARRAY

        val method = findParentWithClass(psiElement, MethodReference::class.java)
                as? MethodReference ?: return PsiElement.EMPTY_ARRAY
        if (method.name != "render") {
            return PsiElement.EMPTY_ARRAY
        }

        val actionNames = actionNamesFromRenderCall(method)
            ?: return PsiElement.EMPTY_ARRAY
        val topSourceDirectory = topSourceDirectoryFromSourceFile(
            settings,
            containingFile
        ) ?: return null
        val allTemplatesPaths = allTemplatePathsFromTopSourceDirectory(
            project,
            settings,
            topSourceDirectory
        ) ?: return null

        val allViewPaths = allViewPathsFromController(
            controllerPath,
            allTemplatesPaths,
            settings,
            actionNames
        )
        val files = viewFilesFromAllViewPaths(
            project = project,
            allTemplatesPaths = allTemplatesPaths,
            allViewPaths = allViewPaths
        )
        return files.toTypedArray()
    }

    override fun getActionText(dataContext: DataContext): String? = null
}

