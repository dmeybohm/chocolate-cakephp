package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.ChocolateCakePHPBundle
import com.daveme.chocolateCakePHP.cake.CakeIcons
import com.daveme.chocolateCakePHP.createDirectoriesIfMissing
import com.daveme.chocolateCakePHP.cake.viewFilePathInfoFromPath
import com.daveme.chocolateCakePHP.mneumonicEscape
import com.daveme.chocolateCakePHP.showErrorDialog
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager

class CreateViewFileAction(
    val destinationPath: String = "",
    title: String = "Create Default View File",
    val allowEdit: Boolean = false,
) : AnAction(
    title,
    "Create view file",
    CakeIcons.LOGO_PNG
) {

    private val _title = title
    val title: String get() = _title

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val baseDir = project.guessProjectDir() ?: return

        val filePath = if (allowEdit) {
            val userInput = Messages.showInputDialog(
                project,
                ChocolateCakePHPBundle.message("createView.dialog.title"),
                ChocolateCakePHPBundle.message("createView.dialog.createTitle"),
                Messages.getQuestionIcon(),
                destinationPath,
                object : InputValidator {
                    override fun checkInput(inputString: String?): Boolean = true
                    override fun canClose(inputString: String?): Boolean = true
                }
            )
            if (userInput != null) userInput else null
        } else {
            destinationPath
        } ?: return

        // Create file
        WriteCommandAction.runWriteCommandAction(
            project,
            ChocolateCakePHPBundle.message("createView.command.name"),
            null,
            object : Runnable {
                override fun run() {

                    val template = FileTemplateManager.getInstance(project)
                        .getInternalTemplate("CakePHP View File.php")
                    val text = template.getText()

                    val viewFilePathInfo = viewFilePathInfoFromPath(filePath) ?: return
                    val parentDir = viewFilePathInfo.templateDirPath
                    val filename = viewFilePathInfo.viewFilename
                    val baseDirPath = baseDir.path
                    if (!createDirectoriesIfMissing("${baseDirPath}/${parentDir}")) {
                        showError(ChocolateCakePHPBundle.message("createView.error.createDirectories"))
                        return
                    }
                    val parentDirVirtualFile = baseDir.findFileByRelativePath(parentDir) ?: return
                    if (!parentDirVirtualFile.isDirectory) {
                        showError(ChocolateCakePHPBundle.message("createView.error.findDirectory"))
                        return
                    }
                    val parentDirPsiFile = PsiManager.getInstance(project).findDirectory(parentDirVirtualFile)
                    if (parentDirPsiFile == null) {
                        showError(ChocolateCakePHPBundle.message("createView.error.findDirectory"))
                        return
                    }

                    // Check if file exists already:
                    val child = parentDirVirtualFile.findChild(filename)
                    if (child != null && child.exists()) {
                        showError(ChocolateCakePHPBundle.message("createView.error.fileExists"))
                        return
                    }

                    val psiFile = PsiFileFactory.getInstance(project)
                        .createFileFromText(
                            filename,
                            FileTypeManager.getInstance().getFileTypeByFileName(filename),
                            text
                        )
                    CodeStyleManager.getInstance(project).reformat(psiFile)

                    val result = parentDirPsiFile.add(psiFile)
                    if (result is PsiFile) {
                        OpenFileDescriptor(project, result.virtualFile).navigate(true)
                    }
                }

                fun showError(error: String) {
                    showErrorDialog(error, ChocolateCakePHPBundle.message("createView.dialog.createTitle"))
                }
            }

        )
    }

    override fun update(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT)
        if (project == null || destinationPath == "") {
            event.presentation.isEnabledAndVisible = false
            return
        }
        event.presentation.setEnabledAndVisible(true)
        event.presentation.text = title.mneumonicEscape()
    }

}