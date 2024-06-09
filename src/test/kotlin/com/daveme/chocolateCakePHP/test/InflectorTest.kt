package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Inflector
import com.daveme.chocolateCakePHP.singularize

class InflectorTest : BaseTestCase() {

    val stringsToTest = listOf(
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

    fun `test singularize`() {
        for ((expected, input) in stringsToTest) {
            val result = Inflector.singularize(input)
            assertEquals(expected, result)
        }
    }


    fun `test String singularize`() {
        for ((expected, input) in stringsToTest) {
            val result = input.singularize()
            assertEquals(expected, result)
        }
    }

}