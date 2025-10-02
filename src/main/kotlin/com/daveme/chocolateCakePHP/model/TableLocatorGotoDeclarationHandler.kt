package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.getPossibleTableClasses
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.jetbrains.annotations.Nls

class TableLocatorGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        if (sourceElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        if (DumbService.getInstance(sourceElement.project).isDumb) {
            return PsiElement.EMPTY_ARRAY
        }
        val settings = Settings.getInstance(sourceElement.project)
        if (!settings.cake3Enabled) {
            return PsiElement.EMPTY_ARRAY
        }

        // Check if the element is a string literal
        if (!psiElement(StringLiteralExpression::class.java)
                .withLanguage(PhpLanguage.INSTANCE)
                .accepts(sourceElement.context)
        ) {
            return PsiElement.EMPTY_ARRAY
        }

        val stringLiteral = sourceElement.context as? StringLiteralExpression
            ?: return PsiElement.EMPTY_ARRAY
        val parameterList = stringLiteral.parent as? ParameterList
            ?: return PsiElement.EMPTY_ARRAY
        val methodReference = parameterList.parent as? MethodReference
            ?: return PsiElement.EMPTY_ARRAY

        // Check if this is a fetchTable() or get() method call
        val methodName = methodReference.name
        val isFetchTable = methodName.equals("fetchTable", ignoreCase = true)
        val isGet = methodName.equals("get", ignoreCase = true)

        if (!isFetchTable && !isGet) {
            return PsiElement.EMPTY_ARRAY
        }

        // For get() method, we need to verify it's called on a TableLocator
        if (isGet) {
            val classReference = methodReference.classReference ?: return PsiElement.EMPTY_ARRAY
            val type = classReference.type
            val isTableLocatorContext = type.isProbablyTableLocatorClass() ||
                type.types.any {
                    it.hasGetTableLocatorMethodCall() ||
                    (methodReference.isStatic && it.isTableRegistryClass())
                }
            if (!isTableLocatorContext) {
                return PsiElement.EMPTY_ARRAY
            }
        }

        // For fetchTable(), verify it's called on a Controller
        if (isFetchTable) {
            val classReference = methodReference.classReference ?: return PsiElement.EMPTY_ARRAY
            val type = classReference.type
            val isControllerContext = type.isProbablyControllerClass()
            if (!isControllerContext) {
                return PsiElement.EMPTY_ARRAY
            }
        }

        // Get the table name from the string literal
        val tableName = stringLiteral.contents
        if (tableName.isEmpty() || tableName.length > 255) {
            return PsiElement.EMPTY_ARRAY
        }

        // Look up the table class
        val phpIndex = PhpIndex.getInstance(sourceElement.project)
        val tableClasses = phpIndex.getPossibleTableClasses(settings, tableName)

        return if (tableClasses.isNotEmpty()) {
            tableClasses.toTypedArray()
        } else {
            PsiElement.EMPTY_ARRAY
        }
    }

    override fun getActionText(context: DataContext): @Nls(capitalization = Nls.Capitalization.Title) String? {
        return super<GotoDeclarationHandler>.getActionText(context)
    }
}