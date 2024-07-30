package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class TemplateGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(psiElement: PsiElement?, i: Int, editor: Editor): Array<PsiElement>? {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        val project = psiElement.project
        val settings = Settings.getInstance(project)
        if (!settings.enabled) {
            return PsiElement.EMPTY_ARRAY
        }
        if (!PlatformPatterns
                .psiElement(StringLiteralExpression::class.java)
                .withLanguage(PhpLanguage.INSTANCE)
                .accepts(psiElement.context)
        ) {
            return PsiElement.EMPTY_ARRAY
        }
        val containingFile = psiElement.containingFile
        val virtualFile = containingFile.virtualFile
        val filename = virtualFile.nameWithoutExtension

        if (!isCakeControllerFile(containingFile)) {
            return PsiElement.EMPTY_ARRAY
        }

        val method = findParentWithClass(psiElement, MethodReference::class.java)
                as? MethodReference ?: return PsiElement.EMPTY_ARRAY
        if (method.name != "render") {
            return PsiElement.EMPTY_ARRAY
        }

        val controllerName = filename.controllerBaseName() ?: return PsiElement.EMPTY_ARRAY
        val actionNames = actionNamesFromRenderCall(method)
            ?: return PsiElement.EMPTY_ARRAY
        val topSourceDirectory = topSourceDirectoryFromSourceFile(
            settings,
            containingFile
        ) ?: return null
        val templatesDirectory = templatesDirectoryFromTopSourceDirectory(
            settings,
            topSourceDirectory
        ) ?: return null

        val templatesDirWithPath = templatesDirWithPath(project, templatesDirectory)
            ?: return null
        val allViewPaths = allViewPathsFromController(
            controllerName,
            templatesDirWithPath,
            settings,
            actionNames
        )
        val files = viewFilesFromAllViewPaths(
            project = project,
            templatesDirectory = templatesDirectory,
            allViewPaths = allViewPaths
        )
        return files.toTypedArray()
    }

    override fun getActionText(dataContext: DataContext): String? = null
}

