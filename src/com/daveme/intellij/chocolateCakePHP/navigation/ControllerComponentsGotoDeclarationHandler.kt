package com.daveme.intellij.chocolateCakePHP.navigation

import com.daveme.intellij.chocolateCakePHP.cake.getClasses
import com.daveme.intellij.chocolateCakePHP.psi.findParentWithClass
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ControllerComponentsGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(psiElement: PsiElement?, i: Int, editor: Editor): Array<PsiElement>? {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        if (!PlatformPatterns
                .psiElement(StringLiteralExpression::class.java)
                .withLanguage(PhpLanguage.INSTANCE)
                .accepts(psiElement.context)
        ) {
            return PsiElement.EMPTY_ARRAY
        }
        val field = findParentWithClass(psiElement, Field::class.java) as Field? ?: return PsiElement.EMPTY_ARRAY
        val text = field.text

        // PhpStorm already has completion based on strings that contain class names, so
        // we only need to check for the components and helpers properties:
        return when {
            text.contains("\$components") ->
                getClasses(psiElement.project, psiElement.text + "Component").toTypedArray()

            text.contains("\$helpers") ->
                getClasses(psiElement.project, psiElement.text + "Helper").toTypedArray()

            else ->
                PsiElement.EMPTY_ARRAY
        }
    }

    override fun getActionText(dataContext: DataContext): String? {
        return null
    }
}
