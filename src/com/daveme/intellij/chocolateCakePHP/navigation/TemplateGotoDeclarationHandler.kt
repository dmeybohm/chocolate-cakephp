package com.daveme.intellij.chocolateCakePHP.navigation

import com.daveme.intellij.chocolateCakePHP.cake.appDirectoryFromFile
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.util.HashSet

import com.daveme.intellij.chocolateCakePHP.cake.controllerBaseName
import com.daveme.intellij.chocolateCakePHP.util.findRelativeFile
import com.daveme.intellij.chocolateCakePHP.util.virtualFilesToPsiFiles

class TemplateGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(psiElement: PsiElement?, i: Int, editor: Editor): Array<PsiElement>? {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        val project = psiElement.project
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
        val controllerName = controllerBaseName(filename) ?: return PsiElement.EMPTY_ARRAY

        val appDir = appDirectoryFromFile(containingFile)
        val templatePath = String.format("View/%s/%s.ctp", controllerName, psiElement.text)
        val relativeFile = findRelativeFile(appDir, templatePath) ?: return PsiElement.EMPTY_ARRAY

        val files = HashSet<VirtualFile>()
        files.add(relativeFile)
        return virtualFilesToPsiFiles(project, files).toTypedArray()
    }

    override fun getActionText(dataContext: DataContext): String? {
        return null
    }

}

