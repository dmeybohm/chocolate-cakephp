package com.daveme.chocolateCakePHP.view

import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.Variable

private val assetMethods = listOf("css", "script", "image")

object AssetMethodPattern :
    PatternCondition<MethodReference>("AssetMethodPattern") {

    override fun accepts(
        methodReference: MethodReference,
        context: ProcessingContext
    ): Boolean {
        val fieldReference = methodReference.firstChild as? FieldReference ?: return false
        val variable = fieldReference.firstChild as? Variable ?: return false
        if (variable.name != "this") {
            return false
        }
        return assetMethods.any { assetMethod ->
            assetMethod.equals(methodReference.name, ignoreCase = true)
        }
    }

}