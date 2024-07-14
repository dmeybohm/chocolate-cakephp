package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Inflector
import com.daveme.chocolateCakePHP.pluralize
import com.daveme.chocolateCakePHP.singularize

class InflectorTest : BaseTestCase() {

    val singularizeStringsToTest = listOf(
        "categoria" to "categorias",
        "menu" to "menus",
        "news" to "news",
        "food_menu" to "food_menus",
        "Menu" to "Menus",
        "FoodMenu" to "FoodMenus",
        "house" to "houses",
        "powerhouse" to "powerhouses",
        "quiz" to "quizzes",
        "Bus" to "Buses",
        "bus" to "buses",
        "matrix_row" to "matrix_rows",
        "matrix" to "matrices",
        "vertex" to "vertices",
        "index" to "indices",
        "index" to "indexes",
        "Alias" to "Aliases",
        "Alias" to "Alias",
        "Media" to "Media",
        "NodeMedia" to "NodeMedia",
        "alumnus" to "alumni",
        "bacillus" to "bacilli",
        "cactus" to "cacti",
        "focus" to "foci",
        "fungus" to "fungi",
        "nucleus" to "nuclei",
        "octopus" to "octopuses",
        "radius" to "radii",
        "stimulus" to "stimuli",
        "syllabus" to "syllabi",
    )

    val pluralizeStringsToTest = listOf(
        "axmen" to "axman",
        "men" to "man",
        "women" to "woman",
        "humans" to "human",
        "axmen" to "axman",
        "men" to "man",
        "women" to "woman",
        "humans" to "human",
        "categorias" to "categoria",
        "houses" to "house",
        "powerhouses" to "powerhouse",
        "Buses" to "Bus",
        "buses" to "bus",
        "menus" to "menu",
        "news" to "news",
        "food_menus" to "food_menu",
        "Menus" to "Menu",
        "FoodMenus" to "FoodMenu",
        "quizzes" to "quiz",
        "matrix_rows" to "matrix_row",
        "matrices" to "matrix",
        "vertices" to "vertex",
        "indexes" to "index",
        "Aliases" to "Alias",
        "Aliases" to "Aliases",
        "Media" to "Media",
        "NodeMedia" to "NodeMedia",
        "alumni" to "alumnus",
        "bacilli" to "bacillus",
        "cacti" to "cactus",
        "foci" to "focus",
        "fungi" to "fungus",
        "nuclei" to "nucleus",
        "octopuses" to "octopus",
        "radii" to "radius",
        "stimuli" to "stimulus",
        "syllabi" to "syllabus",
        "termini" to "terminus",
        "viruses" to "virus",
        "people" to "person",
        "people" to "people",
        "gloves" to "glove",
        "crises" to "crisis",
        "taxes" to "tax",
        "waves" to "wave",
        "bureaus" to "bureau",
        "cafes" to "cafe",
        "roofs" to "roof",
        "foes" to "foe",
        "cookies" to "cookie",
        "wolves" to "wolf",
        "thieves" to "thief",
        "potatoes" to "potato",
        "heroes" to "hero",
        "buffaloes" to "buffalo",
        "teeth" to "tooth",
        "geese" to "goose",
        "feet" to "foot",
        "objectives" to "objective",
        "briefs" to "brief",
        "quotas" to "quota",
        "curves" to "curve",
        "body_curves" to "body_curve",
        "metadata" to "metadata",
        "files_metadata" to "files_metadata",
        "stadia" to "stadia",
        "Addresses" to "Address",
        "sieves" to "sieve",
        "blue_octopuses" to "blue_octopus",
        "chefs" to "chef",
        "" to "",
        "pokemon" to "pokemon",
    )

    fun `test singularize`() {
        for ((expected, input) in singularizeStringsToTest) {
            val result = Inflector.singularize(input)
            assertEquals(expected, result)
        }
    }


    fun `test String singularize`() {
        for ((expected, input) in singularizeStringsToTest) {
            val result = input.singularize()
            assertEquals(expected, result)
        }
    }

    fun `test pluralize`() {
        for ((expected, input) in pluralizeStringsToTest) {
            val result = Inflector.pluralize(input)
            assertEquals(expected, result)
        }
    }

    fun `test String pluralize`() {
        for ((expected, input) in pluralizeStringsToTest) {
            val result = input.pluralize()
            assertEquals(expected, result)
        }
    }

    fun `test pluralize doesnt alter already plural`() {
        assertEquals("Movies", Inflector.pluralize("Movies"))
        assertEquals("thieves", Inflector.pluralize("thieves"))
        assertEquals("cars", Inflector.pluralize("cars"))
//  todo
//        assertEquals("axmen", Inflector.pluralize("axmen")) // broken
//        axmen != axmens
//        men != man
//        women != womens
//        axmen != axmens
//        men != man
//        women != womens
//        menus != menuses
//        food_menus != food_menuses
//        Menus != Menuses
//        FoodMenus != FoodMenuses
//        alumni != alumnis
//        bacilli != bacillis
//        cacti != cactis
//        foci != focis
//        fungi != fungis
//        nuclei != nucleis
//        octopuses != octopus
//        radii != radiis
//        stimuli != stimulis
//        syllabi != syllabis
//        termini != terminis
//        people != person
//        people != person
//        bureaus != bureauses
//        cafes != cafe
//        foes != foe
//        cookies != cookie
//        potatoes != potato
//        heroes != hero
//        teeth != tooth
//        geese != goose
//        feet != foot
//        briefs != brief
//        sieves != siev
    }

}