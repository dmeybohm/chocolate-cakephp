package com.daveme.chocolateCakePHP.view.viewfileindex

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import java.io.File

val VIEW_FILE_INDEX_KEY : ID<String, List<ViewReferenceData>> =
    ID.create("com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndex")

data class ViewReferenceData(
    val methodName: String,
    val elementType: String, // "Method" or "MethodReference" 
    val offset: Int
)

data class PsiElementAndPath(
    val path: String,
    val elementPointer: SmartPsiElementPointer<PsiElement>
) {
    val nameWithoutExtension: String by lazy { File(path).nameWithoutExtension }
    val controllerPath: ControllerPath? by lazy {  controllerPathFromPsiElementAndPath() }
    val psiElement: PsiElement? get() = elementPointer.element

    private fun controllerPathFromPsiElementAndPath(): ControllerPath? {
        val parts = path.split("/")
        if (parts.size < 2) {
            return null
        }
        return if ("Controller" in parts) {
            ControllerPath(
                prefix = parts.reversed()
                    .drop(1)
                    .takeWhile { it != "Controller" }
                    .joinToString("/"),
                name = nameWithoutExtension.controllerBaseName() ?: return null
            )
        } else {
            null
        }
    }
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

fun lookupControllerFileInfo(controllerFile: VirtualFile, settings: Settings): ControllerInfo {
    return ControllerInfo(
        controllerFile,
        isCakeTwoController(controllerFile, settings)
    )
}

private fun isCakeTwoController(
    controllerFile: VirtualFile,
    settings: Settings
): Boolean {
    var controllerDir: VirtualFile? = controllerFile.parent
    while (controllerDir != null && controllerDir.name != "Controller") {
        controllerDir = controllerDir.parent
    }
    if (controllerDir == null) {
        return false
    }
    val topSourceDir = controllerDir.parent ?: return false
    val topDir = topSourceDir.parent ?: return false
    return topSourceDir.name == settings.cake2AppDirectory &&
        !topDir.children.any { it.nameWithoutExtension == "templates" } &&
            !topSourceDir.children.any { it.nameWithoutExtension == "Template" }
}

object ViewFileIndexService {
    fun canonicalizeFilenameToKey(
        templatesDirectory: TemplatesDir,
        settings: Settings,
        filename: String
    ): String {
        val extension = when (templatesDirectory) {
            is CakeTwoTemplatesDir -> ".${settings.cake2TemplateExtension}"
            is CakeThreeTemplatesDir -> ".${settings.cakeTemplateExtension}"
            is CakeFourTemplatesDir -> ".php"
        }
        var result = filename
            .removeFromStart(templatesDirectory.directory.path)
            .removeFromStart("/")
            .removeFromEnd(extension, ignoreCase = true)
        for (dataViewExtension in settings.dataViewExtensions) {
            val next = result.removeImmediateParentDir(dataViewExtension)
            if (next != result) {
                result = next
                break
            }
        }
        return result
    }

    fun referencingElements(project: Project, filenameKey: String): List<PsiElementAndPath> {
        return ReadAction.compute<List<PsiElementAndPath>, Nothing> {
            val result = mutableListOf<PsiElementAndPath>()
            val fileIndex = FileBasedIndex.getInstance()
            val searchScope = GlobalSearchScope.allScope(project)

            fileIndex.processValues(VIEW_FILE_INDEX_KEY, filenameKey, null,
                { indexedFile, referenceDataList: List<ViewReferenceData> ->
                    val psiFile = PsiManager.getInstance(project).findFile(indexedFile) ?: return@processValues true
                    val smartPointerManager = SmartPointerManager.getInstance(project)
                    
                    referenceDataList.forEach { data ->
                        val element = psiFile.findElementAt(data.offset)
                        if (element?.isValid == true) {
                            val methodOrReference = when (data.elementType) {
                                "MethodReference" -> PsiTreeUtil.getParentOfType(element, MethodReference::class.java, false)
                                "Method" -> PsiTreeUtil.getParentOfType(element, Method::class.java, false)
                                else -> null
                            }
                            if (methodOrReference != null) {
                                val pointer = smartPointerManager.createSmartPsiElementPointer(methodOrReference as PsiElement)
                                result.add(PsiElementAndPath(indexedFile.path, pointer))
                            }
                        }
                    }
                    true
                }, searchScope)
                
            result
        }
    }

}

private fun isTemplateDir(currentDir: VirtualFile): Boolean {
    return currentDir.name == "templates" ||
            currentDir.name == "Template" ||
            currentDir.name == "View"
}

private fun findElementDir(currentDir: VirtualFile): VirtualFile? {
    return currentDir.findChild("element")  ?:
            currentDir.findChild("Element") ?:
           currentDir.findChild("Elements")
}

fun viewPathPrefixFromSourceFile(
    projectDir: VirtualFile,
    sourceFile: VirtualFile,
): ViewPathPrefix? {
    // For render() calls inside a controller, we want to append the implicit
    // controller path if the path is not absolute, and otherwise use the render
    // path directly if it is.
    val controllerPath = controllerPathFromControllerFile(sourceFile)
    if (controllerPath != null) {
        return ViewPathPrefix(controllerPath.viewPath)
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
        return ViewPathPrefix(paths.reversed().joinToString(separator = "/") + "/")
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