/**
 * This file contains comes code derived from Inflector.php in CakePHP.
 * Here is the original copyright notice for that:
 *
 * CakePHP(tm) : Rapid Development Framework (https://cakephp.org)
 * Copyright (c) Cake Software Foundation, Inc. (https://cakefoundation.org)
 *
 * Licensed under The MIT License
 * Redistributions of files must retain the above copyright notice.
 */
package com.daveme.chocolateCakePHP

object Inflector {

    private val plural = listOf(
        "(s)tatus$" to "$1tatuses",
        "(quiz)$" to "$1zes",
        "^(ox)$" to "$1en",
        "([m|l])ouse$" to "$1ice",
        "(matr|vert)(ix|ex)$" to "$1ices",
        "(x|ch|ss|sh)$" to "$1es",
        "([^aeiouy]|qu)y$" to "$1ies",
        "(hive)$" to "$1s",
        "(chef)$" to "$1s",
        "(?:([^f])fe|([lre])f)$" to "$1$2ves",
        "sis$" to "ses",
        "([ti])um$" to "$1a",
        "(p)erson$" to "$1eople",
        "(?<!u)(m)an$" to "$1en",
        "(c)hild$" to "$1hildren",
        "(buffal|tomat)o$" to "$1oes",
        "(alumn|bacill|cact|foc|fung|nucle|radi|stimul|syllab|termin)us$" to "$1i",
        "us$" to "uses",
        "(alias)$" to "$1es",
        "(ax|cris|test)is$" to "$1es",
        "s$" to "s",
        "^$" to "",
        "$" to "s"
    )

    private val singular = listOf(
        "(s)tatuses$" to "$1tatus",
        "^(.*)(menu)s$" to "$1$2",
        "(quiz)zes$" to "$1",
        "(matr)ices$" to "$1ix",
        "(vert|ind)ices$" to "$1ex",
        "^(ox)en" to "$1",
        "(alias|lens)(es)*$" to "$1",
        "(alumn|bacill|cact|foc|fung|nucle|radi|stimul|syllab|termin|viri?)i$" to "$1us",
        "([ftw]ax)es" to "$1",
        "(cris|ax|test)es$" to "$1is",
        "(shoe)s$" to "$1",
        "(o)es$" to "$1",
        "ouses$" to "ouse",
        "([^a])uses$" to "$1us",
        "([m|l])ice$" to "$1ouse",
        "(x|ch|ss|sh)es$" to "$1",
        "(m)ovies$" to "$1ovie",
        "(s)eries$" to "$1eries",
        "(s)pecies$" to "$1pecies",
        "([^aeiouy]|qu)ies$" to "$1y",
        "(tive)s$" to "$1",
        "(hive)s$" to "$1",
        "(drive)s$" to "$1",
        "([le])ves$" to "$1f",
        "([^rfoa])ves$" to "$1fe",
        "(^analy)ses$" to "$1sis",
        "(analy|diagno|^ba|(p)arenthe|(p)rogno|(s)ynop|(t)he)ses$" to "$1$2sis",
        "([ti])a$" to "$1um",
        "(p)eople$" to "$1erson",
        "(m)en$" to "$1an",
        "(c)hildren$" to "$1hild",
        "(n)ews$" to "$1ews",
        "eaus$" to "eau",
        "^(.*us)$" to "$1",
        "s$" to ""
    )

    private val uninflectedPatterns = listOf(
        ".*[nrlm]ese",
        ".*data",
        ".*deer",
        ".*fish",
        ".*measles",
        ".*ois",
        ".*pox",
        ".*sheep",
        "people",
        "feedback",
        "stadia",
        ".*?media",
        "chassis",
        "clippers",
        "debris",
        "diabetes",
        "equipment",
        "gallows",
        "graffiti",
        "headquarters",
        "information",
        "innings",
        "news",
        "nexus",
        "pokemon",
        "proceedings",
        "research",
        "sea[- ]bass",
        "series",
        "species",
        "weather"
    )

    private val irregular = hashMapOf(
        "atlas" to "atlases",
        "beef" to "beefs",
        "brief" to "briefs",
        "brother" to "brothers",
        "cafe" to "cafes",
        "child" to "children",
        "cookie" to "cookies",
        "corpus" to "corpuses",
        "cow" to "cows",
        "criterion" to "criteria",
        "ganglion" to "ganglions",
        "genie" to "genies",
        "genus" to "genera",
        "graffito" to "graffiti",
        "hoof" to "hoofs",
        "loaf" to "loaves",
        "man" to "men",
        "money" to "monies",
        "mongoose" to "mongooses",
        "move" to "moves",
        "mythos" to "mythoi",
        "niche" to "niches",
        "numen" to "numina",
        "occiput" to "occiputs",
        "octopus" to "octopuses",
        "opus" to "opuses",
        "ox" to "oxen",
        "penis" to "penises",
        "person" to "people",
        "sex" to "sexes",
        "soliloquy" to "soliloquies",
        "testis" to "testes",
        "trilby" to "trilbys",
        "turf" to "turfs",
        "potato" to "potatoes",
        "hero" to "heroes",
        "tooth" to "teeth",
        "goose" to "geese",
        "foot" to "feet",
        "foe" to "foes",
        "sieve" to "sieves",
        "cache" to "caches",
    )

    private val irregularCapitalized = irregular.map {
        it.key.latinCapitalize() to it.value.latinCapitalize()
    }.toMap()

    private val irregularInverted = irregular.map {
        it.value to it.key
    }.toMap()

    private val irregularInvertedCapitalized = irregularInverted.map {
        it.key.latinCapitalize() to it.value.latinCapitalize()
    }.toMap()

    private val singularRegex : List<Pair<Regex, String>> = singular.map {
        Regex(it.first, RegexOption.IGNORE_CASE) to it.second
    }

    private val pluralRegex : List<Pair<Regex, String>> = plural.map {
        Regex(it.first, RegexOption.IGNORE_CASE) to it.second
    }

    private val uninflectedRegex = Regex(
        "^(" + uninflectedPatterns.joinToString("|") +")$",
        RegexOption.IGNORE_CASE
    )

    fun singularize(plural: String): String {
        if (plural.isEmpty()) {
            return plural
        }
        val firstChar = plural[0]
        val value = if (firstChar.isUpperCase())
            irregularInvertedCapitalized[plural]
        else
            irregularInverted[plural]
        if (value != null) {
            return value
        }
        if (uninflectedRegex.matches(plural)) {
            return plural
        }
        for ((pattern, replacement) in singularRegex) {
            if (pattern.containsMatchIn(plural)) {
                return pattern.replace(plural, replacement)
            }
        }
        return plural
    }

    fun pluralize(singular: String): String {
        if (singular.isEmpty()) {
            return singular
        }
        val firstChar = singular[0]
        val value = if (firstChar.isUpperCase())
            irregularCapitalized[singular]
        else
            irregular[singular]
        if (value != null) {
            return value
        }

        if (uninflectedRegex.matches(singular)) {
            return singular
        }
        for ((pattern, replacement) in pluralRegex) {
            if (pattern.containsMatchIn(singular)) {
                return pattern.replace(singular, replacement)
            }
        }
        return singular
    }

}
