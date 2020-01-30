package com.daveme.chocolateCakePHP.navigation

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.componentFieldClassesFromFieldName
import com.daveme.chocolateCakePHP.psi.findParentWithClass
import com.daveme.chocolateCakePHP.viewHelperClassesFromFieldName
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ControllerComponentsGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(psiElement: PsiElement?, i: Int, editor: Editor): Array<PsiElement>? {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        val settings = Settings.getInstance(psiElement.project)
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
        val field = findParentWithClass(psiElement, Field::class.java) as Field? ?: return PsiElement.EMPTY_ARRAY
        val text = field.text

        val phpIndex = PhpIndex.getInstance(psiElement.project)

        // PhpStorm already has completion based on strings that contain class names, so
        // we only need to check for the components and helpers properties:
        return when {
            text.contains("\$components") ->
                phpIndex.componentFieldClassesFromFieldName(settings, psiElement.text).toTypedArray()

            text.contains("\$helpers") ->
                phpIndex.viewHelperClassesFromFieldName(settings, psiElement.text).toTypedArray()

            else ->
                PsiElement.EMPTY_ARRAY
        }
    }

    override fun getActionText(dataContext: DataContext): String? {
        return null
    }
}
