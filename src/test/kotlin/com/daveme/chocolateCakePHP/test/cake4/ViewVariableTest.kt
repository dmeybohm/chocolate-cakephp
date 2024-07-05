package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText

class ViewVariableTest : Cake4BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake4/src4/Controller/AppController.php",
            "cake4/src4/Controller/MovieController.php",
            "cake4/src4/Controller/Component/MovieMetadataComponent.php",
            "cake4/src4/View/Helper/MovieFormatterHelper.php",
            "cake4/src4/View/Helper/ArtistFormatterHelper.php",
            "cake4/src4/View/AppView.php",
            "cake4/src4/Model/Table/MoviesTable.php",
            "cake4/vendor/cakephp.php"
        )
    }

    fun `test type is communicated from controller to view`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
            
        <?php
        echo ${'$'}moviesTable-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("findOwnedBy"))
    }

}