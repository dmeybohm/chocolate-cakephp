package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.PluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.AssetGotoDeclarationHandler
import com.daveme.chocolateCakePHP.view.ElementGotoDeclarationHandler
import com.daveme.chocolateCakePHP.view.TemplateGotoDeclarationHandler

class PluginSupportTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Controller/MovieController.php",
            "cake5/src5/View/AppView.php",
            "cake5/templates/Movie/artist.php",
            "cake5/webroot/css/movie.css",
            "cake5/webroot/js/movie.js",
            "cake5/webroot/img/pluginIcon.svg",
            "cake5/plugins/TestPlugin/webroot/css/plugin_style.css",
            "cake5/plugins/TestPlugin/webroot/js/plugin_script.js",
            "cake5/plugins/TestPlugin/webroot/img/plugin_logo.png",
            "cake5/plugins/TestPlugin/templates/element/sidebar.php",
            "cake5/plugins/TestPlugin/templates/TestController/index.php",
            "cake5/vendor/cakephp.php",
        )
    }

    override fun setUp() {
        super.setUp()

        // Configure the TestPlugin
        val settings = Settings.getInstance(myFixture.project)
        val state = settings.state.copy()
        state.pluginConfigs = listOf(
            PluginConfig(
                pluginName = "TestPlugin",
                namespace = "\\TestPlugin",
                pluginPath = "plugins/TestPlugin",
                srcPath = "src",
                assetPath = "webroot"
            )
        )
        settings.loadState(state)
    }

    // ========== Asset Navigation Tests ==========

    fun `test can go to plugin css assets`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->css('<caret>TestPlugin.plugin_style');
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "plugin_style.css")
    }

    fun `test can go to plugin js assets`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->script('<caret>TestPlugin.plugin_script');
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "plugin_script.js")
    }

    fun `test can go to plugin image assets`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->image('<caret>TestPlugin.plugin_logo.png');
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "plugin_logo.png")
    }

    fun `test can go to plugin css assets in array`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->css(['<caret>TestPlugin.plugin_style']);
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "plugin_style.css")
    }

    fun `test plugin prefix does not affect non-prefixed assets`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->css('<caret>movie');
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "movie.css")
    }

    fun `test unknown plugin returns no results`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        ${'$'}this->Html->css('<caret>UnknownPlugin.some_style');
        """.trimIndent())
        val handler = AssetGotoDeclarationHandler()
        val targets = gotoDeclarationHandlerTargets(handler)
        assertNotNull(targets)
        assertEmpty(targets!!)
    }

    // ========== Element Navigation Tests ==========

    fun `test can go to plugin element`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->element('<caret>TestPlugin.sidebar');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "sidebar.php")
    }

    fun `test plugin element with unknown plugin returns no results`() {
        myFixture.configureByFilePathAndText("cake5/templates/Movie/artist.php", """
        <?php
        echo ${'$'}this->element('<caret>UnknownPlugin.sidebar');
        """.trimIndent())
        val handler = ElementGotoDeclarationHandler()
        val targets = gotoDeclarationHandlerTargets(handler)
        assertNotNull(targets)
        assertEmpty(targets!!)
    }

    // ========== Template Navigation Tests ==========

    fun `test can go to plugin template from render`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        class MovieController extends AppController {
            public function index() {
                ${'$'}this->render('<caret>TestPlugin.TestController/index');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "index.php")
    }

    fun `test can go to plugin template from setTemplate`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        class MovieController extends AppController {
            public function index() {
                ${'$'}this->viewBuilder()->setTemplate('<caret>TestPlugin.TestController/index');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "index.php")
    }

    fun `test plugin template with unknown plugin returns no results`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
        <?php
        namespace App\Controller;

        class MovieController extends AppController {
            public function index() {
                ${'$'}this->render('<caret>UnknownPlugin.TestController/index');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        val targets = gotoDeclarationHandlerTargets(handler)
        assertNotNull(targets)
        assertEmpty(targets!!)
    }

}
