package com.daveme.intellij.chocolateCakePHP.completion

import com.daveme.intellij.chocolateCakePHP.cake.viewHelperClassesFiltered
import com.daveme.intellij.chocolateCakePHP.util.chopFromEnd
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.PhpIndex

fun completeFromSubclasses(
        completionResultSet: CompletionResultSet,
        project: Project,
        parentClassName: String,
        replaceName: String
) {
    val index = PhpIndex.getInstance(project)
    val allSubclasses = index.getAllSubclasses(parentClassName)

    for (klass in viewHelperClassesFiltered(allSubclasses)) {
        val helperNameAsPropertyName = klass.name.chopFromEnd(replaceName)
        val lookupElement = LookupElementBuilder.create(helperNameAsPropertyName)
                .withIcon(PhpIcons.FIELD)
                .withTypeText(klass.type.toString())
        completionResultSet.addElement(lookupElement)
    }
}