package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.MethodReference

object TableLocatorMethodPattern :
    PatternCondition<MethodReference>("TableLocatorMethodPattern")
{
    // $this->getTableLocator()->get("Movies")
    // $this->fetchTable("Movies")
    // TableRegistry::getTableLocator()->get("Movies")
    // TableRegistry::get("Movies")
    override fun accepts(methodReference: MethodReference, context: ProcessingContext): Boolean {
        if (!"get".equals(methodReference.name, ignoreCase = true) &&
            !"fetchTable".equals(methodReference.name, ignoreCase = true)
        ) {
            return false
        }
        val settings =
            Settings.getInstance(methodReference.project)
        if (!settings.cake3Enabled) {
            return false
        }
        val type = methodReference.classReference?.type ?: return false
        return type.isProbablyTableLocatorClass() ||
                type.isProbablyControllerClass() ||
                type.isProbablyTableRegistryClass()
    }
}