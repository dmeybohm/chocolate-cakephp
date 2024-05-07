package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import org.junit.Test

class ViewTest : Cake4BaseTestCase() {

    override fun prepareTest() {
        myFixture.configureByFiles(
            "cake4/src2/Controller/AppController.php",
            "cake4/src2/Controller/Component/MovieMetadataComponent.php",
            "cake4/src2/View/Helper/MovieFormatterHelper.php",
            "cake4/src2/View/Helper/ArtistFormatterHelper.php",
            "cake4/src2/View/AppView.php",
            "cake4/vendor/cakephp.php"
        )
    }

    @Test
    fun `test completing view helper inside a view for cake4`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/artist.php", """
        <?php
        ${'$'}this-><caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("MovieFormatter"))
    }

    @Test
    fun `test completing view helper inside a view helper for cake4`() {
        myFixture.configureByFilePathAndText("cake4/src2/View/Helper/MovieFormatterHelper.php", """
        <?php
        namespace App\View\Helper;

        class MovieFormatterHelper extends \Cake\View\Helper
        {
            public function format(array ${'$'}movies): string {
                return ${'$'}this-><caret>;
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("ArtistFormatter"))
        assertFalse(result.contains("MovieFormatter"))
    }

}