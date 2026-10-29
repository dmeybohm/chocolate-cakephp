package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.PhpFilesModificationTracker
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.isAnyControllerClass
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.PhpClass
import java.util.concurrent.ConcurrentHashMap

/**
 * The controllers that render a view file.
 */
object RenderingControllers {

    private const val MAX_INCLUDING_FILES = 200

    private val CACHE_KEY = Key.create<CachedValue<ConcurrentHashMap<ViewFile, List<String>>>>(
        "com.daveme.chocolateCakePHP.view.RenderingControllers"
    )

    /**
     * The controllers that render [viewFile], directly or through the files that include it.
     */
    fun of(project: Project, viewFile: ViewFile): List<String> {
        val tracker = project.getService(PhpFilesModificationTracker::class.java)
        tracker.ensureArmed()
        val cache = CachedValuesManager.getManager(project).getCachedValue(
            project,
            CACHE_KEY,
            { CachedValueProvider.Result.create(ConcurrentHashMap<ViewFile, List<String>>(), tracker) },
            false
        )
        return cache.getOrPut(viewFile) { find(project, viewFile) }
    }

    // An element renders in the including view, so walk back through the files that include it:
    private fun find(project: Project, viewFile: ViewFile): List<String> {
        val settings = Settings.getInstance(project)
        val controllers = linkedSetOf<String>()
        val visited = mutableSetOf(viewFile)
        val queue = ArrayDeque(listOf(viewFile))
        while (queue.isNotEmpty() && visited.size <= MAX_INCLUDING_FILES) {
            ProgressManager.checkCanceled()
            val current = queue.removeFirst()
            for (reference in ViewFileIndexService.referencingElementsInSmartReadAction(project, current.templateKey)) {
                if (!resolvesTo(project, settings, reference.plugin, current)) {
                    continue
                }
                val element = reference.psiElement ?: continue
                val phpClass = PsiTreeUtil.getParentOfType(element, PhpClass::class.java, false)
                if (phpClass != null) {
                    if (phpClass.fqn.isAnyControllerClass()) {
                        controllers += phpClass.fqn
                    }
                    continue
                }
                val includingFile = element.containingFile?.virtualFile ?: continue
                val including = ViewHelperReceivers.viewFileOf(project, settings, includingFile) ?: continue
                if (visited.add(including)) {
                    queue.add(including)
                }
            }
        }
        return controllers.toList()
    }

    // A name resolving in a plugin finds the plugin's file first, and the app's otherwise:
    private fun resolvesTo(project: Project, settings: Settings, referencePlugin: String?, viewFile: ViewFile): Boolean =
        when {
            viewFile.plugin != null -> referencePlugin.equals(viewFile.plugin, ignoreCase = true)
            referencePlugin == null -> true
            else -> !pluginHasViewFile(project, settings, viewFile.copy(plugin = referencePlugin))
        }

    private fun pluginHasViewFile(project: Project, settings: Settings, pluginFile: ViewFile): Boolean {
        val name = pluginFile.templateKey.substringAfterLast('/')
        val extensions = listOf(settings.cake2TemplateExtension, settings.cakeTemplateExtension, "php").distinct()
        return extensions.any { extension ->
            FilenameIndex.getVirtualFilesByName("$name.$extension", GlobalSearchScope.projectScope(project)).any {
                ViewHelperReceivers.viewFileOf(project, settings, it) == pluginFile
            }
        }
    }
}
