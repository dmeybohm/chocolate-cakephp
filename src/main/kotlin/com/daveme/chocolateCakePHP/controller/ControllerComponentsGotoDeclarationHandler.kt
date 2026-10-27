package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.componentFieldClassesFromFieldName
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.LoadSource
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberLoad
import com.daveme.chocolateCakePHP.configuredmemberindex.methodCallSources
import com.daveme.chocolateCakePHP.findParentWithClass
import com.daveme.chocolateCakePHP.getStaticClassRefOrNull
import com.daveme.chocolateCakePHP.viewHelperClassesFromFieldName
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ControllerComponentsGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(psiElement: PsiElement?, i: Int, editor: Editor): Array<PsiElement>? {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        if (DumbService.getInstance(psiElement.project).isDumb) {
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
        val literal = psiElement.context as StringLiteralExpression
        val load = sourceMethodLoad(literal)
        if (load != null) {
            return ConfiguredMemberService.loadClasses(psiElement.project, load).toTypedArray()
        }

        val field = findParentWithClass(psiElement, Field::class.java) as Field? ?: return PsiElement.EMPTY_ARRAY

        val phpIndex = PhpIndex.getInstance(psiElement.project)

        // PhpStorm already has completion based on strings that contain class names, so
        // we only need to check for the components and helpers properties:
        return when {
            field.textMatches("\$components") ->
                phpIndex.componentFieldClassesFromFieldName(settings, psiElement.text).toTypedArray()

            field.textMatches("\$helpers") ->
                phpIndex.viewHelperClassesFromFieldName(settings, psiElement.text).toTypedArray()

            else ->
                PsiElement.EMPTY_ARRAY
        }
    }

    // The load for a literal that is the first argument of a source method such as
    // `$this->loadComponent('Name', [...])`, or the value of its `className` option.
    private fun sourceMethodLoad(literal: StringLiteralExpression): MemberLoad? {
        val arguments = when (val parent = literal.parent) {
            is ParameterList -> parent.takeIf { it.parameters.firstOrNull() == literal }
            else -> {
                val hashElement = parent?.parent as? ArrayHashElement ?: return null
                val key = hashElement.key as? StringLiteralExpression ?: return null
                if (key.contents != "className" || hashElement.value != literal) {
                    return null
                }
                val options = hashElement.parent as? ArrayCreationExpression ?: return null
                (options.parent as? ParameterList)?.takeIf { it.parameters.getOrNull(1) == options }
            }
        } ?: return null

        val methodReference = arguments.parent as? MethodReference ?: return null
        if (methodReference.classReference?.textMatches("\$this") != true) {
            return null
        }
        val kind = methodCallSources[methodReference.name?.lowercase()] ?: return null
        val loadedName = (arguments.parameters.firstOrNull() as? StringLiteralExpression)?.contents
            ?: return null
        val className = (arguments.parameters.getOrNull(1) as? ArrayCreationExpression)
            ?.hashElements
            ?.firstOrNull { (it.key as? StringLiteralExpression)?.contents == "className" }
            ?.value
            ?.let { (it as? StringLiteralExpression)?.contents ?: (it as? ClassConstantReference)?.getStaticClassRefOrNull() }
        return MemberLoad(
            kind = kind,
            source = LoadSource.METHOD_CALL,
            alias = loadedName.substringAfterLast('.'),
            loadedName = loadedName,
            className = className,
            methodName = null,
            offset = literal.textOffset,
        )
    }

    override fun getActionText(dataContext: DataContext): String? {
        return null
    }
}
