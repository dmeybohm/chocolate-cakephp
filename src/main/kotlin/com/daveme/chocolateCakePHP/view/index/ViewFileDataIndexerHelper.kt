package com.daveme.chocolateCakePHP.view.index

import com.daveme.chocolateCakePHP.controllerBaseName
import com.daveme.chocolateCakePHP.removeQuotes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.ID

val VIEW_FILE_INDEX_KEY : ID<ViewFileLocation, Void?> =
    ID.create("com.daveme.chocolateCakePHP.view.index.ViewFileIndex")


fun isControllerFile(file: VirtualFile): Boolean {
    return file.nameWithoutExtension.endsWith("Controller")
}

fun isTemplateDir(projectDir: VirtualFile, file: VirtualFile): Boolean {
    val nameWithoutExtension = file.nameWithoutExtension
    val parent = file.parent
    return (nameWithoutExtension == "templates" && parent == projectDir) ||
            (nameWithoutExtension == "Template" && parent?.parent == projectDir) ||
            (nameWithoutExtension == "View" && parent?.parent == projectDir)
}

data class ViewPathPrefix(
    val prefix: String
)

data class RenderPath(
    val renderPath: String,
) {
    val isAbsolute: Boolean get() = renderPath.startsWith("/")
    val quotesRemoved : String get() =
        renderPath.removeQuotes()
}

fun viewPathPrefixFromSourceFile(
    projectDir: VirtualFile,
    sourceFile: VirtualFile,
): ViewPathPrefix {
    // For render() calls inside a controller, we want to append the implicit
    // controller path if the path is not absolute, and otherwise use the render
    // path directly if it is.
    if (isControllerFile(sourceFile)) {
        val controllerBaseName = sourceFile.nameWithoutExtension.controllerBaseName()
        return ViewPathPrefix("${controllerBaseName}/")
    }

    val paths = mutableListOf<String>()
    var currentDir: VirtualFile? = sourceFile
    while (
        currentDir != null &&
        sourceFile != projectDir &&
        isTemplateDir(projectDir, sourceFile)
    ) {
        paths.add(currentDir.name)
        currentDir = currentDir.parent
    }
    return ViewPathPrefix(paths.joinToString(separator = "/") + "/")
}

fun fullViewPathFromPrefixAndRenderPath(
    viewPathPrefix: ViewPathPrefix,
    renderPath: RenderPath
): String =
    if (renderPath.isAbsolute)
        renderPath.quotesRemoved.substring(1)
    else
        "${viewPathPrefix.prefix}${renderPath.quotesRemoved}"