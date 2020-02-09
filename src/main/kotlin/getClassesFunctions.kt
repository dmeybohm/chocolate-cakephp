package com.daveme.chocolateCakePHP

import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.resolve.types.PhpType

private const val VIEW_HELPER_CAKE2_PARENT_CLASS = "\\AppHelper"
private const val VIEW_HELPER_CAKE3_PARENT_CLASS = "\\Cake\\View\\Helper"

private const val MODEL_CAKE2_PARENT_CLASS = "\\AppModel"

private const val COMPONENT_CAKE2_PARENT_CLASS = "\\AppComponent"
private const val COMPONENT_CAKE3_PARENT_CLASS = "\\Cake\\Controller\\Component"

private val cake2HelperBlackList = hashSetOf(
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

fun PhpIndex.getAllViewHelperSubclasses(settings: Settings): Collection<PhpClass> {
    val result = arrayListOf<PhpClass>()
    if (settings.cake2Enabled) {
        result += getAllSubclasses(VIEW_HELPER_CAKE2_PARENT_CLASS).filter {
            !cake2HelperBlackList.contains(it.name)
        }
    }
    if (settings.cake3Enabled) {
        result += getAllSubclasses(VIEW_HELPER_CAKE3_PARENT_CLASS)
    }
    return result
}

fun PhpIndex.getAllModelSubclasses(settings: Settings): Collection<PhpClass> {
    val result = arrayListOf<PhpClass>()
    if (settings.cake2Enabled) {
        result += getAllSubclasses(MODEL_CAKE2_PARENT_CLASS)
    }
//    val cake3Subclasses = phpIndex.getAllSubclasses(MODEL_CAKE3_PARENT_CLASS)
    return result
}

fun PhpIndex.getAllComponentSubclasses(settings: Settings): Collection<PhpClass> {
    val result = arrayListOf<PhpClass>()
    if (settings.cake2Enabled) {
        result += getAllSubclasses(COMPONENT_CAKE2_PARENT_CLASS)
    }
    if (settings.cake3Enabled) {
        result += getAllSubclasses(COMPONENT_CAKE3_PARENT_CLASS)
    }
    return result
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
    val result = arrayListOf<PhpClass>()
    if (settings.cake2Enabled) {
        result += getClassesByFQN("\\${fieldName}Helper")
    }
    if (settings.cake3Enabled) {
        result += getClassesByFQN(
            "\\Cake\\View\\Helper\\${fieldName}Helper"
        )
        result += getClassesByFQN(
            "${settings.appNamespace}\\View\\Helper\\${fieldName}Helper"
        )
        for (pluginEntry in settings.pluginEntries) {
            result += getClassesByFQN("${pluginEntry.namespace}\\View\\Helper\\${fieldName}Helper")
        }
    }
    return result
}

fun PhpIndex.componentAndModelClassesFromFieldName(settings: Settings, fieldName: String): Collection<PhpClass> =
    this.componentFieldClassesFromFieldName(settings, fieldName) +
            this.modelFieldClassesFromFieldName(settings, fieldName)

fun PhpIndex.componentFieldClassesFromFieldName(settings: Settings, fieldName: String): Collection<PhpClass> {
    val result = arrayListOf<PhpClass>()
    if (settings.cake2Enabled) {
        result += getClassesByFQN("\\${fieldName}Component")
    }

    if (settings.cake3Enabled) {
        result += getClassesByFQN(
                "\\Cake\\Controller\\Component\\${fieldName}Component"
        )
        result += getClassesByFQN(
                "${settings.appNamespace}\\Controller\\Component\\${fieldName}Component"
        )
        for (pluginEntry in settings.pluginEntries) {
            result += getClassesByFQN("${pluginEntry.namespace}\\Controller\\Component\\${fieldName}Component")
        }
    }
    return result
}

fun PhpIndex.modelFieldClassesFromFieldName(settings: Settings, fieldName: String): Collection<PhpClass> {
    val result = arrayListOf<PhpClass>()
    if (settings.cake2Enabled) {
        result += getClassesByFQN("\\$fieldName")
    }
    if (settings.cake3Enabled) {
        result += getClassesByFQN(
                "${settings.appNamespace}\\Model\\Table\\$fieldName"
        )
        for (pluginEntry in settings.pluginEntries) {
            result += getClassesByFQN("${pluginEntry.namespace}\\Model\\Table\\${fieldName}")
        }
    }
    return result
}

fun viewHelperTypeFromFieldName(settings: Settings, fieldName: String): PhpType {
    var result = PhpType()
    if (settings.cake2Enabled) {
        result = result.add("\\${fieldName}Helper")
    }
    if (settings.cake3Enabled) {
        result = result.add("\\Cake\\View\\Helper\\${fieldName}Helper")
            .add("${settings.appNamespace}\\View\\Helper\\${fieldName}Helper")
        for (pluginEntry in settings.pluginEntries) {
            result = result.add("${pluginEntry.namespace}\\View\\Helper\\${fieldName}Helper")
        }
    }
    return result
}

fun componentOrModelTypeFromFieldName(settings: Settings, fieldName: String): PhpType {
    var result = PhpType()
    if (settings.cake2Enabled) {
       result = result .add("\\" + fieldName)
            .add("\\" + fieldName + "Component")
    }
    if (settings.cake3Enabled) {
        result = result.add("\\Cake\\Controller\\Component\\${fieldName}Component")
            .add("${settings.appNamespace}\\Controller\\Component\\${fieldName}Component")
        for (pluginEntry in settings.pluginEntries) {
            result = result.add("${pluginEntry.namespace}\\Controller\\Component\\${fieldName}Component")
        }
    }
    return result
}

fun viewType(settings: Settings): PhpType {
    var result = PhpType()
    if (settings.cake2Enabled) {
        result = result.add("\\AppView")
    }
    if (settings.cake3Enabled) {
        result = result.add("${settings.appNamespace}\\View\\AppView")
    }
    return result
}