package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.CakeTwoPluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase
import com.daveme.chocolateCakePHP.test.ViewNavigation
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileDataIndexer
import com.intellij.util.indexing.FileContentImpl

class BothVersionsLayoutNavigationTest : BaseTestCase() {

    override val cakeMajorVersion = 2

    private val navigation by lazy { ViewNavigation(myFixture) }

    override fun setUp() {
        super.setUp()
        val settings = Settings.getInstance(myFixture.project)
        val state = Settings.getDefaults(myFixture.project).state.copy()
        state.cake2Enabled = true
        state.cake3ForceEnabled = true
        state.cake2AppDirectory = "app"
        state.appDirectory = "src"
        settings.loadState(state)
        myFixture.configureByFiles("cake2/app/Controller/AppController.php")
    }

    fun `test plugin controller keeps the CakePHP 2 conventions`() {
        myFixture.addFileToProject("cake2/app/Plugin/Blog/Controller/PostsController.php", """
            <?php
            class PostsController extends AppController {
                public function viewAll() { ${'$'}this->layout = 'admin'; }
            }
        """.trimIndent())
        val layout = myFixture.addFileToProject("cake2/app/Plugin/Blog/View/Layouts/admin.ctp", "<?php echo 1;")
        val template = myFixture.addFileToProject("cake2/app/Plugin/Blog/View/Posts/view_all.ctp", "<?php echo 1;")

        assertEquals(setOf("Controllers: PostsController::viewAll()"), navigation.related(layout))
        assertEquals(setOf("Controllers: PostsController::viewAll()"), navigation.related(template))
    }

    fun `test app controller keeps the CakePHP 2 conventions beside a templates directory`() {
        myFixture.addFileToProject("cake2/templates/Posts/index.php", "<?php echo 1;")
        myFixture.addFileToProject("cake2/app/Controller/PostsController.php", """
            <?php
            class PostsController extends AppController {
                public function viewAll() { ${'$'}this->layout = 'admin'; }
            }
        """.trimIndent())
        val layout = myFixture.addFileToProject("cake2/app/View/Layouts/admin.ctp", "<?php echo 1;")

        assertEquals(setOf("Controllers: PostsController::viewAll()"), navigation.related(layout))
    }

    fun `test configured plugin controller beside the app stores CakePHP 2 layout keys`() {
        val settings = Settings.getInstance(myFixture.project)
        val state = settings.state.copy()
        state.pluginConfigs = listOf(CakeTwoPluginConfig(pluginName = "Blog", pluginPath = "plugins/Blog"))
        settings.loadState(state)
        myFixture.addFileToProject("cake2/templates/Posts/index.php", "<?php echo 1;")
        val controller = myFixture.addFileToProject("cake2/plugins/Blog/Controller/PostsController.php", """
            <?php
            class PostsController extends AppController {
                public function viewAll() { ${'$'}this->layout = 'admin'; }
            }
        """.trimIndent()).virtualFile

        val keys = ViewFileDataIndexer.map(FileContentImpl.createByFile(controller, project)).keys
        assertTrue("Keys were: $keys", "Layouts/admin" in keys)
        assertFalse("Keys were: $keys", "Layout/admin" in keys)
    }

    fun `test configured plugin beside the app navigates from its template and layout`() {
        val settings = Settings.getInstance(myFixture.project)
        val state = settings.state.copy()
        state.pluginConfigs = listOf(CakeTwoPluginConfig(pluginName = "Blog", pluginPath = "plugins/Blog"))
        settings.loadState(state)
        myFixture.addFileToProject("cake2/plugins/Blog/Controller/PostsController.php", """
            <?php
            class PostsController extends AppController {
                public function viewAll() { ${'$'}this->layout = 'admin'; }
            }
        """.trimIndent())
        val layout = myFixture.addFileToProject("cake2/plugins/Blog/View/Layouts/admin.ctp", "<?php echo 1;")
        val template = myFixture.addFileToProject("cake2/plugins/Blog/View/Posts/view_all.ctp", "<?php echo 1;")

        assertEquals(setOf("Controllers: PostsController::viewAll()"), navigation.related(layout))
        assertEquals(setOf("Controllers: PostsController::viewAll()"), navigation.related(template))
    }

    fun `test the longer app directory setting wins when it nests the CakePHP 2 one`() {
        val keys = layoutKeysOfControllerIn("src/app", cake2AppDirectory = "src/app", appDirectory = "app")
        assertTrue("Keys were: $keys", "Layouts/admin" in keys)
        assertFalse("Keys were: $keys", "Layout/admin" in keys)
    }

    fun `test the longer app directory setting wins when it nests the CakePHP 3 one`() {
        val keys = layoutKeysOfControllerIn("src/app", cake2AppDirectory = "app", appDirectory = "src/app")
        assertTrue("Keys were: $keys", "Layout/admin" in keys)
        assertFalse("Keys were: $keys", "Layouts/admin" in keys)
    }

    private fun layoutKeysOfControllerIn(appPath: String, cake2AppDirectory: String, appDirectory: String): Set<String> {
        val settings = Settings.getInstance(myFixture.project)
        val state = settings.state.copy()
        state.cake2AppDirectory = cake2AppDirectory
        state.appDirectory = appDirectory
        settings.loadState(state)
        val controller = myFixture.addFileToProject("cake2/$appPath/Controller/PostsController.php", """
            <?php
            class PostsController extends AppController {
                public function viewAll() { ${'$'}this->layout = 'admin'; }
            }
        """.trimIndent()).virtualFile
        return ViewFileDataIndexer.map(FileContentImpl.createByFile(controller, project)).keys
    }
}
