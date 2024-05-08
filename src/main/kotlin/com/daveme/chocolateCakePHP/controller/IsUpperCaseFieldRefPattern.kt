package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.startsWithUppercaseCharacter
import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.FieldReference

object IsUpperCaseFieldRefPattern : PatternCondition<FieldReference>("IsUppercaseFieldRef") {
    override fun accepts(fieldReference: FieldReference, context: ProcessingContext): Boolean {
        val settings = Settings.getInstance(fieldReference.project)
        if (!settings.enabled) {
            return false
        }
        return fieldReference.name?.startsWithUppercaseCharacter() ?: false
    }
}

