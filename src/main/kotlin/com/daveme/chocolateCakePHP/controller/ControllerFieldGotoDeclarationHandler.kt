package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeTwoModel
import com.daveme.chocolateCakePHP.componentFieldClassesFromFieldName
import com.daveme.chocolateCakePHP.modelFieldClassesFromFieldName
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.daveme.chocolateCakePHP.isAnyControllerClass
import com.daveme.chocolateCakePHP.isAnyTableClass
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference

class ControllerFieldGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(psiElement: PsiElement?, i: Int, editor: Editor): Array<PsiElement>? {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        if (DumbService.getInstance(psiElement.project).isDumb) {
            return PsiElement.EMPTY_ARRAY
        }
        if (!PlatformPatterns.psiElement().withParent(FieldReference::class.java).accepts(psiElement)) {
            return PsiElement.EMPTY_ARRAY
        }
        val settings = Settings.getInstance(psiElement.project)
        if (!settings.enabled) {
            return PsiElement.EMPTY_ARRAY
        }

        val parent = psiElement.parent ?: return PsiElement.EMPTY_ARRAY
        val fieldReference = parent as FieldReference
        val fieldName = fieldReference.name ?: return PsiElement.EMPTY_ARRAY
        val project = psiElement.project
        val phpIndex = PhpIndex.getInstance(project)

        val receiverTypes = fieldReference.classReference?.type
            ?.lookupCompleteType(project, phpIndex, null)
            ?.types
            ?.filter { it.startsWith("\\") }
            .orEmpty()
        val controllerTypes = receiverTypes.filter { it.isAnyControllerClass() }
        val tableTypes = receiverTypes.filter {
            it.isAnyTableClass() || (settings.cake2Enabled && phpIndex.isCakeTwoModel(it))
        }
        val configured = when {
            controllerTypes.isNotEmpty() -> ConfiguredMemberService.memberType(
                project, controllerTypes, setOf(MemberKind.COMPONENT, MemberKind.TABLE), fieldName
            )
            tableTypes.isNotEmpty() -> ConfiguredMemberService.memberType(
                project, tableTypes, setOf(MemberKind.ASSOCIATION), fieldName
            )
            else -> null
        }

        val result = configured?.classes.orEmpty().toMutableList()
        if (configured == null || !configured.hasInitializeLoad) {
            val excludedKinds = configured?.excludedKinds.orEmpty()
            if (MemberKind.COMPONENT !in excludedKinds) {
                result += phpIndex.componentFieldClassesFromFieldName(settings, fieldName)
            }
            if (MemberKind.TABLE !in excludedKinds) {
                result += phpIndex.modelFieldClassesFromFieldName(settings, fieldName)
            }
        }
        return result.distinct().toTypedArray()
    }

    override fun getActionText(dataContext: DataContext): String? {
        return null
    }

}
