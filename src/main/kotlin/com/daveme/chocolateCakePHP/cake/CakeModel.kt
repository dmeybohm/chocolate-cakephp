package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass

fun isCakeTwoModelClass(classes: Collection<PhpClass>): Boolean {
    return classes.any { phpClass -> phpClass.superFQN == "\\AppModel" }
}

fun PhpIndex.getPossibleTableClasses(settings: Settings, possibleTableName: String): Collection<PhpClass> {
    val resultClasses = mutableListOf<PhpClass>()
    val possibleAppNamespaceClass = "${settings.appNamespace}\\Model\\Table\\${possibleTableName}Table"
    resultClasses += this.getClassesByFQN(possibleAppNamespaceClass)

    settings.pluginEntries.forEach { pluginEntry ->
        resultClasses += this.getClassesByFQN("${pluginEntry.namespace}\\Model\\Table\\${possibleTableName}Table")
    }
    return resultClasses
}