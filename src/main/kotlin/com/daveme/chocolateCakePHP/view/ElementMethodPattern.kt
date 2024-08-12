package com.daveme.chocolateCakePHP.view

import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.Variable

object ElementMethodPattern :
    PatternCondition<MethodReference>("ElementMethodPattern") {

    override fun accepts(
        methodReference: MethodReference,
        context: ProcessingContext
    ): Boolean {
        val variable = methodReference.firstChild as? Variable ?: return false
        if (variable.name != "this") {
            return false
        }
        return methodReference.name.equals("element", ignoreCase = true)
    }

}
