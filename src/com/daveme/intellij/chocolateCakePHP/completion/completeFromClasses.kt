package com.daveme.intellij.chocolateCakePHP.completion

import com.daveme.intellij.chocolateCakePHP.util.chopFromEnd
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.lang.psi.elements.PhpClass

fun completeFromClasses(
        completionResultSet: CompletionResultSet,
        classes: Collection<PhpClass>,
        replaceName: String = ""
) {
    for (klass in classes) {
        val helperNameAsPropertyName = klass.name.chopFromEnd(replaceName)
        val lookupElement = LookupElementBuilder.create(helperNameAsPropertyName)
                .withIcon(PhpIcons.FIELD)
                .withTypeText(klass.type.toString())
        completionResultSet.addElement(lookupElement)
    }
}