package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.absoluteClassName
import com.daveme.chocolateCakePHP.getStringifiedClassOrNull
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

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
    val namespace = if (plugin.isEmpty()) settings.appNamespace else plugin.replace('/', '\\')
    return "${namespace.absoluteClassName()}\\Model\\Table\\${table.replace('/', '\\')}Table"
}

/**
 * The class a table locator call loads, or null if its arguments don't name one.
 */
fun tableClassFqnFromLocatorCall(settings: Settings, methodReference: MethodReference): String? {
    val parameters = methodReference.parameters
    val options = parameters.getOrNull(1) as? ArrayCreationExpression
    val classNameOption = options?.hashElements
        ?.firstOrNull { (it.key as? StringLiteralExpression)?.contents == "className" }
        ?.value
    val fromOption = when (classNameOption) {
        is StringLiteralExpression -> tableClassFqnFromAlias(settings, classNameOption.contents)
        else -> classNameOption?.getStringifiedClassOrNull()
    }
    if (fromOption != null && fromOption.length < 255) {
        return fromOption
    }
    val alias = parameters.firstOrNull() as? StringLiteralExpression ?: return null
    return tableClassFqnFromAlias(settings, alias.contents)
}

/**
 * The table locator aliases that load a table class: its name in the app namespace, and its
 * `Plugin.Table` name in each configured plugin namespace. CakePHP reads the plugin part as a
 * namespace, so it comes from the namespace and not from the configured plugin name.
 */
fun tableAliasesFromClassFqn(settings: Settings, classFqn: String): List<String> {
    val appName = tableNameFromClassFqn(settings.appNamespace, classFqn)
    val pluginAliases = settings.pluginConfigs.mapNotNull { pluginConfig ->
        val name = tableNameFromClassFqn(pluginConfig.namespace, classFqn) ?: return@mapNotNull null
        "${pluginConfig.namespace.trim('\\').replace('\\', '/')}.${name}"
    }
    return listOfNotNull(appName) + pluginAliases
}

private fun tableNameFromClassFqn(namespace: String, classFqn: String): String? {
    val prefix = "${namespace.absoluteClassName()}\\Model\\Table\\"
    if (!classFqn.startsWith(prefix, ignoreCase = true) || !classFqn.endsWith("Table")) {
        return null
    }
    val name = classFqn.substring(prefix.length).removeSuffix("Table")
    if (name.isEmpty()) {
        return null
    }
    return name.replace('\\', '/')
}
