package com.daveme.intellij.chocolateCakePHP.psi

import com.intellij.openapi.editor.Document
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.PhpClass

fun addValueToProperty(phpFile: PhpFile, document: Document, property: String, valueToAdd: String) {
    for ((_, value) in phpFile.topLevelDefs.entrySet()) {
        for (topLevelDef in value) {
            // todo handle adding to namespaced classes
            if (topLevelDef is PhpClass) {
                val field = topLevelDef.findOwnFieldByName(property, false) ?: continue
                if (appendToProperty(phpFile, document, valueToAdd, field)) {
                    return
                }
            }
        }
    }
}

private fun appendToProperty(file: PhpFile, document: Document, valueToAdd: String, field: Field): Boolean {
    val lastChild = field.lastChild
    return if (lastChild is ArrayCreationExpression) {
        appendToArrayCreationExpression(file, document, valueToAdd, lastChild)
    } else false
}