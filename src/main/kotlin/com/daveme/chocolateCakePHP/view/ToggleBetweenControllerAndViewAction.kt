package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.daveme.chocolateCakePHP.createViewActionPopupFromAllViewPaths
import com.daveme.chocolateCakePHP.getScreenPoint
import com.daveme.chocolateCakePHP.showPsiElementPopupFromEditor
import com.daveme.chocolateCakePHP.showPsiFilePopupFromEditor
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.PsiNavigateUtil
import com.jetbrains.php.lang.psi.elements.Method
import java.awt.Point
import java.awt.event.MouseEvent


class ToggleBetweenControllerAndViewAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        val project = e.project ?: return

        val settings = Settings.getInstance(project)
        if (!settings.enabled) {
            return
        }

        val psiFile = getPsiFile(project, e) ?: return
        val isViewFile = isCakeViewFile(project, settings, psiFile)
        val isControllerFile = isCakeControllerFile(psiFile)

        e.presentation.isEnabled = isViewFile || isControllerFile
        e.presentation.isVisible = isViewFile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val virtualFile = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return
        val settings = Settings.getInstance(project)

        if (isCakeViewFile(project, settings, psiFile)) {
            tryToNavigateFromView(project, settings, virtualFile, psiFile, e)
        } else if (isCakeControllerFile(psiFile)) {
            tryToNavigateToView(project, settings, virtualFile, psiFile, e)
        }
    }

    private fun tryToNavigateToView(
        project: Project,
        settings: Settings,
        virtualFile: VirtualFile,
        psiFile: PsiFile,
        e: AnActionEvent
    ) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)
        val method = PsiTreeUtil.getParentOfType(element, Method::class.java) ?: return

        val actionNames = actionNamesFromControllerMethod(method)
        val topSourceDirectory = topSourceDirectoryFromSourceFile(settings, psiFile)
            ?: return
        val templatesDirectory = templatesDirectoryFromTopSourceDirectory(project, settings, topSourceDirectory)
            ?: return
        val controllerName = virtualFile.nameWithoutExtension.controllerBaseName()
            ?: return

        val templatesDirWithPath = templatesDirWithPath(project, templatesDirectory)
            ?: return
        val allViewPaths = allViewPathsFromController(
            controllerName,
            templatesDirWithPath,
            settings,
            actionNames
        )
        val files = viewFilesFromAllViewPaths(
            project = project,
            templatesDirectory = templatesDirectory,
            allViewPaths = allViewPaths
        )

        when (files.size) {
            0 -> {
                val getContext = DataManager.getInstance().dataContextFromFocusAsync
                getContext.then { context ->
                    val popup = JBPopupFactory.getInstance()
                        .createActionGroupPopup(
                            "Create View File",
                            createViewActionPopupFromAllViewPaths(allViewPaths),
                            context,
                            JBPopupFactory.ActionSelectionAid.NUMBERING,
                            true,
                        )
                    popup.showInBestPositionFor(editor)
                }
            }
            1 -> {
                val first = files.first().virtualFile
                FileEditorManager.getInstance(project).openFile(first, true)
            }
            else -> {
                showPsiFilePopupFromEditor(files.toList(), project, editor)
            }
        }
    }

    private fun tryToNavigateFromView(
        project: Project,
        settings: Settings,
        virtualFile: VirtualFile,
        psiFile: PsiFile,
        e: AnActionEvent,
    ) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val inputEvent = e.inputEvent as? MouseEvent
        val point = inputEvent?.getScreenPoint()


        val templatesDir = templatesDirectoryFromViewFile(project, settings, psiFile) ?: return
        val templateDirVirtualFile = templatesDir.psiDirectory.virtualFile
        val relativePath = VfsUtil.getRelativePath(virtualFile, templateDirVirtualFile) ?: return
        val filenameKey = ViewFileIndexService.canonicalizeFilenameToKey(templatesDir, settings, relativePath)
        val fileList = ViewFileIndexService.referencingElements(project, filenameKey)

        val targets = fileList.asSequence()
            .filter {
                it.psiElement.isValid
            }.map {
                it.psiElement
            }.toList()

        val relativePoint = if (point != null)
            RelativePoint(Point(Math.max(0, point.x - 400), point.y))
        else
            null
        openTargets(project, targets, editor, relativePoint)
    }

    private fun openTargets(
        project: Project,
        targetList: List<PsiElement>,
        editor: Editor,
        relativePoint: RelativePoint?
    ) {
        when (targetList.size) {
            0 -> {}
            1 -> {
                PsiNavigateUtil.navigate(targetList.first())
            }
            else -> {
                showPsiElementPopupFromEditor(targetList, project, editor, relativePoint)
            }
        }
    }

    private fun getPsiFile(project: Project, e: AnActionEvent): PsiFile? {
        val virtualFile = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile)
    }

}
