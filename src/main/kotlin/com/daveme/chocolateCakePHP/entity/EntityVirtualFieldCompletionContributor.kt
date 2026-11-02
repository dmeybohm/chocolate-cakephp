package com.daveme.chocolateCakePHP.entity

import com.daveme.chocolateCakePHP.Settings
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference

class EntityVirtualFieldCompletionContributor : CompletionContributor() {

    init {
        val pattern = PlatformPatterns.psiElement(LeafPsiElement::class.java)
            .withParent(FieldReference::class.java)
        extend(CompletionType.BASIC, pattern, EntityVirtualFieldCompletionProvider())
        extend(CompletionType.SMART, pattern, EntityVirtualFieldCompletionProvider())
    }

    private class EntityVirtualFieldCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val fieldReference = parameters.position.parent as? FieldReference ?: return
            val project = fieldReference.project
            val settings = Settings.getInstance(project)
            if (!settings.enabled || !settings.cake3Enabled || DumbService.getInstance(project).isDumb) {
                return
            }
            val phpIndex = PhpIndex.getInstance(project)
            val offered = mutableSetOf<String>()
            for (entity in EntityAccessors.entitiesOfReceiver(phpIndex, fieldReference)) {
                val table = EntityAccessors.tableOf(project, entity)
                for (field in table.fields) {
                    if (entity.findFieldByName(field, false) != null) {
                        continue
                    }
                    val getter = table.getter(entity, field)
                    val method = getter ?: table.setter(entity, field) ?: continue
                    if (!offered.add(field)) {
                        continue
                    }
                    val typeText = getter?.let { EntityVirtualFieldTypes.getterType(project, phpIndex, it).toString() }
                    result.addElement(
                        LookupElementBuilder.create(method, field)
                            .withIcon(method.getIcon(0))
                            .withTypeText(typeText)
                    )
                }
                for ((field, property) in EntityAssociations.propertiesOf(project, phpIndex, entity)) {
                    if (entity.findFieldByName(field, false) != null || !offered.add(field)) {
                        continue
                    }
                    result.addElement(
                        LookupElementBuilder.create(field)
                            .withIcon(PhpIcons.FIELD)
                            .withTypeText(property.type.toString())
                    )
                }
            }
        }
    }
}
