package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
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
        return classRefType.isProbablyControllerClass() ||
                classRefType.isProbablyTableClass() ||
                classRefType.isProbablyQueryObject()
    }
}