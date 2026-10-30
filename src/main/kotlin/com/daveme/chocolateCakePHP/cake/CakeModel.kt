package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass

fun isCakeTwoModelClass(classes: Collection<PhpClass>): Boolean {
    return classes.any { phpClass -> phpClass.superFQN == "\\AppModel" }
}

/**
 * Whether [fqn] is a CakePHP 2 model, extending `\AppModel` or `\Model` at any depth.
 */
fun PhpIndex.isCakeTwoModel(fqn: String): Boolean {
    var current = listOf(fqn)
    val visited = mutableSetOf<String>()
    while (current.isNotEmpty()) {
        val next = mutableListOf<String>()
        for (each in current) {
            if (each.equals("\\AppModel", ignoreCase = true) || each.equals("\\Model", ignoreCase = true)) {
                return fqn != each
            }
            if (!visited.add(each.lowercase())) {
                continue
            }
            getClassesByFQN(each).mapNotNullTo(next) { it.superFQN }
        }
        current = next
    }
    return false
}

fun PhpIndex.getPossibleTableClasses(settings: Settings, possibleTableName: String): Collection<PhpClass> {
    val resultClasses = mutableListOf<PhpClass>()
    val possibleAppNamespaceClass = "${settings.appNamespace}\\Model\\Table\\${possibleTableName}Table"
    resultClasses += this.getClassesByFQN(possibleAppNamespaceClass)

    settings.cakeThreePluginConfigs.forEach { pluginConfig ->
        resultClasses += this.getClassesByFQN("${pluginConfig.namespace}\\Model\\Table\\${possibleTableName}Table")
    }
    return resultClasses
}