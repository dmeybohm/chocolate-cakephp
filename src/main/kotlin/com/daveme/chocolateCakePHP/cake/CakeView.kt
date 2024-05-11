package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.pathRelativeToProject
import com.daveme.chocolateCakePHP.virtualFilesToPsiFiles
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.ktor.util.*

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
                controllerAction
            )
        ) + fileExtensions.mapNotNull { fileExtension ->
            templatePathToVirtualFile(
                settings,
                templatesDirectory,
                controllerName,
                "${fileExtension}/${controllerAction}"
            )
        }
    }.flatMap { it }

    return virtualFilesToPsiFiles(project, files)
}

data class DataView(
    val label: String,
    val fullPath: String
)

data class AllViewPaths(
    val defaultViewPath: String,
    val dataViewPaths: List<DataView>
)

fun allViewPathsFromController(
    project: Project,
    controllerName: String,
    templatesDirectory: TemplatesDir,
    settings: Settings,
    actionNames: ActionNames,
): AllViewPaths {
    val defaultViewPath = defaultViewPathFromController(project, controllerName, templatesDirectory)
    val viewFilename = actionNameToViewFilename(
        templatesDirectory,
        settings,
        actionNames.defaultActionName
    )
    val dataViewPaths = settings.dataViewExtensions.map {
        DataView(
            label = it.toUpperCasePreservingASCIIRules(),
            fullPath = "${defaultViewPath}${it}/${viewFilename}"
        )
    }
    return AllViewPaths(
        defaultViewPath = "${defaultViewPath}${viewFilename}",
        dataViewPaths = dataViewPaths
    )
}

fun defaultViewPathFromController(
    project: Project,
    controllerName: String,
    templatesDirectory: TemplatesDir,
): String {
    val templateFullPath = pathRelativeToProject(project, templatesDirectory.psiDirectory)
    return "${templateFullPath}/${controllerName}/"
}