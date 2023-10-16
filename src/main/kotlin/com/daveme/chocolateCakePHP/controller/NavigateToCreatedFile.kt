package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.topSourceDirectoryFromFile
import com.daveme.chocolateCakePHP.virtualFileToPsiFile
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent


class NavigateToCreatedFile(val destinationPath: String) : GutterIconNavigationHandler<PsiElement> {

    override fun navigate(e: MouseEvent, elt: PsiElement?) {
        val element = elt as? PsiElement ?: return
        val project = element.project


        val popup = JBPopupFactory.getInstance()
            .createConfirmation("Create view file", "Create view file", "Cancel",
                {
                    val template = FileTemplateManager.getInstance(project)
                        .getCodeTemplate("CakePHP View File.php")
                    val text = template.getText()

                    // Create file
                    WriteCommandAction.runWriteCommandAction(project) {
                        val baseDir = project.guessProjectDir() ?: return@runWriteCommandAction

                        // TODO: also create directories that don't exist along path here.
                        val parentDir = destinationPath.substring(0, destinationPath.lastIndexOf('/'));
                        val parentDirVirtualFile = baseDir.findFileByRelativePath(parentDir)
                        val parentDirPsiFile = if (parentDirVirtualFile != null && parentDirVirtualFile.isDirectory) {
                            PsiManager.getInstance(project).findDirectory(parentDirVirtualFile)
                        } else {
                            null
                        } ?: return@runWriteCommandAction

                        val psiFile = PsiFileFactory.getInstance(project)
                            .createFileFromText(
                                destinationPath,
                                FileTypeManager.getInstance().getFileTypeByFileName(destinationPath),
                                text
                            )

                        parentDirPsiFile.add(psiFile) as PsiFile

                        // Open file in editor
                        val editor: Editor? = FileEditorManager.getInstance(project).openTextEditor(
                            OpenFileDescriptor(project, psiFile.virtualFile),
                            true
                        )
//                        if (editor != null) {
//                            FileEditorManager.getInstance(project).scrollToCaret(editor)
//                        }
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

        popup.show(RelativePoint(e));
    }
}
