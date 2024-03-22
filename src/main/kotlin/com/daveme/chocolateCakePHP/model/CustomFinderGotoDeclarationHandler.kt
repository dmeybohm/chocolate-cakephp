package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
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
        val phpIndex = PhpIndex.getInstance(sourceElement.project)
        val reference = methodReference.classReference ?: return PsiElement.EMPTY_ARRAY
        val completedType = if (reference.type.isComplete)
            reference.type
        else
            phpIndex.completeType(sourceElement.project, reference.type, null)
        val tableTypes = completedType.types.mapNotNull { type ->
                when {
                    type.isPluginSpecificTypeForQueryBuilder() -> type.unwrapFromPluginSpecificTypeForQueryBuilder()
                    type.isAnyTableClass() -> type
                    else -> null
                }
            }
            .filter {
                it.startsWith("\\") &&  // only full-formed classes
                        !it.equals("\\Cake\\ORM\\Table", ignoreCase = true) // more specific types only
            }
            .distinct()

        if (tableTypes.isEmpty()) {
            return PsiElement.EMPTY_ARRAY
        }

        // Iterate each of the types looking for the findXXX method:
        return phpIndex.customFinderMethods(tableTypes, sourceElement.text).toTypedArray()
    }

    override fun getActionText(context: DataContext): @Nls(capitalization = Nls.Capitalization.Title) String? {
        return super<GotoDeclarationHandler>.getActionText(context)
    }

}