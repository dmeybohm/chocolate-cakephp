package com.daveme.intellij.chocolateCakePHP.cake

import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import java.util.*

private const val VIEW_HELPER_PARENT_CLASS = "\\AppHelper"

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
    return getClasses(project, "\\" + fieldName + "Helper")
}

fun getAllViewHelperSubclassesFiltered(project: Project): Collection<PhpClass> {
    val index = PhpIndex.getInstance(project)
    val allSubclasses = index.getAllSubclasses(VIEW_HELPER_PARENT_CLASS)
    return viewHelperClassesFiltered(allSubclasses)
}

fun controllerFieldClasses(project: Project, fieldName: String): Collection<PhpClass> {
    val result = ArrayList<PhpClass>()
    val phpIndex = PhpIndex.getInstance(project)
    val modelClasses = phpIndex.getClassesByFQN(fieldName)
    val componentClasses = phpIndex.getClassesByFQN(fieldName + "Component")
    result.addAll(modelClasses)
    result.addAll(componentClasses)
    return result
}

fun getClasses(project: Project, className: String): Collection<PhpClass> {
    val phpIndex = PhpIndex.getInstance(project)
    return phpIndex.getClassesByFQN(className)
}

private fun viewHelperClassesFiltered(collection: Collection<PhpClass>): Collection<PhpClass> {
    val results = ArrayList<PhpClass>()
    for (klass in collection) {
        if (helperBlacklist.contains(klass.name)) {
            continue
        }
        results.add(klass)
    }
    return results
}
