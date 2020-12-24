package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ElementGotoDeclarationHandler : GotoDeclarationHandler {

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
        val appDirectories = appDirectories(settings, containingFile)
        val relativeFiles = elementPathToVirtualFiles(settings, appDirectories, psiElement.text)
        if (relativeFiles.isEmpty()) {
            return PsiElement.EMPTY_ARRAY
        }

        return virtualFilesToPsiFiles(project, relativeFiles).toTypedArray()
    }

    override fun getActionText(dataContext: DataContext): String? = null
}