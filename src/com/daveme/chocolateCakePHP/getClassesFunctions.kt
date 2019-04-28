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

fun getAllViewHelperSubclasses(phpIndex: PhpIndex): Collection<PhpClass> {
    val cake2Subclasses = phpIndex.getAllSubclasses(VIEW_HELPER_CAKE2_PARENT_CLASS)
    val cake3Subclasses = phpIndex.getAllSubclasses(VIEW_HELPER_CAKE3_PARENT_CLASS)

    return cake2Subclasses.filter { !helperBlacklist.contains(it.name) } + cake3Subclasses
}

fun getAllModelSubclasses(phpIndex: PhpIndex): Collection<PhpClass> {
    val cake2Subclasses = phpIndex.getAllSubclasses(MODEL_CAKE2_PARENT_CLASS)
//    val cake3Subclasses = phpIndex.getAllSubclasses(MODEL_CAKE3_PARENT_CLASS)
    
    return cake2Subclasses //+ cake3Subclasses
}

fun getAllComponentSubclasses(phpIndex: PhpIndex): Collection<PhpClass> {
    val cake2Subclasses = phpIndex.getAllSubclasses(COMPONENT_CAKE2_PARENT_CLASS)
    val cake3Subclasses = phpIndex.getAllSubclasses(COMPONENT_CAKE3_PARENT_CLASS)
    
    return cake2Subclasses + cake3Subclasses
}

fun getAllAncestorTypesFromFQNs(phpIndex: PhpIndex, classes: List<String>): List<PhpClass> {
    val result = ArrayList<PhpClass>()
    classes.map {
        val directClasses = phpIndex.getClassesByFQN(it)

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

fun viewHelperClassesFromFieldName(phpIndex: PhpIndex, settings: Settings, fieldName: String): Collection<PhpClass> {
    val cake2Helpers = phpIndex.getClassesByFQN("\\${fieldName}Helper")
    val cake3BuiltInHelpers = phpIndex.getClassesByFQN(
        "\\Cake\\View\\Helper\\${fieldName}Helper"
    )
    val cake3UserHelpers = phpIndex.getClassesByFQN(
        "${settings.appNamespace}\\View\\Helper\\${fieldName}Helper"
    )

    return cake2Helpers + cake3BuiltInHelpers + cake3UserHelpers
}

fun componentAndModelClassesFromFieldName(phpIndex: PhpIndex, settings: Settings, fieldName: String): Collection<PhpClass> =
    componentFieldClassesFromFieldName(phpIndex, settings, fieldName) +
            modelFieldClassesFromFieldName(phpIndex, settings, fieldName)

fun componentFieldClassesFromFieldName(phpIndex: PhpIndex, settings: Settings, fieldName: String): Collection<PhpClass> {
    val cake2ComponentClasses = phpIndex.getClassesByFQN("\\${fieldName}Component")

    val cake3BuiltinComponentClasses = phpIndex.getClassesByFQN(
        "\\Cake\\Controller\\Component\\${fieldName}Component"
    )
    val cake3UserComponentClasses = phpIndex.getClassesByFQN(
        "${settings.appNamespace}\\Controller\\Component\\${fieldName}Component"
    )

    return cake2ComponentClasses + cake3BuiltinComponentClasses + cake3UserComponentClasses
}

fun modelFieldClassesFromFieldName(phpIndex: PhpIndex, settings: Settings, fieldName: String): Collection<PhpClass> {
    val cake2ModelClasses = phpIndex.getClassesByFQN("\\$fieldName")
    val cake3ModelClasses = phpIndex.getClassesByFQN(
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