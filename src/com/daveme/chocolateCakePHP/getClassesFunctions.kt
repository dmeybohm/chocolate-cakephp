package com.daveme.chocolateCakePHP

import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.resolve.types.PhpType

private const val VIEW_HELPER_CAKE2_PARENT_CLASS = "\\AppHelper"
private const val VIEW_HELPER_CAKE3_PARENT_CLASS = "\\Cake\\View\\Helper"

private const val MODEL_CAKE2_PARENT_CLASS = "\\AppModel"

private const val COMPONENT_CAKE2_PARENT_CLASS = "\\AppComponent"
private const val COMPONENT_CAKE3_PARENT_CLASS = "\\Cake\\Controller\\Component"

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

fun PhpIndex.getAllViewHelperSubclasses(): Collection<PhpClass> {
    val cake2Subclasses = getAllSubclasses(VIEW_HELPER_CAKE2_PARENT_CLASS)
    val cake3Subclasses = getAllSubclasses(VIEW_HELPER_CAKE3_PARENT_CLASS)
    return cake2Subclasses.filter { !helperBlacklist.contains(it.name) } + cake3Subclasses
}

fun PhpIndex.getAllModelSubclasses(): Collection<PhpClass> {
    val cake2Subclasses = getAllSubclasses(MODEL_CAKE2_PARENT_CLASS)
//    val cake3Subclasses = phpIndex.getAllSubclasses(MODEL_CAKE3_PARENT_CLASS)
    return cake2Subclasses //+ cake3Subclasses
}

fun PhpIndex.getAllComponentSubclasses(): Collection<PhpClass> {
    val cake2Subclasses = getAllSubclasses(COMPONENT_CAKE2_PARENT_CLASS)
    val cake3Subclasses = getAllSubclasses(COMPONENT_CAKE3_PARENT_CLASS)
    return cake2Subclasses + cake3Subclasses
}

fun PhpIndex.getAllAncestorTypesFromFQNs(classes: List<String>): List<PhpClass> {
    val result = ArrayList<PhpClass>()
    classes.map {
        val directClasses = getClassesByFQN(it)

        //
        // Add parent classes, traits, and interfaces:
        //
        val superClasses = ArrayList<PhpClass>()
        directClasses.map directSubclasses@  {
            val superClass = it.superClass ?: return@directSubclasses
            superClasses.add(superClass)
        }

        val interfaces = ArrayList<PhpClass>()
        directClasses.map {
            interfaces += it.implementedInterfaces
        }

        val traits = ArrayList<PhpClass>()
        directClasses.map {
            traits += it.traits
        }

        result += directClasses + superClasses + traits
    }

    return result
}

fun PhpIndex.viewHelperClassesFromFieldName(settings: Settings, fieldName: String): Collection<PhpClass> {
    val cake2Helpers = getClassesByFQN("\\${fieldName}Helper")
    val cake3BuiltInHelpers = getClassesByFQN(
        "\\Cake\\View\\Helper\\${fieldName}Helper"
    )
    val cake3UserHelpers = getClassesByFQN(
        "${settings.appNamespace}\\View\\Helper\\${fieldName}Helper"
    )
    return cake2Helpers + cake3BuiltInHelpers + cake3UserHelpers
}

fun PhpIndex.componentAndModelClassesFromFieldName(settings: Settings, fieldName: String): Collection<PhpClass> =
    this.componentFieldClassesFromFieldName(settings, fieldName) +
            this.modelFieldClassesFromFieldName(settings, fieldName)

fun PhpIndex.componentFieldClassesFromFieldName(settings: Settings, fieldName: String): Collection<PhpClass> {
    val cake2ComponentClasses = getClassesByFQN("\\${fieldName}Component")

    val cake3BuiltinComponentClasses = getClassesByFQN(
        "\\Cake\\Controller\\Component\\${fieldName}Component"
    )
    val cake3UserComponentClasses = getClassesByFQN(
        "${settings.appNamespace}\\Controller\\Component\\${fieldName}Component"
    )
    return cake2ComponentClasses + cake3BuiltinComponentClasses + cake3UserComponentClasses
}

fun PhpIndex.modelFieldClassesFromFieldName(settings: Settings, fieldName: String): Collection<PhpClass> {
    val cake2ModelClasses = getClassesByFQN("\\$fieldName")
    val cake3ModelClasses = getClassesByFQN(
        "${settings.appNamespace}\\Model\\Table\\$fieldName"
    )
    return cake2ModelClasses + cake3ModelClasses
}

fun viewHelperTypeFromFieldName(settings: Settings, fieldName: String): PhpType {
    return PhpType().add("\\${fieldName}Helper")
        .add("\\Cake\\View\\Helper\\${fieldName}Helper")
        .add("${settings.appNamespace}\\View\\Helper\\${fieldName}Helper")
        .add("\\DebugKit\\View\\Helper\\${fieldName}Helper")
}

fun componentOrModelTypeFromFieldName(settings: Settings, fieldName: String): PhpType {
    return PhpType()
        .add("\\" + fieldName)
        .add("\\" + fieldName + "Component")
        .add("\\Cake\\Controller\\Component\\${fieldName}Component")
        .add("${settings.appNamespace}\\Controller\\Component\\${fieldName}Component")
}