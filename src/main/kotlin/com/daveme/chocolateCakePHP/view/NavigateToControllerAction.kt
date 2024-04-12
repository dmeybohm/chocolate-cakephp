package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.*
import com.daveme.chocolateCakePHP.findRelativeFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.io.File

class NavigateToControllerAction : AnAction() {

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
        val enabled = isCakeViewFile(project, settings, psiFile)
        e.presentation.isEnabledAndVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val projectRoot = project.guessProjectDir() ?: return
        val virtualFile = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return
        val settings = Settings.getInstance(project)

        val templatesDir = templatesDirectoryFromViewFile(project, settings, psiFile) ?: return
        val templateDirVirtualFile = templatesDir.psiDirectory.virtualFile
        val relativePath = VfsUtil.getRelativePath(virtualFile, templateDirVirtualFile) ?: return;
        val pathParts = relativePath.split("/")
        if (pathParts.size <= 1) {
            return
        }
        val potentialControllerName = pathParts[0]

        when (templatesDir) {
            is CakeFourTemplatesDir, is CakeThreeTemplatesDir ->  {
                if (settings.cake3Enabled) {
                    tryToNavigateToCakeThreeController(project, projectRoot, settings, templatesDir, potentialControllerName)
                }
            }
            is CakeTwoTemplatesDir -> {
                if (settings.cake2Enabled) {
                    tryToNavigateToCakeTwoController(project, projectRoot, settings, templatesDir, potentialControllerName)
                }
            }
        }

    }

    private fun tryToNavigateToCakeTwoController(
        project: Project,
        projectRoot: VirtualFile,
        settings: Settings,
        templatesDir: TemplatesDir,
        potentialControllerName: String
    ) {
        val topPath = projectRoot.path
        val controllerPath = "${settings.cake2AppDirectory}/Controller/${potentialControllerName}Controller.php"
        val fullPath = "${topPath}/${controllerPath}"
        val file = File(fullPath)
        val fileURL = file.toURL().toString()
        val targetFile = VirtualFileManager.getInstance().findFileByUrl(fileURL) ?: return
        FileEditorManager.getInstance(project).openFile(targetFile, true)
    }

    private fun tryToNavigateToCakeThreeController(
        project: Project,
        projectRoot: VirtualFile,
        settings: Settings,
        templatesDir: TemplatesDir,
        potentialControllerName: String
    ) {
        val topSrcDir = topSourceDirectoryFromTemplatesDirectory(templatesDir, project, settings)
            ?: return
        val controllerPath = "Controller/${potentialControllerName}Controller.php"
        val targetController = findRelativeFile(topSrcDir.psiDirectory, controllerPath)
            ?: return
        FileEditorManager.getInstance(project).openFile(targetController, true)
    }


    private fun getPsiFile(project: Project, e: AnActionEvent): PsiFile? {
        val virtualFile = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile)
    }
}
