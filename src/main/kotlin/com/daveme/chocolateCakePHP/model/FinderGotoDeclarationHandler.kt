package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.componentFieldClassesFromFieldName
import com.daveme.chocolateCakePHP.findParentWithClass
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
import org.jetbrains.annotations.Nls

class FinderGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        if (sourceElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        val settings = Settings.getInstance(sourceElement.project)
        if (!settings.enabled) {
            return PsiElement.EMPTY_ARRAY
        }
        if (!PlatformPatterns
                .psiElement(StringLiteralExpression::class.java)
                .withLanguage(PhpLanguage.INSTANCE)
                .accepts(sourceElement.context)
        ) {
            return PsiElement.EMPTY_ARRAY
        }
        val field = findParentWithClass(sourceElement, Field::class.java) as Field? ?: return PsiElement.EMPTY_ARRAY

        val phpIndex = PhpIndex.getInstance(sourceElement.project)

        // PhpStorm already has completion based on strings that contain class names, so
        // we only need to check for the components and helpers properties:
        // todo
        return PsiElement.EMPTY_ARRAY
//        return when {
//            field.textMatches("\$components") ->
//                phpIndex.componentFieldClassesFromFieldName(settings, psiElement.text).toTypedArray()
//
//            field.textMatches("\$helpers") ->
//                phpIndex.viewHelperClassesFromFieldName(settings, psiElement.text).toTypedArray()
//
//            else ->
//                PsiElement.EMPTY_ARRAY
//        }
//
    }

    override fun getActionText(context: DataContext): @Nls(capitalization = Nls.Capitalization.Title) String? {
        return super<GotoDeclarationHandler>.getActionText(context)
    }
}
