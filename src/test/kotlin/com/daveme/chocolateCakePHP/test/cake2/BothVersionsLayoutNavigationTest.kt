package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase
import com.daveme.chocolateCakePHP.test.ViewNavigation

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
        val template = myFixture.addFileToProject("cake2/app/Plugin/Blog/View/Posts/viewAll.ctp", "<?php echo 1;")

        assertEquals(setOf("Controllers: PostsController::viewAll()"), navigation.related(layout))
        assertEquals(1, navigation.related(template).size)
    }
}
