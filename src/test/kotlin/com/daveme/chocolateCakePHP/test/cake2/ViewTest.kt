package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class ViewTest : Cake2BaseTestCase() {

    override fun prepareTest() {
        myFixture.configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/Controller/Component/AppComponent.php",
            "cake2/app/Controller/Component/MovieMetadataComponent.php",
            "cake2/app/Model/AppModel.php",
            "cake2/app/Model/Movie.php",
            "cake2/app/Model/Artist.php",
            "cake2/app/Model/Director.php",
            "cake2/app/Model/Movie.php",
            "cake2/app/View/Elements/Flash/default.ctp",
            "cake2/app/View/Helper/AppHelper.php",
            "cake2/app/View/Helper/ArtistFormatterHelper.php",
            "cake2/app/View/Helper/MovieFormatterHelper.php",
            "cake2/app/View/Helper/MyViewHelper.php",
            "cake2/app/View/Layouts/Emails/html/default.ctp",
            "cake2/vendor/cakephp.php"
        )
    }

    @Test
    public fun `test completing view helper inside a view helper for cake2`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Helper/MovieFormatterHelper.php", """
        <?php

        class MovieFormatterHelper extends AppHelper
        {
            public function format(array ${'$'}movies): string {
                return ${'$'}this-><caret>;
            }
        }
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        BasePlatformTestCase.assertTrue(result!!.contains("ArtistFormatter"))
        BasePlatformTestCase.assertFalse(result.contains("MovieFormatter"))
    }

}