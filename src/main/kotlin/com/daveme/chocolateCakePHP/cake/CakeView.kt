package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

data class ControllerPath(
    val name: String,
    val prefix: String
) {
    val viewPath: String get() =
        if (prefix.isEmpty())
            "${name}/"
        else
            "${prefix}/${name}/"
}

fun controllerPathFromControllerFile(controllerFile: VirtualFile): ControllerPath? {
    val baseName = controllerFile.nameWithoutExtension.controllerBaseName()
    if (baseName == null) {
        return null
    }
    var parent : VirtualFile? = controllerFile.parent
    val prefix = mutableListOf<String>()
    while (parent != null && parent.name != "Controller") {
        prefix.add(parent.name)
        parent = parent.parent
    }
    return ControllerPath(
        prefix = prefix.reversed().joinToString("/"),
        name = baseName
    )
}

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
    controllerPath: ControllerPath,
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
            prefix = controllerPath.viewPath,
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
    controllerPath: ControllerPath,
    templatesDirWithPath: TemplatesDirWithPath,
    settings: Settings,
    actionNames: ActionNames,
): AllViewPaths {
    val dataViewPaths = settings.dataViewExtensions.map {
        val dataViewPrefix = if (actionNames.defaultActionName.isAbsolute)
            actionNames.defaultActionName.pathPrefix
        else
            "/${controllerPath.viewPath}"
        val actionName = ActionName(
            pathPrefix = "${dataViewPrefix}${it}/",
            name = actionNames.defaultActionName.name,
        )
        viewPathFromControllerNameAndActionName(
            templatesDirWithPath = templatesDirWithPath,
            settings = settings,
            label = it.uppercase(),
            controllerPath = controllerPath,
            actionName = actionName,
            convertCase = true
        )
    }
    val otherViewPaths = actionNames.otherActionNames.map { actionName ->
        viewPathFromControllerNameAndActionName(
            templatesDirWithPath = templatesDirWithPath,
            settings = settings,
            label = actionName.path,
            controllerPath = controllerPath,
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
            controllerPath = controllerPath,
            actionName = actionNames.defaultActionName,
            convertCase = true
        ),
        otherViewPaths = otherViewPaths,
        dataViewPaths = dataViewPaths
    )
}