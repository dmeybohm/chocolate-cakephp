package com.daveme.chocolateCakePHP

import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.resolve.types.PhpType

private const val VIEW_HELPER_CAKE2_PARENT_CLASS = "\\AppHelper"
private const val VIEW_HELPER_CAKE3_PARENT_CLASS = "\\Cake\\View\\Helper"

private val helperBlacklist = hashSetOf(
    "Html5TestHelper",
    "OtherHelperHelper",
    "OptionEngineHelper",
    "PluggedHelperHelper",
    "HtmlAliasHelper",
    "TestHtmlHelper",
    "TestPluginAppHelper",
    "TimeHelperTestObject",
    "NumberHelperTestObject",
    "TextHelperTestObject"
)

fun getClassesForViewHelper(phpIndex: PhpIndex, settings: Settings, fieldName: String): Collection<PhpClass> {
    val cake2Helpers = phpIndex.getClassesByFQN("\\${fieldName}Helper")
    val cake3BuiltInHelpers = phpIndex.getClassesByFQN(
        "\\Cake\\View\\Helper\\${fieldName}Helper"
    )
    val cake3UserHelpers = phpIndex.getClassesByFQN(
        "${settings.appNamespace}\\View\\Helper\\${fieldName}Helper"
    )

    return cake2Helpers + cake3BuiltInHelpers + cake3UserHelpers
}

fun getAllViewHelperSubclasses(phpIndex: PhpIndex): Collection<PhpClass> {
    val cake2Subclasses = phpIndex.getAllSubclasses(VIEW_HELPER_CAKE2_PARENT_CLASS)
    val cake3Subclasses = phpIndex.getAllSubclasses(VIEW_HELPER_CAKE3_PARENT_CLASS)

    return cake2Subclasses.filter { !helperBlacklist.contains(it.name) } + cake3Subclasses
}

fun controllerFieldClasses(phpIndex: PhpIndex, settings: Settings, fieldName: String): Collection<PhpClass> {
    val cake2ModelClasses = phpIndex.getClassesByFQN("\\$fieldName")
    val cake2ComponentClasses = phpIndex.getClassesByFQN("\\${fieldName}Component")

    val cake3ModelClasses = phpIndex.getClassesByFQN(
        "${settings.appNamespace}\\Model\\Table\\$fieldName"
    )
    val cake3BuiltinComponentClasses = phpIndex.getClassesByFQN(
        "\\Cake\\Controller\\Component\\${fieldName}Component"
    )
    val cake3UserComponentClasses = phpIndex.getClassesByFQN(
        "${settings.appNamespace}\\Controller\\Component\\${fieldName}Component"
    )

    return cake2ModelClasses +
            cake2ComponentClasses +
            cake3ModelClasses +
            cake3BuiltinComponentClasses +
            cake3UserComponentClasses
}

fun viewHelperTypeFromFieldReference(settings: Settings, fieldName: String): PhpType {
    return PhpType().add("\\${fieldName}Helper")
        .add("\\Cake\\View\\Helper\\${fieldName}Helper")
        .add("${settings.appNamespace}\\View\\Helper\\${fieldName}Helper")
        .add("\\DebugKit\\View\\Helper\\${fieldName}Helper")
}

fun componentOrModelTypeFromFieldReference(settings: Settings, fieldName: String): PhpType {
    return PhpType()
        .add("\\" + fieldName)
        .add("\\" + fieldName + "Component")
        .add("\\Cake\\Controller\\Component\\${fieldName}Component")
        .add("${settings.appNamespace}\\Controller\\Component\\${fieldName}Component")
}