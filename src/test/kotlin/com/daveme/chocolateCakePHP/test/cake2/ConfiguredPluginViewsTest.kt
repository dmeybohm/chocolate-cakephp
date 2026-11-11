package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.CakeTwoPluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.test.ViewNavigation
import com.daveme.chocolateCakePHP.view.viewFileOf
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileDataIndexer
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableASTDataIndexer
import com.daveme.chocolateCakePHP.view.viewvariableindex.elementDataKey
import com.intellij.util.indexing.FileContentImpl

class ConfiguredPluginViewsTest : Cake2BaseTestCase() {

    private val navigation by lazy { ViewNavigation(myFixture) }

    override fun setUpTestFiles() {
        myFixture.configureByFiles("cake2/app/Controller/AppController.php")
        val settings = Settings.getInstance(myFixture.project)
        val state = settings.state.copy()
        state.pluginConfigs = listOf(
            CakeTwoPluginConfig(pluginName = "Blog", pluginPath = "plugins/Blog"),
            CakeTwoPluginConfig(pluginName = "Shop", pluginPath = "lib/Shop/"),
        )
        settings.loadState(state)
    }

    private fun postsController(pluginDir: String) {
        myFixture.addFileToProject("$pluginDir/Controller/PostsController.php", """
            <?php
            class PostsController extends AppController {
                public function viewAll() { ${'$'}this->layout = 'admin'; }
            }
        """.trimIndent())
    }

    fun `test go to related from a configured plugin's template and layout lists the action`() {
        postsController("cake2/plugins/Blog")
        val layout = myFixture.addFileToProject("cake2/plugins/Blog/View/Layouts/admin.ctp", "<?php echo 1;")
        val template = myFixture.addFileToProject("cake2/plugins/Blog/View/Posts/view_all.ctp", "<?php echo 1;")

        assertEquals(setOf("Controllers: PostsController::viewAll()"), navigation.related(layout))
        assertEquals(setOf("Controllers: PostsController::viewAll()"), navigation.related(template))
    }

    fun `test configured plugin at an explicit path is navigable and named`() {
        postsController("cake2/lib/Shop")
        val layout = myFixture.addFileToProject("cake2/lib/Shop/View/Layouts/admin.ctp", "<?php echo 1;")
        val template = myFixture.addFileToProject("cake2/lib/Shop/View/Posts/view_all.ctp", "<?php echo 1;")

        assertEquals(setOf("Controllers: PostsController::viewAll()"), navigation.related(layout))
        assertEquals(setOf("Controllers: PostsController::viewAll()"), navigation.related(template))
        val viewFile = viewFileOf(project, Settings.getInstance(project), template.virtualFile)
        assertEquals("Shop", viewFile?.plugin)
    }

    fun `test element call in a configured plugin's view is indexed`() {
        val caller = myFixture.addFileToProject("cake2/plugins/Blog/View/Posts/caller.ctp", """
            <?php
            echo ${'$'}this->element('nav', ['x' => ${'$'}y]);
        """.trimIndent()).virtualFile

        val content = FileContentImpl.createByFile(caller, project)
        assertTrue(ViewFileDataIndexer.map(content).containsKey("Elements/nav"))
        assertTrue(ViewVariableASTDataIndexer.map(content).containsKey(elementDataKey("Elements/nav")))
    }

    fun `test view of an unconfigured root plugin has no templates directory`() {
        val template = myFixture.addFileToProject("cake2/plugins/Forum/View/Posts/index.ctp", "<?php echo 1;")
        assertNull(templatesDirectoryOfViewFile(project, Settings.getInstance(project), template.virtualFile))
    }
}
