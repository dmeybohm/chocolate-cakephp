package com.daveme.intellij.chocolateCakePHP.cake

import com.jetbrains.php.lang.psi.elements.PhpClass
import java.util.*

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

fun viewHelperClassesFiltered(collection: Collection<PhpClass>): Collection<PhpClass> {
    val results = ArrayList<PhpClass>()
    for (klass in collection) {
        if (helperBlacklist.contains(klass.name)) {
            continue
        }
        results.add(klass)
    }
    return results
}


