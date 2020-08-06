package com.daveme.chocolateCakePHP.navigation

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.viewHelperClassesFromFieldName
import com.daveme.chocolateCakePHP.startsWithUppercaseCharacter
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference

class ViewHelperGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(psiElement: PsiElement?, i: Int, editor: Editor): Array<PsiElement>? {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        val settings = Settings.getInstance(psiElement.project)
        if (!settings.enabled) {
            return PsiElement.EMPTY_ARRAY
        }
        if (!PlatformPatterns.psiElement().withParent(FieldReference::class.java).accepts(psiElement)) {
            return PsiElement.EMPTY_ARRAY
        }
        val parent = psiElement.parent ?: return PsiElement.EMPTY_ARRAY
        val fieldReference = parent as FieldReference

        val fieldName = fieldReference.name ?: return PsiElement.EMPTY_ARRAY
        val classReference = fieldReference.classReference ?: return PsiElement.EMPTY_ARRAY

        if (!fieldName.startsWithUppercaseCharacter()) {
            return PsiElement.EMPTY_ARRAY
        }

        if (classReference.textMatches("\$this")) {
            val project = psiElement.project
            val phpIndex = PhpIndex.getInstance(project)
            return phpIndex.viewHelperClassesFromFieldName(settings, fieldName).toTypedArray()
        }
        return PsiElement.EMPTY_ARRAY
    }

    override fun getActionText(dataContext: DataContext): String? = null
}