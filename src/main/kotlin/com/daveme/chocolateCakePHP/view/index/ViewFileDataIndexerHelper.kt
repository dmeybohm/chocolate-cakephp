package com.daveme.chocolateCakePHP.view.index

import com.daveme.chocolateCakePHP.controllerBaseName
import com.daveme.chocolateCakePHP.removeQuotes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.ID

val VIEW_FILE_INDEX_KEY : ID<String, List<Int>> =
    ID.create("com.daveme.chocolateCakePHP.view.index.ViewFileIndex")

data class ViewPathPrefix(
    val prefix: String
)

data class RenderPath(
    val renderPath: String,
) {
    val isAbsolute: Boolean get() = quotesRemoved.startsWith("/")
    val quotesRemoved : String get() =
        renderPath.removeQuotes()
}

fun isControllerFile(file: VirtualFile): Boolean {
    return file.nameWithoutExtension.endsWith("Controller")
}

private fun isTemplateDir(currentDir: VirtualFile): Boolean {
    return currentDir.name == "templates" ||
            currentDir.name == "Template" ||
            currentDir.name == "View"
}

private fun findElementDir(currentDir: VirtualFile): VirtualFile? {
    return currentDir.findChild("element")  ?:
            currentDir.findChild("Element")
}

fun viewPathPrefixFromSourceFile(
    projectDir: VirtualFile,
    sourceFile: VirtualFile,
): ViewPathPrefix? {
    // For render() calls inside a controller, we want to append the implicit
    // controller path if the path is not absolute, and otherwise use the render
    // path directly if it is.
    if (isControllerFile(sourceFile)) {
        val controllerBaseName = sourceFile.nameWithoutExtension.controllerBaseName()
        return ViewPathPrefix("${controllerBaseName}/")
    }

    val containingDir = sourceFile.parent ?: return null
    return viewPathToTemplatesDirRoot(projectDir, containingDir)
}

fun viewPathToTemplatesDirRoot(
    projectDir: VirtualFile,
    sourceFile: VirtualFile
): ViewPathPrefix? {
    val paths = mutableListOf<String>()
    var currentDir: VirtualFile? = sourceFile
    var foundTemplatesDir = false
    while (
        currentDir != null &&
        sourceFile != projectDir
    ) {
        val found = isTemplateDir(currentDir)
        if (found) {
            foundTemplatesDir = true
            break
        }
        paths.add(currentDir.name)
        currentDir = currentDir.parent
    }
    if (foundTemplatesDir)
        return ViewPathPrefix(paths.joinToString(separator = "/") + "/")
    else
        return null
}

fun elementPathPrefixFromSourceFile(
    projectDir: VirtualFile,
    sourceFile: VirtualFile,
): ViewPathPrefix? {
    var currentDir: VirtualFile? = sourceFile
    var elementPath : VirtualFile? = null
    while (
        currentDir != null &&
        sourceFile != projectDir
    ) {
        val found = isTemplateDir(currentDir)
        if (found) {
            elementPath = findElementDir(currentDir)
            break
        }
        currentDir = currentDir.parent
    }
    if (elementPath != null)
        return ViewPathPrefix(elementPath.name + "/")
    else
        return null
}

fun fullViewPathFromPrefixAndRenderPath(
    viewPathPrefix: ViewPathPrefix,
    renderPath: RenderPath
): String {
    return if (renderPath.isAbsolute)
        renderPath.quotesRemoved.substring(1)
    else
        "${viewPathPrefix.prefix}${renderPath.quotesRemoved}"
}
