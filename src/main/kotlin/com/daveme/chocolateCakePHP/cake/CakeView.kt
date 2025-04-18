package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
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

data class TemplatesDirWithPath(
    val templatesDir: TemplatesDir,
    val templatesPath: String
)

data class AllViewPaths(
    val defaultViewPaths: List<ViewPath>,
    val otherViewPaths: List<ViewPath>,
    val dataViewPaths: List<ViewPath>,
) {
    val defaultViewPath: ViewPath =
        defaultViewPaths.first()

    val all: List<ViewPath>
        get() = defaultViewPaths + otherViewPaths + dataViewPaths
}

fun controllerPathFromControllerFile(controllerFile: VirtualFile): ControllerPath? {
    val baseName = controllerFile.nameWithoutExtension.controllerBaseName()
    if (baseName.isNullOrEmpty()) {
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
    allTemplatesPaths: AllTemplatePaths,
    allViewPaths: AllViewPaths,
): Collection<PsiFile> {
    val files = allViewPaths.all.asSequence()
        .flatMap { viewPath ->
            allTemplatesPaths.allPaths.asSequence()
                .map { templatePath -> viewPath to templatePath }
        }
        .mapNotNull { (viewPath, templatePath) ->
            viewPath.toVirtualFile(templatePath.templatesDir)
        }
        .toList()

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
        findRelativeFile(templatesDir.directory, this.pathWithoutTemplate)
}

fun viewPathsFromControllerNameAndActionName(
    templatesPaths: List<TemplatesDirWithPath>,
    settings: Settings,
    label: String,
    controllerPath: ControllerPath,
    actionName: ActionName,
    convertCase: Boolean,
    altLabel: String = ""
): List<ViewPath> {
    return templatesPaths.map { templateDirWithPath ->
        if (actionName.isAbsolute)
            ViewPath(
                templatePath = templateDirWithPath.templatesPath,
                prefix = "",
                label = label,
                altLabel = altLabel,
                relativePath = actionName.getViewFilename(templateDirWithPath.templatesDir, settings, convertCase)
            )
        else
            ViewPath(
                templatePath = templateDirWithPath.templatesPath,
                prefix = controllerPath.viewPath,
                label = label,
                altLabel = altLabel,
                relativePath = actionName.getViewFilename(templateDirWithPath.templatesDir, settings, convertCase)
            )
    }
}


fun allViewPathsFromController(
    controllerPath: ControllerPath,
    allTemplatePaths: AllTemplatePaths,
    settings: Settings,
    actionNames: ActionNames,
): AllViewPaths {
    val dataViewPaths = settings.dataViewExtensions.flatMap {
        val dataViewPrefix = if (actionNames.defaultActionName.isAbsolute)
            actionNames.defaultActionName.pathPrefix
        else
            "/${controllerPath.viewPath}"
        val actionName = ActionName(
            pathPrefix = "${dataViewPrefix}${it}/",
            name = actionNames.defaultActionName.name,
        )
        viewPathsFromControllerNameAndActionName(
            templatesPaths = allTemplatePaths.allPaths,
            settings = settings,
            label = it.uppercase(),
            controllerPath = controllerPath,
            actionName = actionName,
            convertCase = true
        )
    }
    val otherViewPaths = actionNames.otherActionNames.flatMap { actionName ->
        viewPathsFromControllerNameAndActionName(
            templatesPaths = allTemplatePaths.allPaths,
            settings = settings,
            label = actionName.path,
            controllerPath = controllerPath,
            actionName = actionName,
            convertCase = false
        )
    }
    return AllViewPaths(
        defaultViewPaths = viewPathsFromControllerNameAndActionName(
            templatesPaths = allTemplatePaths.allPaths,
            settings = settings,
            label = "Default",
            altLabel = actionNames.defaultActionName.path,
            controllerPath = controllerPath,
            actionName = actionNames.defaultActionName,
            convertCase = true,
        ),
        otherViewPaths = otherViewPaths,
        dataViewPaths = dataViewPaths,
    )
}

private fun elementViewPaths(
    allTemplatesPaths: AllTemplatePaths,
    settings: Settings,
    elementPath: String,
): List<ViewPath> {
    return allTemplatesPaths.allPaths.map { templatesDirWithPath ->
        ViewPath(
            label = elementPath,
            templatePath = templatesDirWithPath.templatesPath,
            prefix = templatesDirWithPath.templatesDir.elementDirName + "/",
            altLabel = elementPath,
            relativePath = addViewFilenameExtension(
                templatesDirectory = templatesDirWithPath.templatesDir,
                name = elementPath,
                settings = settings,
                convertCase = false,
            )
        )
    }
}

fun allViewPathsFromElementPath(
    allTemplatePaths: AllTemplatePaths,
    settings: Settings,
    elementPath: String,
): AllViewPaths {
    return AllViewPaths(
        defaultViewPaths = elementViewPaths(
            allTemplatesPaths = allTemplatePaths,
            settings = settings,
            elementPath = elementPath,
        ),
        otherViewPaths = listOf(),
        dataViewPaths = listOf()
    )
}

fun allViewPathsToFiles(
    project: Project,
    allViewPaths: AllViewPaths
): Collection<PsiFile> {
    val projectRoot: VirtualFile = project.guessProjectDir()
        ?: return setOf()
    val files = allViewPaths.all.mapNotNull { viewPath ->
        val templatePath = findRelativeFile(projectRoot, viewPath.templatePath)
            ?: return@mapNotNull null
        findRelativeFile(templatePath, viewPath.pathWithoutTemplate)
    }

    return virtualFilesToPsiFiles(project, files)
}
