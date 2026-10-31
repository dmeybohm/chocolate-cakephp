package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.view.RenderingControllers
import com.daveme.chocolateCakePHP.view.viewFileOf

class FrameworkNotIndexedControllerHelperTest : Cake5BaseTestCase() {

    override fun setUpTestFiles() {
    }

    fun `test a layout finds controllers at any depth when the framework class is not indexed`() {
        assumeStubVendor("The real framework is always attached")
        myFixture.addFileToProject("cake5/src5/Controller/AppController.php", """
            <?php
            namespace App\Controller;
            class AppController extends \Cake\Controller\Controller {}
        """.trimIndent())
        myFixture.addFileToProject("cake5/src5/Controller/MovieController.php", """
            <?php
            namespace App\Controller;
            class MovieController extends AppController {}
        """.trimIndent())
        val layout = myFixture.addFileToProject("cake5/templates/layout/default.php", "<?php echo 1;")
        val viewFile = viewFileOf(project, Settings.getInstance(project), layout.virtualFile)!!
        assertEquals(
            setOf("\\App\\Controller\\AppController", "\\App\\Controller\\MovieController"),
            RenderingControllers.of(project, viewFile).toSet()
        )
    }
}
