package com.daveme.intellij.chocolateCakePHP.navigation

import com.daveme.intellij.chocolateCakePHP.cake.controllerFieldClasses
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.FieldReference

class ControllerFieldGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(psiElement: PsiElement?, i: Int, editor: Editor): Array<PsiElement>? {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        if (!PlatformPatterns.psiElement().withParent(FieldReference::class.java).accepts(psiElement)) {
            return PsiElement.EMPTY_ARRAY
        }
        val parent = psiElement.parent ?: return PsiElement.EMPTY_ARRAY
        val fieldReference = parent as FieldReference
        val fieldName = fieldReference.name ?: return PsiElement.EMPTY_ARRAY

        return controllerFieldClasses(psiElement.project, fieldName).toTypedArray()
    }

    override fun getActionText(dataContext: DataContext): String? {
        return null
    }

}
