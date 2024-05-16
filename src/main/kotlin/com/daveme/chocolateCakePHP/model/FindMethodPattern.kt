package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.MethodReference

object FindMethodPattern :
    PatternCondition<MethodReference>("FindMethodPattern") {
    override fun accepts(
        methodReference: MethodReference,
        context: ProcessingContext
    ): Boolean {
        if (!"find".equals(methodReference.name, ignoreCase = true)) {
            return false
        }
        val settings =
            Settings.getInstance(methodReference.project)
        if (!settings.cake3Enabled) {
            return false
        }
        val classRefType = methodReference.classReference?.type ?: return false
        val type = if (classRefType.isComplete)
            classRefType
        else {
            val phpIndex = PhpIndex.getInstance(methodReference.project)
            phpIndex.completeType(methodReference.project, classRefType, null)
        }
        return type.isProbablyTableClass() ||
                type.isProbablyQueryObject()
    }
}