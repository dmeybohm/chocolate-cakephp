package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.PhpFilesModificationTracker
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.isAnyControllerClass
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Key
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.util.concurrent.ConcurrentHashMap

/**
 * The controllers that render a view file.
 */
object RenderingControllers {

    private const val MAX_INCLUDING_FILES = 200
    private const val MAX_CHAIN_LENGTH = 20

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
        private val projectDir = project.guessProjectDir()

        // An element renders in the including view, so walk back through the files that include it:
        fun find(viewFile: ViewFile): List<String> {
            val controllers = linkedSetOf<String>()
            val visited = mutableSetOf(viewFile)
            val queue = ArrayDeque(listOf(viewFile))
            while (queue.isNotEmpty() && visited.size <= MAX_INCLUDING_FILES) {
                ProgressManager.checkCanceled()
                val current = queue.removeFirst()
                val isLayout = current.structure.isLayoutKey(current.templateKey)
                for (reference in ViewFileIndexService.referencingElementsInSmartReadAction(project, current.templateKey)) {
                    val element = reference.psiElement ?: continue
                    val phpClass = PsiTreeUtil.getParentOfType(element, PhpClass::class.java, false)
                    if (phpClass != null) {
                        if (!phpClass.fqn.isAnyControllerClass()) {
                            continue
                        }
                        // A layout a class names is the layout of its subclasses too:
                        val candidates = if (isLayout) layoutControllers(current, phpClass, element is Field) else listOf(phpClass)
                        for (controller in candidates) {
                            val plugin = reference.pluginPrefix ?: pluginOf(current, controller)
                            if (resolvesTo(plugin, current)) {
                                controllers += controller.fqn
                            }
                        }
                        continue
                    }
                    val includingFile = element.containingFile?.virtualFile ?: continue
                    val including = ViewHelperReceivers.viewFileOf(project, settings, includingFile) ?: continue
                    if (resolvesTo(reference.pluginPrefix ?: including.plugin, current) && visited.add(including)) {
                        queue.add(including)
                    }
                }
                if (current.templateKey == current.structure.defaultLayoutKey) {
                    controllersWithDefaultLayout(current)
                        .filter { resolvesTo(pluginOf(current, it), current) }
                        .mapTo(controllers) { it.fqn }
                }
            }
            return controllers.toList()
        }

        private fun pluginOf(viewFile: ViewFile, phpClass: PhpClass): String? =
            phpClass.containingFile?.virtualFile?.let { viewFile.structure.pluginOf(it, projectDir, settings) }

        // An assignment applies to every subclass, a declaration to those that do not redeclare it:
        private fun layoutControllers(viewFile: ViewFile, phpClass: PhpClass, isDeclaration: Boolean): List<PhpClass> {
            val subclasses = phpIndex.getAllSubclasses(phpClass.fqn).filter {
                it.fqn.isAnyControllerClass() &&
                        (!isDeclaration || nearestLayoutDeclaration(it)?.fqn == phpClass.fqn)
            }
            return (listOf(phpClass) + subclasses).filter { rendersPages(viewFile, it) }
        }

        // A controller others extend is their base unless it renders pages of its own, and an
        // abstract one is never dispatched:
        private fun rendersPages(viewFile: ViewFile, phpClass: PhpClass): Boolean =
            !phpClass.isAbstract && (
                    phpIndex.getDirectSubclasses(phpClass.fqn).none { it.fqn.isAnyControllerClass() } ||
                            rendersOwnPages(viewFile, phpClass)
                    )

        // A template for one of its own actions, or a render() call of its own, rather than any
        // public method, so adding a helper method to a base class changes nothing:
        private fun rendersOwnPages(viewFile: ViewFile, phpClass: PhpClass): Boolean {
            val actions = phpClass.ownMethods.filter { it.access.isPublic && !it.name.startsWith("_") }
            if (actions.any { callsRender(it) }) {
                return true
            }
            val controllerName = phpClass.name.removeSuffix("Controller")
            val plugin = pluginOf(viewFile, phpClass)
            return actions.any { viewFileExists(ViewFile(viewFile.structure, "$controllerName/${it.name}", plugin)) }
        }

        private fun callsRender(method: Method): Boolean =
            PsiTreeUtil.findChildrenOfType(method, MethodReference::class.java).any {
                it.name.equals("render", ignoreCase = true) && it.classReference?.textMatches("\$this") == true
            }

        // Controllers whose chain declares the default layout, or none (as when the framework
        // class that declares it is not indexed):
        private fun controllersWithDefaultLayout(viewFile: ViewFile): List<PhpClass> {
            val appController = viewFile.structure.appControllerClass ?: return emptyList()
            return (phpIndex.getClassesByFQN(appController) + phpIndex.getAllSubclasses(appController))
                .filter { rendersPages(viewFile, it) }
                .filter { controller ->
                    val declaration = nearestLayoutDeclaration(controller) ?: return@filter true
                    val layout = declaration.findOwnFieldByName("layout", false)?.defaultValue
                    (layout as? StringLiteralExpression)?.contents == "default"
                }
        }

        private fun nearestLayoutDeclaration(phpClass: PhpClass): PhpClass? {
            var current: PhpClass? = phpClass
            var depth = 0
            while (current != null && depth++ < MAX_CHAIN_LENGTH) {
                if (current.findOwnFieldByName("layout", false) != null) {
                    return current
                }
                current = current.superClass
            }
            return null
        }

        // A name resolving in a plugin finds the plugin's file first, and the app's otherwise:
        private fun resolvesTo(referencePlugin: String?, viewFile: ViewFile): Boolean =
            when {
                viewFile.plugin != null -> referencePlugin.equals(viewFile.plugin, ignoreCase = true)
                referencePlugin == null -> true
                else -> !viewFileExists(viewFile.copy(plugin = referencePlugin))
            }

        private fun viewFileExists(viewFile: ViewFile): Boolean {
            val name = viewFile.templateKey.substringAfterLast('/')
            val extensions = listOf(settings.cake2TemplateExtension, settings.cakeTemplateExtension, "php").distinct()
            return extensions.any { extension ->
                FilenameIndex.getVirtualFilesByName("$name.$extension", GlobalSearchScope.projectScope(project)).any {
                    ViewHelperReceivers.viewFileOf(project, settings, it) == viewFile
                }
            }
        }
    }
}
