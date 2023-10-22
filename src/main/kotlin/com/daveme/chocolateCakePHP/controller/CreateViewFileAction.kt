package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.cake.CakeIcons
import com.daveme.chocolateCakePHP.createDirectoriesIfMissing
import com.daveme.chocolateCakePHP.cake.viewFilePathInfoFromPath
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager

class CreateViewFileAction(
    val destinationPath: String = "",
    val useCustomPath: Boolean = false,
) : AnAction(CakeIcons.LOGO) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return

        // Create file
        WriteCommandAction.runWriteCommandAction(
            project,
            "Create View File",
            null,
            object : Runnable {
                override fun run() {
                    val template = FileTemplateManager.getInstance(project)
                        .getInternalTemplate("CakePHP View File.php")
                    val text = template.getText()

                    val baseDir = project.guessProjectDir() ?: return

                    val viewFilePathInfo = viewFilePathInfoFromPath(destinationPath) ?: return
                    val parentDir = viewFilePathInfo.templateAndControllerPath
                    val filename = viewFilePathInfo.viewFilename
                    val baseDirPath = baseDir.path
                    if (!createDirectoriesIfMissing("${baseDirPath}/${parentDir}")) {
                        return
                    }
                    val parentDirVirtualFile = baseDir.findFileByRelativePath(parentDir) ?: return
                    if (!parentDirVirtualFile.isDirectory) {
                        return
                    }
                    val parentDirPsiFile = PsiManager.getInstance(project).findDirectory(parentDirVirtualFile)
                        ?: return

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
            }
        )
    }

    override fun update(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT)
        if (project == null || destinationPath == "") {
            event.presentation.isEnabled = false
            return
        }
        event.presentation.setEnabledAndVisible(true)
        event.presentation.text = if (useCustomPath)
            "Create Custom View File"
        else
            "Create Default View File"
    }

}