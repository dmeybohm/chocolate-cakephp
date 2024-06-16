package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.findRelativeFile
import com.daveme.chocolateCakePHP.pathRelativeToProject
import com.daveme.chocolateCakePHP.virtualFilesToPsiFiles
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

fun viewFilesFromAllViewPaths(
    project: Project,
    templatesDirectory: TemplatesDir,
    allViewPaths: AllViewPaths,
): Collection<PsiFile> {
    val files = allViewPaths.all.mapNotNull { viewPath ->
        viewPath.toVirtualFile(templatesDirectory)
    }

    return virtualFilesToPsiFiles(project, files)
}

data class ViewPath(
    val label: String,
    val templatePath: String,
    val prefix: String,
    val relativePath: String,
    val altLabel: String = "",
) {
    val pathWithoutTemplate: String
        get() = "${prefix}${relativePath}"

    val fullPath: String
        get() = "${templatePath}/${pathWithoutTemplate}"

    fun toVirtualFile(templatesDir: TemplatesDir): VirtualFile? =
        findRelativeFile(templatesDir.psiDirectory, this.pathWithoutTemplate)
}

fun viewPathFromControllerNameAndActionName(
    templatesDirWithPath: TemplatesDirWithPath,
    settings: Settings,
    label: String,
    controllerName: String,
    actionName: ActionName,
    convertCase: Boolean,
    altLabel: String = ""
): ViewPath {
    if (actionName.isAbsolute) {
        return ViewPath(
            templatePath = templatesDirWithPath.templatesPath,
            prefix = "",
            label = label,
            altLabel = altLabel,
            relativePath = actionName.getViewFilename(templatesDirWithPath.templatesDir, settings, convertCase)
        )
    } else {
        return ViewPath(
            templatePath = templatesDirWithPath.templatesPath,
            prefix = "${controllerName}/",
            label = label,
            altLabel = altLabel,
            relativePath = actionName.getViewFilename(templatesDirWithPath.templatesDir, settings, convertCase)
        )
    }
}

data class AllViewPaths(
    val defaultViewPath: ViewPath,
    val otherViewPaths: List<ViewPath>,
    val dataViewPaths: List<ViewPath>,
) {
    val all: List<ViewPath>
        get() = listOf(defaultViewPath) + otherViewPaths + dataViewPaths
}

data class TemplatesDirWithPath(
    val templatesDir: TemplatesDir,
    val templatesPath: String
)

fun templatesDirWithPath(project: Project, templatesDir: TemplatesDir): TemplatesDirWithPath? {
    val templatePath = pathRelativeToProject(project, templatesDir.psiDirectory)
        ?: return null

    return TemplatesDirWithPath(templatesDir, templatePath)
}

fun allViewPathsFromController(
    controllerName: String,
    templatesDirWithPath: TemplatesDirWithPath,
    settings: Settings,
    actionNames: ActionNames,
): AllViewPaths {
    val dataViewPaths = settings.dataViewExtensions.map {
        val dataViewPrefix = if (actionNames.defaultActionName.isAbsolute)
            actionNames.defaultActionName.pathPrefix
        else
            "/${controllerName}/"
        val actionName = ActionName(
            pathPrefix = "${dataViewPrefix}${it}/",
            name = actionNames.defaultActionName.name,
        )
        viewPathFromControllerNameAndActionName(
            templatesDirWithPath = templatesDirWithPath,
            settings = settings,
            label = it.uppercase(),
            controllerName = controllerName,
            actionName = actionName,
            convertCase = true
        )
    }
    val otherViewPaths = actionNames.otherActionNames.map { actionName ->
        viewPathFromControllerNameAndActionName(
            templatesDirWithPath = templatesDirWithPath,
            settings = settings,
            label = actionName.path,
            controllerName = controllerName,
            actionName = actionName,
            convertCase = false
        )
    }
    return AllViewPaths(
        defaultViewPath = viewPathFromControllerNameAndActionName(
            templatesDirWithPath = templatesDirWithPath,
            settings = settings,
            label = "Default",
            altLabel = actionNames.defaultActionName.path,
            controllerName = controllerName,
            actionName = actionNames.defaultActionName,
            convertCase = true
        ),
        otherViewPaths = otherViewPaths,
        dataViewPaths = dataViewPaths
    )
}