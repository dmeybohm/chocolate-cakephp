package com.daveme.chocolateCakePHP.view.viewfileindex

import com.daveme.chocolateCakePHP.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import java.io.File

val VIEW_FILE_INDEX_KEY : ID<String, List<Int>> =
    ID.create("com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndex")

data class PsiElementAndPath(
    val path: String,
    val psiElement: PsiElement
) {
    val nameWithoutExtension: String = File(path).nameWithoutExtension
}

data class ViewPathPrefix(
    val prefix: String
)

data class RenderPath(
    val path: String,
) {
    val isAbsolute: Boolean get() = path.startsWith("/")
}

data class ControllerInfo(
    val virtualFile: VirtualFile,
    val isCakeTwo: Boolean
)

fun lookupControllerFileInfo(controllerFile: VirtualFile): ControllerInfo {
    return ControllerInfo(
        controllerFile,
        !isCakeThreePlusController(controllerFile)
    )
}

private fun isCakeThreePlusController(
    controllerFile: VirtualFile
): Boolean {
    val topSourceDir = controllerFile.parent?.parent  ?: return false
    val projectDir = topSourceDir.parent ?: return false

    return projectDir.children.any { it.nameWithoutExtension == "templates"} ||
            topSourceDir.children.any { it.nameWithoutExtension == "Template" }
}

object ViewFileIndexService {
    fun canonicalizeFilenameToKey(filename: String, settings: Settings): String {
        return filename
            .removeFromEnd(settings.cakeTemplateExtension, ignoreCase = true)
            .removeFromEnd(".php", ignoreCase = true)
    }

    fun referencingElements(project: Project, filenameKey: String): List<PsiElementAndPath> {
        val result = mutableListOf<PsiElementAndPath>()
        val fileIndex = FileBasedIndex.getInstance()
        val searchScope = GlobalSearchScope.allScope(project)

        fileIndex.processValues(VIEW_FILE_INDEX_KEY, filenameKey, null,
            { indexedFile, offsets: List<Int>  ->
                offsets.forEach { offset ->
                    val element = indexedFile.findElementAt(project, offset)

                    val methodOrReference = PsiTreeUtil.getParentOfType(element, MethodReference::class.java, false)
                        ?: PsiTreeUtil.getParentOfType(element, Method::class.java)
                        ?: return@forEach
                    result.add(PsiElementAndPath(indexedFile.path, methodOrReference))
                }
                true
            }, searchScope)
        return result
    }

    fun controllerElements(project: Project, filenameKey: String): List<PsiElementAndPath> {
        val elements = referencingElements(project, filenameKey).toMutableList()
        val resultElements = mutableListOf<PsiElementAndPath>()
        // todo
        return resultElements
    }

}

private fun isControllerFile(file: VirtualFile): Boolean {
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

fun fullExplicitViewPath(
    viewPathPrefix: ViewPathPrefix,
    renderPath: RenderPath
): String {
    return if (renderPath.isAbsolute)
        renderPath.path.substring(1)
    else
        "${viewPathPrefix.prefix}${renderPath.path}"
}

fun fullImplicitViewPath(
    viewPathPrefix: ViewPathPrefix,
    controllerInfo: ControllerInfo,
    methodName: String
): String {
    val viewPath = methodName.conditionalCamelCaseToUnderscore(!controllerInfo.isCakeTwo)
    return "${viewPathPrefix.prefix}${viewPath}"
}