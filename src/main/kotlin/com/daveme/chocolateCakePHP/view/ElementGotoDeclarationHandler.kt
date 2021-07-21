package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.util.*

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
        val pluginOrAppDir = topSourceDirectoryFromFile(settings, containingFile)
        val relativeFile = elementPathToVirtualFile(settings, pluginOrAppDir, psiElement.text)
                ?: return PsiElement.EMPTY_ARRAY

        val files = HashSet<VirtualFile>()
        files.add(relativeFile)
        return virtualFilesToPsiFiles(project, files).toTypedArray()
    }

    override fun getActionText(dataContext: DataContext): String? = null
}