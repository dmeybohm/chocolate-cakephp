package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.PhpFilesModificationTracker
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.isAnyControllerClass
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
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
        return cache.getOrPut(viewFile) { Search(project).find(viewFile) }
    }

    private class Search(private val project: Project) {
        private val settings = Settings.getInstance(project)
        private val phpIndex = PhpIndex.getInstance(project)
        private val resolution = ViewFileResolution(project, settings)

        /**
         * A file the walk has reached, with the files it passed through by unprefixed names, which
         * resolve in the plugin of the rendering controller.
         */
        private data class Step(val viewFile: ViewFile, val pending: Set<ViewFile>)

        // An element renders in the including view, so walk back through the files that include it:
        fun find(viewFile: ViewFile): List<String> {
            val controllers = linkedSetOf<String>()
            val start = Step(viewFile, emptySet())
            val visited = mutableSetOf(start)
            val queue = ArrayDeque(listOf(start))
            while (queue.isNotEmpty() && visited.size <= MAX_INCLUDING_FILES) {
                ProgressManager.checkCanceled()
                val (current, pending) = queue.removeFirst()
                // Any controller may render a layout, so whatever a layout includes gets them all:
                if (current.structure.isLayoutKey(current.templateKey)) {
                    allControllers(current.structure)
                        .filter { pendingResolve(pending, pluginOf(current, it)) }
                        .mapTo(controllers) { it.fqn }
                    continue
                }
                for (reference in ViewFileIndexService.referencingElementsInSmartReadAction(project, current.templateKey)) {
                    val element = reference.psiElement ?: continue
                    val phpClass = PsiTreeUtil.getParentOfType(element, PhpClass::class.java, false)
                    if (phpClass != null) {
                        val plugin = pluginOf(current, phpClass)
                        if (phpClass.fqn.isAnyControllerClass() &&
                            resolution.resolvesTo(reference.pluginPrefix ?: plugin, current) &&
                            pendingResolve(pending, plugin)
                        ) {
                            controllers += phpClass.fqn
                        }
                        continue
                    }
                    val includingFile = element.containingFile?.virtualFile ?: continue
                    val including = viewFileOf(project, settings, includingFile) ?: continue
                    val prefix = reference.pluginPrefix
                    if (prefix != null && !resolution.resolvesTo(prefix, current)) {
                        continue
                    }
                    val next = Step(including, if (prefix == null) pending + current else pending)
                    if (visited.add(next)) {
                        queue.add(next)
                    }
                }
            }
            return controllers.toList()
        }

        private fun pendingResolve(pending: Set<ViewFile>, plugin: String?): Boolean =
            pending.all { resolution.resolvesTo(plugin, it) }

        private fun pluginOf(viewFile: ViewFile, phpClass: PhpClass): String? =
            phpClass.containingFile?.virtualFile?.let { resolution.pluginOf(viewFile.structure, it) }

        private fun allControllers(structure: TemplateDirStructure): List<PhpClass> {
            val framework = structure.frameworkControllerClass?.let { phpIndex.getAllSubclasses(it) }.orEmpty()
            val app = structure.appControllerClass?.let {
                phpIndex.getClassesByFQN(it) + phpIndex.getAllSubclasses(it)
            }.orEmpty()
            return (framework + app).distinctBy { it.fqn }
        }
    }
}
