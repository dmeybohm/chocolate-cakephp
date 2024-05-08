package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.isProbablyControllerClass
import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.FieldReference

object IsControllerPattern : PatternCondition<FieldReference>("IsControllerPattern") {
    override fun accepts(fieldReference: FieldReference, context: ProcessingContext): Boolean {
        val settings = Settings.getInstance(fieldReference.project)
        if (!settings.enabled) {
            return false
        }
        val classRefType = fieldReference.classReference?.type ?: return false

        return classRefType.isProbablyControllerClass()
    }
}

