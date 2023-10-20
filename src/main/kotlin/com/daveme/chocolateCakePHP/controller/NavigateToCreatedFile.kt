package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.topSourceDirectoryFromFile
import com.daveme.chocolateCakePHP.virtualFileToPsiFile
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities


class NavigateToCreatedFile(val destinationPath: String) : GutterIconNavigationHandler<PsiElement> {

    override fun navigate(e: MouseEvent, elt: PsiElement?) {
        val element = elt ?: return
        val project = element.project

        val resultPath = destinationPath
        val popup = JBPopupFactory.getInstance()
            .createConfirmation("Create View File", "Create view file", "Cancel",
                {
                    val template: FileTemplate = FileTemplateManager.getInstance(project)
                        .getInternalTemplate("CakePHP View File.php")
                    println("Internal template: ${template}")
//                        .getCodeTemplate("PHP File.php")
                    val text = template.getText()

                    // Create file
                    WriteCommandAction.runWriteCommandAction(project) {
                        val baseDir = project.guessProjectDir() ?: return@runWriteCommandAction

                        // TODO: also create directories that don't exist along path here.
                        val lastSlash = resultPath.lastIndexOf('/')
                        val parentDir = resultPath.substring(0, lastSlash)
                        val filename = resultPath.substring(lastSlash + 1)
                        val parentDirVirtualFile = baseDir.findFileByRelativePath(parentDir)
                        val parentDirPsiFile = if (parentDirVirtualFile != null && parentDirVirtualFile.isDirectory) {
                            PsiManager.getInstance(project).findDirectory(parentDirVirtualFile)
                        } else {
                            null
                        } ?: return@runWriteCommandAction

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
//                            if (editor != null) {
//                                FileEditorManager.getInstance(project).scrollToCaret(editor)
//                            }

                        }
                    }
                },
                0
            )

//        val step = BaseListPopupStep("Create view file", listOf("Create view file"))
//        val popup = JBPopupFactory.getInstance()
//            .createListPopup(step)
//            .addListSelectionListener(object {
//                k
//            })

        popup.show(RelativePoint(e))
    }
}
