package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.PhpFilesModificationTracker
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.CakeTwoTemplatesDir
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.isAnyControllerClass
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.PhpClass
import java.util.concurrent.ConcurrentHashMap

/**
 * The controllers that render a CakePHP 2 view file, whose helpers the view has.
 */
object CakeTwoRenderingControllers {

    private const val APP_CONTROLLER = "\\AppController"
    private const val MAX_INCLUDING_FILES = 200

    private val CACHE_KEY = Key.create<CachedValue<ConcurrentHashMap<String, List<String>>>>(
        "com.daveme.chocolateCakePHP.view.CakeTwoRenderingControllers"
    )

    /**
     * The template key of [file] when it is a CakePHP 2 view file.
     */
    fun templateKeyOfViewFile(project: Project, settings: Settings, file: VirtualFile): String? {
        val templatesDir = templatesDirectoryOfViewFile(project, settings, file) as? CakeTwoTemplatesDir
            ?: return null
        val relativePath = VfsUtil.getRelativePath(file, templatesDir.directory) ?: return null
        return ViewFileIndexService.canonicalizeFilenameToKey(templatesDir, settings, relativePath)
    }

    /**
     * The helper receivers of the view file with [templateKey]: the controllers that render it,
     * or `AppController` when no controller is found.
     */
    fun helperReceivers(project: Project, templateKey: String): List<String> =
        renderingControllers(project, templateKey).ifEmpty { listOf(APP_CONTROLLER) }

    /**
     * The helper receivers of [file] when it is a CakePHP 2 view file.
     */
    fun helperReceiversOfFile(project: Project, settings: Settings, file: PsiFile): List<String>? {
        val virtualFile = file.originalFile.virtualFile ?: return null
        val templateKey = templateKeyOfViewFile(project, settings, virtualFile) ?: return null
        return helperReceivers(project, templateKey)
    }

    private fun renderingControllers(project: Project, templateKey: String): List<String> {
        val tracker = project.getService(PhpFilesModificationTracker::class.java)
        tracker.ensureArmed()
        val cache = CachedValuesManager.getManager(project).getCachedValue(
            project,
            CACHE_KEY,
            { CachedValueProvider.Result.create(ConcurrentHashMap<String, List<String>>(), tracker) },
            false
        )
        return cache.getOrPut(templateKey) { findRenderingControllers(project, templateKey) }
    }

    // An element renders in the including view, so walk back through the files that include it:
    private fun findRenderingControllers(project: Project, templateKey: String): List<String> {
        val settings = Settings.getInstance(project)
        val controllers = linkedSetOf<String>()
        val visited = mutableSetOf(templateKey)
        val queue = ArrayDeque(listOf(templateKey))
        while (queue.isNotEmpty() && visited.size <= MAX_INCLUDING_FILES) {
            ProgressManager.checkCanceled()
            val key = queue.removeFirst()
            for (reference in ViewFileIndexService.referencingElementsInSmartReadAction(project, key)) {
                val element = reference.psiElement ?: continue
                val phpClass = PsiTreeUtil.getParentOfType(element, PhpClass::class.java, false)
                if (phpClass != null) {
                    if (phpClass.fqn.isAnyControllerClass()) {
                        controllers += phpClass.fqn
                    }
                    continue
                }
                val includingFile = element.containingFile?.virtualFile ?: continue
                val includingKey = templateKeyOfViewFile(project, settings, includingFile) ?: continue
                if (visited.add(includingKey)) {
                    queue.add(includingKey)
                }
            }
        }
        return controllers.toList()
    }
}
