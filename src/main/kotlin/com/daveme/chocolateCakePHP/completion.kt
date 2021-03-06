package com.daveme.chocolateCakePHP

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.lang.psi.elements.PhpClass

fun CompletionResultSet.completeFromClasses(
    classes: Collection<PhpClass>,
    replaceName: String = "",
    containingClasses: List<PhpClass> = arrayListOf()
) {
    classes.map { klass ->
        val replacedName = klass.name.chopFromEnd(replaceName)
        if (hasFieldAlready(containingClasses, replacedName)) {
            return@map
        }
        val lookupElement = LookupElementBuilder.create(replacedName)
                .withIcon(PhpIcons.FIELD)
                .withTypeText(klass.type.toString().substring(1))
        this.addElement(lookupElement)
    }
}

private fun hasFieldAlready(containingClasses: List<PhpClass>, propertyName: String): Boolean =
    containingClasses.any {
        val hasField = it.findFieldByName(propertyName, true) != null
        if (hasField) { return@any true }
        val docComment = it.docComment ?: return@any false
        // todo: filter private properties
        return@any docComment.propertyTags.any {
            it.property?.name == propertyName
        }
    }