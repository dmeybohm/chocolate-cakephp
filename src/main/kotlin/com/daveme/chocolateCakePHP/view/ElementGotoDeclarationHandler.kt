package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.elementPathToVirtualFile
import com.daveme.chocolateCakePHP.cake.templatesDirectoryFromViewFile
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
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

        val containingFile = psiElement.containingFile
        val templatesDir = templatesDirectoryFromViewFile(psiElement.project, settings, containingFile)
            ?: return PsiElement.EMPTY_ARRAY
        val relativeFile = elementPathToVirtualFile(settings, templatesDir, psiElement.text)
            ?: return PsiElement.EMPTY_ARRAY

        val files = HashSet<VirtualFile>()
        files.add(relativeFile)
        return virtualFilesToPsiFiles(project, files).toTypedArray()
    }

    override fun getActionText(dataContext: DataContext): String? = null
}