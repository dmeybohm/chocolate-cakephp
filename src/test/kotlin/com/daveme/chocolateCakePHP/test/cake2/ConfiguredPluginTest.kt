package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.CakeTwoPluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.allTemplatePathsFromTopSourceDirectory
import com.daveme.chocolateCakePHP.cake.topSourceDirectoryFromSourceFile
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.AssetGotoDeclarationHandler
import com.daveme.chocolateCakePHP.view.ElementGotoDeclarationHandler

class ConfiguredPluginTest : Cake2BaseTestCase() {

    override fun setUpTestFiles() {
        configureByFiles(
            "cake2/app/Controller/AppController.php",
            "cake2/app/View/AppView.php",
            "cake2/app/View/Movie/artist.ctp",
            "cake2/app/webroot/css/movie.css",
            "cake2/vendor/cakephp.php",
        )
        myFixture.addFileToProject("cake2/lib/Shop/View/Elements/promo.ctp", "")
        myFixture.addFileToProject("cake2/lib/Shop/webroot/css/shop_style.css", "")
        myFixture.addFileToProject("cake2/lib/Blog/View/Elements/teaser.ctp", "")

        val settings = Settings.getInstance(myFixture.project)
        val state = settings.state.copy()
        state.pluginConfigs = listOf(
            CakeTwoPluginConfig(pluginName = "Shop", pluginPath = "lib/Shop"),
            CakeThreePluginConfig(pluginName = "Blog", namespace = "\\Blog", pluginPath = "lib/Blog"),
        )
        settings.loadState(state)
    }

    fun `test can go to element of a configured plugin`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->element('<caret>Shop.promo');
        """.trimIndent())
        assertGotoDeclarationHandlerGoesToFilename(ElementGotoDeclarationHandler(), "promo.ctp")
    }

    fun `test can go to asset of a configured plugin`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->Html->css('<caret>Shop.shop_style');
        """.trimIndent())
        assertGotoDeclarationHandlerGoesToFilename(AssetGotoDeclarationHandler(), "shop_style.css")
    }

    fun `test element of a plugin with no template directory goes nowhere`() {
        myFixture.configureByFilePathAndText("cake2/app/View/Movie/artist.ctp", """
        <?php
        ${'$'}this->element('<caret>Blog.teaser');
        """.trimIndent())
        val targets = gotoDeclarationHandlerTargets(ElementGotoDeclarationHandler())
        assertNotNull(targets)
        assertEmpty(targets!!)
    }

    fun `test only the cakephp 2 plugin is searched for a View directory`() {
        val settings = Settings.getInstance(project)
        val viewFile = myFixture.findFileInTempDir("cake2/app/View/Movie/artist.ctp")
        val topDir = topSourceDirectoryFromSourceFile(settings, viewFile.parent)!!
        val pluginPaths = allTemplatePathsFromTopSourceDirectory(project, settings, topDir)!!
            .pluginAndThemeTemplatePaths.paths.map { it.templatesPath }
        assertEquals(listOf("lib/Shop/View"), pluginPaths)
    }
}
