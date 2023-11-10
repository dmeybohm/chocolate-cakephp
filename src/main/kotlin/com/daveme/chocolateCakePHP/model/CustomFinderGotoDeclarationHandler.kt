package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.customFinderMethods
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.jetbrains.annotations.Nls

class CustomFinderGotoDeclarationHandler : GotoDeclarationHandler {
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
        if (!psiElement(StringLiteralExpression::class.java)
                .withLanguage(PhpLanguage.INSTANCE)
                .accepts(sourceElement.context)
        ) {
            return PsiElement.EMPTY_ARRAY
        }
        val parameterList = sourceElement.context?.parent as? ParameterList
            ?: return PsiElement.EMPTY_ARRAY
        val methodReference = parameterList.parent as? MethodReference
            ?: return PsiElement.EMPTY_ARRAY
        val methodName = methodReference.name
        if (!methodName.equals("find", ignoreCase = true)) {
            return PsiElement.EMPTY_ARRAY
        }
        val reference = methodReference.classReference ?: return PsiElement.EMPTY_ARRAY
        val varType = reference.type.filterUnknown()
        val tableTypes = varType.types.filter { type -> type.contains("\\Table", ignoreCase = true) }
        if (tableTypes.isEmpty()) {
            return PsiElement.EMPTY_ARRAY
        }

        // Iterate each of the types looking for the findXXX method:
        val phpIndex = PhpIndex.getInstance(sourceElement.project)
        return phpIndex.customFinderMethods(tableTypes, sourceElement.text).toTypedArray()
    }

    override fun getActionText(context: DataContext): @Nls(capitalization = Nls.Capitalization.Title) String? {
        return super<GotoDeclarationHandler>.getActionText(context)
    }
}