package com.daveme.chocolateCakePHP

object Inflector {

    fun singularize(plural: String): String {
        if (plural.isEmpty()) {
            return plural
        }
        // Handle common English plural rules
        if (plural.endsWith("ies")) {
            return plural.substring(0, plural.length - 3) + "y"
        } else if (plural.endsWith("s") && !plural.endsWith("ss")) {
            return plural.substring(0, plural.length - 1)
        }
        return plural
    }

}
