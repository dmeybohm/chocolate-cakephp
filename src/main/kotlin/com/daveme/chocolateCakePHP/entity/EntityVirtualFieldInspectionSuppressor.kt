package com.daveme.chocolateCakePHP.entity

import com.daveme.chocolateCakePHP.Settings
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference

class EntityVirtualFieldInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != "PhpUndefinedFieldInspection") {
            return false
        }
        val project = element.project
        val settings = Settings.getInstance(project)
        if (!settings.enabled || !settings.cake3Enabled || DumbService.getInstance(project).isDumb) {
            return false
        }
        val fieldReference = PsiTreeUtil.getParentOfType(element, FieldReference::class.java, false) ?: return false
        val fieldName = fieldReference.name ?: return false
        val phpIndex = PhpIndex.getInstance(project)
        return EntityAccessors.entitiesOfReceiver(phpIndex, fieldReference).any { entity ->
            val table = EntityAccessors.tableOf(project, entity)
            table.getter(entity, fieldName) != null || table.setter(entity, fieldName) != null ||
                fieldName in EntityAssociations.propertiesOf(project, phpIndex, entity)
        }
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> = arrayOf()
}
