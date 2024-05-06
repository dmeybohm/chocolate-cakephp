package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.daveme.chocolateCakePHP.controller.NavigateToViewPopupHandler
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method

class ToggleBetweenControllerAndView : AnAction() {

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
        val isControllerFile = isCakeControllerFile(project, settings, psiFile)

        e.presentation.isEnabled = isViewFile || isControllerFile
        e.presentation.isVisible = isViewFile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val virtualFile = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return
        val settings = Settings.getInstance(project)

        if (isCakeViewFile(project, settings, psiFile)) {
            tryToNavigateToController(project, settings, virtualFile, psiFile)
        } else if (isCakeControllerFile(project, settings, psiFile)) {
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
        val topSourceDirectory = topSourceDirectoryFromControllerFile(settings, psiFile)
            ?: return
        val templateDirectory = templatesDirectoryFromTopSourceDirectory(project, settings, topSourceDirectory)
            ?: return
        val controllerName = virtualFile.nameWithoutExtension.controllerBaseName()
            ?: return

        val files = viewFilesFromControllerAction(
            project = project,
            templatesDirectory = templateDirectory,
            settings = settings,
            controllerName = controllerName,
            actionNames = actionNames
        )

        when (files.size) {
            0 -> {
                val defaultViewFile = defaultViewFileFromController(
                    project,
                    controllerName,
                    templateDirectory,
                    settings,
                    actionNames
                )
                val getContext = DataManager.getInstance().dataContextFromFocusAsync
                getContext.then { context ->
                    val popup = JBPopupFactory.getInstance()
                        .createActionGroupPopup(
                            "Create View File",
                            NavigateToViewPopupHandler.CreateViewFileActionGroup(defaultViewFile),
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
                NavigationUtil.getPsiElementPopup(
                    files.sortedBy { it.virtualFile.path }.toTypedArray(),
                    "Select Target To Navigate"
                ).showInBestPositionFor(editor)
            }
        }
    }

    private fun tryToNavigateToController(
        project: Project,
        settings: Settings,
        virtualFile: VirtualFile,
        psiFile: PsiFile
    ) {
        val templatesDir = templatesDirectoryFromViewFile(project, settings, psiFile) ?: return
        val templateDirVirtualFile = templatesDir.psiDirectory.virtualFile
        val relativePath = VfsUtil.getRelativePath(virtualFile, templateDirVirtualFile) ?: return
        val pathParts = relativePath.split("/")
        if (pathParts.size <= 1) {
            return
        }
        val viewFileName = virtualFile.nameWithoutExtension
        val potentialControllerName = pathParts[0]

        openAndNavigateToController(
            project,
            settings,
            templatesDir,
            potentialControllerName,
            viewFileName
        )
    }

    private fun openAndNavigateToController(
        project: Project,
        settings: Settings,
        templatesDir: TemplatesDir,
        potentialControllerName: String,
        viewFileName: String
    ) {
        val controllerType = controllerTypeFromControllerName(settings, potentialControllerName)
        val phpIndex = PhpIndex.getInstance(project)
        val controllerClasses = phpIndex.phpClassesFromType(controllerType)
        val actionNames = viewFileNameToActionName(viewFileName, settings, templatesDir)
        val method = controllerClasses.findFirstMethodWithName(actionNames.defaultActionName)

        if (method == null || !method.canNavigate()) {
            val topSrcDir = topSourceDirectoryFromTemplatesDirectory(templatesDir, project, settings)
                ?: return
            val controllerPath = "Controller/${potentialControllerName}Controller.php"
            val targetController = findRelativeFile(topSrcDir.psiDirectory, controllerPath)
                ?: return
            FileEditorManager.getInstance(project).openFile(targetController, true)
            return
        } else {
            method.navigate(true)
        }
    }


    private fun getPsiFile(project: Project, e: AnActionEvent): PsiFile? {
        val virtualFile = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile)
    }
}
