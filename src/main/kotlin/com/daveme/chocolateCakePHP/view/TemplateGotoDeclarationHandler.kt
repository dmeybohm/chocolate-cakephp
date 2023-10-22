package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.templatePathToVirtualFile
import com.daveme.chocolateCakePHP.cake.templatesDirectoryFromViewFile
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.util.HashSet

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

        val controllerName = filename.controllerBaseName() ?: return PsiElement.EMPTY_ARRAY

        val templatesDir = templatesDirectoryFromViewFile(psiElement.project, settings, containingFile)
            ?: return PsiElement.EMPTY_ARRAY
        val relativeFile = templatePathToVirtualFile(settings, templatesDir, controllerName, psiElement.text)
            ?: return PsiElement.EMPTY_ARRAY

        val files = HashSet<VirtualFile>()
        files.add(relativeFile)
        return virtualFilesToPsiFiles(project, files).toTypedArray()
    }

    override fun getActionText(dataContext: DataContext): String? = null
}

