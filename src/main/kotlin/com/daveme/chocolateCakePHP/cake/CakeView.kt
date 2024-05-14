package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.pathRelativeToProject
import com.daveme.chocolateCakePHP.virtualFilesToPsiFiles
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

fun viewFilesFromControllerAction(
    project: Project,
    templatesDirectory: TemplatesDir,
    settings: Settings,
    controllerName: String,
    actionNames: ActionNames
): Collection<PsiFile> {
    val fileExtensions = settings.dataViewExtensions

    // Create one file for each of the file extensions that match the naming convention:
    val files = actionNames.allActionNames.map { controllerAction ->
        listOfNotNull(
            templatePathToVirtualFile(
                settings,
                templatesDirectory,
                controllerName,
                controllerAction.path
            )
        ) + fileExtensions.mapNotNull { fileExtension ->
            templatePathToVirtualFile(
                settings,
                templatesDirectory,
                controllerName,
                "${controllerAction.pathPrefix}${fileExtension}/${controllerAction.name}"
            )
        }
    }.flatMap { it }

    return virtualFilesToPsiFiles(project, files)
}

data class ViewPath(
    val label: String,
    val templatePath: String,
    val prefix: String,
    val relativePath: String,
    val altLabel: String = "",
) {
    val fullPath: String
        get() = "${templatePath}/${prefix}${relativePath}"
}

fun viewPathFromControllerNameAndActionName(
    templatesDir: TemplatesDir,
    settings: Settings,
    templatePath: String,
    label: String,
    controllerName: String,
    actionName: ActionName,
    convertCase: Boolean,
    altLabel: String = ""
): ViewPath {
    if (actionName.isAbsolute) {
        return ViewPath(
            templatePath = templatePath,
            prefix = "",
            label = label,
            altLabel = altLabel,
            relativePath = actionName.getViewFilename(templatesDir, settings, convertCase)
        )
    } else {
        return ViewPath(
            templatePath = templatePath,
            prefix = "${controllerName}/",
            label = label,
            altLabel = altLabel,
            relativePath = actionName.getViewFilename(templatesDir, settings, convertCase)
        )
    }
}

data class AllViewPaths(
    val defaultViewPath: ViewPath,
    val otherViewPaths: List<ViewPath>,
    val dataViewPaths: List<ViewPath>,
)

fun allViewPathsFromController(
    project: Project,
    controllerName: String,
    templatesDirectory: TemplatesDir,
    settings: Settings,
    actionNames: ActionNames,
): AllViewPaths? {
    val templatePath = pathRelativeToProject(project, templatesDirectory.psiDirectory)
        ?: return null
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
            templatesDir = templatesDirectory,
            settings = settings,
            templatePath = templatePath,
            label = it.uppercase(),
            controllerName = controllerName,
            actionName = actionName,
            convertCase = true
        )
    }
    val otherViewPaths = actionNames.otherActionNames.map { actionName ->
        viewPathFromControllerNameAndActionName(
            templatesDir = templatesDirectory,
            settings = settings,
            templatePath = templatePath,
            label = actionName.path,
            controllerName = controllerName,
            actionName = actionName,
            convertCase = false
        )
    }
    return AllViewPaths(
        defaultViewPath = viewPathFromControllerNameAndActionName(
            templatesDir = templatesDirectory,
            settings = settings,
            templatePath = templatePath,
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