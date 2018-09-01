package com.daveme.intellij.chocolateCakePHP.psi

import com.daveme.intellij.chocolateCakePHP.psi.addValueToClassProperty
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.jetbrains.php.lang.psi.PhpFile

class AddValueToPropertyInsertHandler(private val type: String) : InsertHandler<LookupElement> {

    override fun handleInsert(insertionContext: InsertionContext, lookupElement: LookupElement) {
        val file = insertionContext.file as? PhpFile ?: return
        addValueToClassProperty(file, insertionContext.document, type, lookupElement.lookupString)
    }

}