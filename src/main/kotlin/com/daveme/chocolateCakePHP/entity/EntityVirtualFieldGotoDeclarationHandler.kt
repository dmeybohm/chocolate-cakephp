package com.daveme.chocolateCakePHP.entity

import com.daveme.chocolateCakePHP.Settings
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference

class EntityVirtualFieldGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(psiElement: PsiElement?, i: Int, editor: Editor): Array<PsiElement>? {
        val fieldReference = psiElement?.parent as? FieldReference ?: return PsiElement.EMPTY_ARRAY
        val fieldName = fieldReference.name ?: return PsiElement.EMPTY_ARRAY
        val project = fieldReference.project
        val settings = Settings.getInstance(project)
        if (!settings.enabled || !settings.cake3Enabled || DumbService.getInstance(project).isDumb) {
            return PsiElement.EMPTY_ARRAY
        }
        val targets = EntityAccessors.entitiesOfReceiver(PhpIndex.getInstance(project), fieldReference)
            .filter { it.findFieldByName(fieldName, false) == null }
            .flatMap { entity ->
                val table = EntityAccessors.tableOf(project, entity)
                listOfNotNull(table.getter(entity, fieldName), table.setter(entity, fieldName))
            }
        return targets.toTypedArray()
    }
}
