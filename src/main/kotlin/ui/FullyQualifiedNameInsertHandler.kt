package com.daveme.chocolateCakePHP.ui

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.TextRange
import com.jetbrains.php.completion.insert.PhpInsertHandlerUtil
import com.jetbrains.php.lang.PhpLangUtil

//
// Insertion handler for the class text fields.
//
class FullyQualifiedNameInsertHandler : InsertHandler<LookupElement> {

    override fun handleInsert(
        insertionContext: InsertionContext,
        lookupElement: LookupElement
    ) {
        val qualifiedName = lookupElement.getObject()
        if (qualifiedName is String) {
            val result = StringBuilder()
            val parent = PhpLangUtil.getParentQualifiedName(qualifiedName)
            if (!parent.isEmpty()) {
                result.append(parent)
                result.append("\\")
            }
            insertionContext.document.insertString(
                insertionContext.startOffset,
                result
            )
        }
    }

}