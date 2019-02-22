package com.daveme.intellij.chocolateCakePHP.cake

import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass

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

fun getClassesForViewHelper(project: Project, fieldName: String): Collection<PhpClass> {
    val cake2Helpers = getClasses(project, "\\${fieldName}Helper")
    val cake3BuiltInHelpers = getClasses(project, "\\Cake\\View\\Helper\\${fieldName}Helper")
    val cake3UserHelpers = getClasses(project, "\\App\\View\\Helper\\${fieldName}Helper")

    return cake2Helpers + cake3BuiltInHelpers + cake3UserHelpers
}

fun getAllViewHelperSubclasses(project: Project): Collection<PhpClass> {
    val index = PhpIndex.getInstance(project)
    val cake2Subclasses = index.getAllSubclasses(VIEW_HELPER_CAKE2_PARENT_CLASS)
    val cake3Subclasses = index.getAllSubclasses(VIEW_HELPER_CAKE3_PARENT_CLASS)

    return cake2Subclasses.filter { !helperBlacklist.contains(it.name) } + cake3Subclasses
}

fun controllerFieldClasses(project: Project, fieldName: String): Collection<PhpClass> {
    val phpIndex = PhpIndex.getInstance(project)

    val cake2ModelClasses = phpIndex.getClassesByFQN("\\$fieldName")
    val cake2ComponentClasses = phpIndex.getClassesByFQN("\\${fieldName}Component")

    val cake3ModelClasses = phpIndex.getClassesByFQN("\\App\\Model\\Table\\$fieldName")
    val cake3BuiltinComponentClasses = phpIndex.getClassesByFQN(
        "\\Cake\\Controller\\Component\\${fieldName}Component"
    )
    val cake3UserComponentClasses = phpIndex.getClassesByFQN(
        "\\App\\Controller\\Component\\${fieldName}Component"
    )

    return cake2ModelClasses +
            cake2ComponentClasses +
            cake3ModelClasses +
            cake3BuiltinComponentClasses +
            cake3UserComponentClasses
}

fun getClasses(project: Project, className: String): Collection<PhpClass> {
    val phpIndex = PhpIndex.getInstance(project)
    return phpIndex.getClassesByFQN(className)
}

