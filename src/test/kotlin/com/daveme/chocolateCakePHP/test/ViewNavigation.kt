package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.navigation.CakeGotoRelatedItem
import com.daveme.chocolateCakePHP.view.ToggleBetweenControllerAndViewAction
import com.daveme.chocolateCakePHP.view.ViewToControllerGotoRelatedProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
 * The navigation a view file offers back to whatever refers to it.
 */
class ViewNavigation(private val fixture: CodeInsightTestFixture) {

    /**
     * What the toggle action did from a view file.
     */
    data class ToggleResult(val openedFile: String?, val message: String?)

    /**
     * The "go to related" items of [file], as `group: name`.
     */
    fun related(file: PsiFile): Set<String> =
        ViewToControllerGotoRelatedProvider().getItems(file.firstChild)
            .map { "${it.group}: ${(it as CakeGotoRelatedItem).customName}" }
            .toSet()

    /**
     * Performs the toggle action from [file] and waits for it to finish.
     */
    fun toggle(file: PsiFile): ToggleResult {
        fixture.openFileInEditor(file.virtualFile)
        val project = fixture.project
        var message: String? = null
        val previousDialog = TestDialogManager.setTestDialog(TestDialog { shown ->
            message = shown
            Messages.OK
        })
        try {
            val action = ToggleBetweenControllerAndViewAction()
            val context = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.EDITOR, fixture.editor)
                .add(CommonDataKeys.VIRTUAL_FILE, file.virtualFile)
                .build()
            action.actionPerformed(TestActionEvent.createTestEvent(action, context))

            val editors = FileEditorManager.getInstance(project)
            fun openedFile() = editors.selectedFiles.firstOrNull()?.takeIf { it != file.virtualFile }
            val deadline = System.currentTimeMillis() + 10_000
            while (message == null && openedFile() == null && System.currentTimeMillis() < deadline) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                Thread.sleep(10)
            }
            return ToggleResult(openedFile()?.name, message)
        } finally {
            TestDialogManager.setTestDialog(previousDialog)
        }
    }
}
