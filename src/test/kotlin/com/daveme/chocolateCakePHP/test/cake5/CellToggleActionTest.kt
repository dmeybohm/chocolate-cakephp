package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.ToggleBetweenControllerAndViewAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.TestActionEvent

class CellToggleActionTest : Cake5BaseTestCase() {

    // The action is enabled but not visible outside view files, so testAction() would not
    // perform it; build the event and call it directly instead
    private fun performToggleAction(): Presentation {
        val action = ToggleBetweenControllerAndViewAction()
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.VIRTUAL_FILE, myFixture.file.virtualFile)
            .build()
        val event = TestActionEvent.createTestEvent(action, context)
        action.update(event)
        if (event.presentation.isEnabled) {
            action.actionPerformed(event)
        }
        return event.presentation
    }

    override fun setUpTestFiles() {
        configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/View/AppView.php",
            "cake5/vendor/cakephp.php",
        )
        // Copied without opening, so the toggle action is what opens them
        myFixture.copyFileToProject("cake5/templates/cell/Inventory/display.php")
        myFixture.copyFileToProject("cake5/templates/cell/Inventory/compact.php")
    }

    fun `test toggle action goes from a cell action to its template`() {
        myFixture.configureByFilePathAndText("cake5/src5/View/Cell/InventoryCell.php", """
        <?php
        namespace App\View\Cell;

        use Cake\View\Cell;

        class InventoryCell extends Cell
        {
            public function display(): void {
                ${'$'}items = [<caret>];
            }
        }
        """.trimIndent())
        val presentation = performToggleAction()
        assertTrue(presentation.isEnabled)

        val opened = FileEditorManager.getInstance(project).openFiles.map {
            it.path.substringAfter("/cake5/")
        }
        assertContainsElements(opened, "templates/cell/Inventory/display.php")
        assertDoesntContain(opened, "templates/cell/Inventory/compact.php")
    }

    fun `test toggle action is disabled in a plain class file`() {
        myFixture.configureByFilePathAndText("cake5/src5/View/AppView.php", """
        <?php
        namespace App\View;

        class AppView extends \Cake\View\View
        {
            public function initialize(): void {<caret>}
        }
        """.trimIndent())
        val presentation = performToggleAction()
        assertFalse(presentation.isEnabled)
    }

}
