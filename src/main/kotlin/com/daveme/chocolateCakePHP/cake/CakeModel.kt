package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.absoluteClassName
import com.daveme.chocolateCakePHP.findPluginConfigByName
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

    settings.pluginConfigs.forEach { pluginConfig ->
        resultClasses += this.getClassesByFQN("${pluginConfig.namespace}\\Model\\Table\\${possibleTableName}Table")
    }
    return resultClasses
}

/**
 * The class a table locator alias names, or null if the alias can't name a class.
 */
fun tableClassFqnFromAlias(settings: Settings, alias: String): String? {
    if (alias.isEmpty() || alias.length >= 255) {
        return null
    }
    if (alias.contains('\\')) {
        return alias.absoluteClassName()
    }
    val plugin = alias.substringBefore('.', "")
    val table = alias.substringAfter('.')
    if (table.isEmpty() || table.contains('.')) {
        return null
    }
    val namespace = when {
        plugin.isEmpty() -> settings.appNamespace
        else -> settings.findPluginConfigByName(plugin)?.namespace ?: plugin.replace('/', '\\')
    }
    return "${namespace.absoluteClassName()}\\Model\\Table\\${table.replace('/', '\\')}Table"
}
