package com.daveme.intellij.chocolateCakePHP.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.jetbrains.php.completion.PhpVariantsUtil
import com.jetbrains.php.completion.UsageContext
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpModifier

fun complete(
        classes: Collection<PhpClass>,
        fromClass: PhpClass,
        completionResultSet: CompletionResultSet) {
    if (classes.isEmpty()) {
        return
    }
    val usageContext = UsageContext(PhpModifier.State.DYNAMIC)
    usageContext.targetObjectClass = fromClass
    for (klass in classes) {
        try {
            val lookupItems = PhpVariantsUtil.getLookupItems(klass.methods, false, usageContext)
            completionResultSet.addAllElements(lookupItems)
        } catch (e: Exception) {

        }

        try {
            val lookupElements = PhpVariantsUtil.getLookupItems(klass.fields, false, usageContext)
            completionResultSet.addAllElements(lookupElements)
        } catch (e: Exception) {

        }

    }
}