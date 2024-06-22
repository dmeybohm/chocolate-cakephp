package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.daveme.chocolateCakePHP.controller.createViewActionPopupFromAllViewPaths
import com.daveme.chocolateCakePHP.controller.showPsiElementPopupFromEditor
import com.daveme.chocolateCakePHP.controller.showPsiFilePopupFromEditor
import com.daveme.chocolateCakePHP.view.index.ViewFileIndexService
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
import com.jetbrains.php.lang.psi.elements.Method


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
        e: AnActionEvent
    ) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val templatesDir = templatesDirectoryFromViewFile(project, settings, psiFile) ?: return
        val templateDirVirtualFile = templatesDir.psiDirectory.virtualFile
        val relativePath = VfsUtil.getRelativePath(virtualFile, templateDirVirtualFile) ?: return
        val pathParts = relativePath.split("/")
        if (pathParts.size <= 1) {
            return
        }
        val filenameKey = ViewFileIndexService.canonicalizeFilenameToKey(relativePath, settings)
        val fileList = ViewFileIndexService.referencingElements(project, filenameKey)

        val viewFileName = virtualFile.nameWithoutExtension
        val potentialControllerName = pathParts[0]

        val controllerMethod = getControllerMethod(
            project,
            settings,
            templatesDir,
            potentialControllerName,
            viewFileName
        )

        val targets = fileList.map {
            it.psiElement
        } + listOf(controllerMethod)
            .mapNotNull { it }

        openTargets(project, targets, editor)
    }

    private fun openTargets(project: Project, targetList: List<PsiElement>, editor: Editor) {
        when (targetList.size) {
            0 -> {}
            1 -> {
                val target = targetList.first().containingFile.virtualFile
                FileEditorManager.getInstance(project).openFile(target, true)
            }
            else -> {
                showPsiElementPopupFromEditor(targetList, project, editor)
            }
        }
    }

    private fun getControllerMethod(
        project: Project,
        settings: Settings,
        templatesDir: TemplatesDir,
        potentialControllerName: String,
        viewFilename: String
    ): PsiElement? {
        val controllerClasses = getControllerClassesOfPotentialControllerName(project, settings, potentialControllerName)
        val method = controllerMethodFromViewFilename(controllerClasses, settings, viewFilename, templatesDir)

        if (method == null || !method.canNavigate()) {
            return null
        } else {
            return method
        }
    }

    private fun getPsiFile(project: Project, e: AnActionEvent): PsiFile? {
        val virtualFile = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile)
    }

}
