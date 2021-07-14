package com.daveme.chocolateCakePHP.test

import com.jetbrains.php.PhpCaches

class ViewTest : PluginTestCase() {

    fun `test completing view helper`() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/Component/MovieMetadataComponent.php",
            "cake3/src/View/Helper/MovieFormatterHelper.php",
            "cake3/src/View/AppView.php",
            "cake3/vendor/cakephp.php"
        )
        myFixture.configureByFile("cake3/src/Template/Movie/artist.ctp")
        myFixture.editor.caretModel.moveCaretRelatively("\$this->".length, 1, false, false, false)
        PhpCaches.getInstance(myFixture.project).SIGNATURES_CACHE
        myFixture.completeBasic();

        val result = myFixture.lookupElementStrings
        assertTrue(result!!.contains("MovieFormatter"))
    }

}