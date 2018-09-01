package com.daveme.intellij.chocolateCakePHP.cake

import com.jetbrains.php.lang.psi.elements.PhpClass
import java.util.*

class ViewHelperClassesFiltered(private val collection: Collection<PhpClass>) {

    fun filtered(): List<PhpClass> {
        val results = ArrayList<PhpClass>()
        for (klass in collection) {
            if (helperBlacklist.contains(klass.name)) {
                continue
            }
            results.add(klass)
        }
        return results
    }

    companion object {
        // TODO I need to look up which one of these need to add "Helper" back
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
    }

}
