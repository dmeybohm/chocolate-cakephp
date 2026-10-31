package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.controllerPathFromControllerFile
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.isAnyControllerClass
import com.daveme.chocolateCakePHP.view.TemplateDirStructure
import com.daveme.chocolateCakePHP.view.ViewFile
import com.daveme.chocolateCakePHP.view.ViewFileResolution
import com.daveme.chocolateCakePHP.view.viewFileOf
import com.daveme.chocolateCakePHP.view.viewfileindex.PsiElementAndPath
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method

/**
 * A bounded walk back through the view file index from one view file to the classes that render it.
 *
 * An unprefixed name in a template or element resolves in the plugin of the rendering class, so a
 * hop through one can only be decided once the walk reaches the class. Whatever that hop
 * contributes waits until then. A file nothing renders decides by its own plugin.
 */
internal class ContributingSourceWalk(
    private val project: Project,
    private val settings: Settings,
    private val root: ViewFile,
) {
    /**
     * A file reached by the walk, with the files it passed through by unprefixed names.
     */
    private data class Node(val viewFile: ViewFile, val pending: Set<ViewFile>)

    private class Edge(val to: Node, val from: Node, val elementData: ViewVariableSource?, val viewSet: ViewVariableSource?)

    private class Step(val reference: PsiElementAndPath, val node: Node)

    private val resolution = ViewFileResolution(project, settings)
    private val rootNode = Node(root, emptySet())
    private val edges = mutableListOf<Edge>()
    private val actions = linkedSetOf<ViewVariableSource>()
    private val accepted = mutableSetOf<Node>()
    private val acceptedPastBudget = mutableSetOf<Node>()

    /**
     * The sources of [root], with each reference visited once per node and each node expanded
     * once, so the lookup budget does not multiply with repeated render calls. The data of a
     * direct call doesn't count against the budget.
     */
    fun sources(): List<ViewVariableSource> {
        val queue = ArrayDeque<Step>()
        val nodes = mutableSetOf(rootNode)
        val references = HashMap<String, List<PsiElementAndPath>>()
        fun referencesTo(viewFile: ViewFile) = references.getOrPut(viewFile.templateKey) {
            // A cell template renders in its own view, so its callers' variables do not reach it
            ViewFileIndexService.referencingElementsInSmartReadAction(project, viewFile.templateKey)
                .filterNot { it.isCellRender }
        }
        referencesTo(root).mapTo(queue) { Step(it, rootNode) }

        var lookups = MAX_LOOKUPS
        while (queue.isNotEmpty() && lookups > 0) {
            lookups -= 1
            val step = queue.removeFirst()
            if (isClass(step.reference)) {
                visitClass(step)
                continue
            }
            val next = addEdge(step, inBudget = true) ?: continue
            if (nodes.add(next)) {
                val nextReferences = referencesTo(next.viewFile)
                if (nextReferences.isEmpty()) {
                    decide(next, next.viewFile.plugin, accepted)
                }
                nextReferences.mapTo(queue) { Step(it, next) }
            }
        }
        queue.forEach { step ->
            if (isClass(step.reference)) {
                val plugin = resolution.pluginOf(root.structure, step.reference.file)
                if (classReaches(step, plugin)) {
                    acceptedPastBudget += step.node
                }
            } else {
                addEdge(step, inBudget = false)?.let { decide(it, it.viewFile.plugin, acceptedPastBudget) }
            }
        }
        return collectSources()
    }

    private fun isClass(reference: PsiElementAndPath): Boolean =
        reference.nameWithoutExtension.isAnyControllerClass() ||
            (settings.cake3Enabled && reference.cellName != null)

    private fun visitClass(step: Step) {
        val plugin = resolution.pluginOf(root.structure, step.reference.file)
        if (!classReaches(step, plugin)) {
            return
        }
        accepted += step.node
        actionKey(step.reference)?.let { actions += ViewVariableSource.ControllerAction(it) }
    }

    private fun classReaches(step: Step, plugin: String?): Boolean =
        resolution.resolvesTo(step.reference.pluginPrefix ?: plugin, step.node.viewFile) &&
            pendingResolve(step.node, plugin)

    private fun actionKey(reference: PsiElementAndPath): ViewVariablesKey? {
        val psiElement = reference.psiElement ?: return null
        val method = psiElement as? Method ?: PsiTreeUtil.getParentOfType(psiElement, Method::class.java)
        if (method == null || !method.isValid) {
            return null
        }
        val plugin = TemplateDirStructure.pluginOfClassFile(project, settings, reference.file)
        val cellName = reference.cellName
        return if (!reference.nameWithoutExtension.isAnyControllerClass() && cellName != null) {
            cellActionKey(cellName, method.name, plugin)
        } else {
            val controllerPath = controllerPathFromControllerFile(reference.file) ?: return null
            controllerMethodKey(controllerPath, method.name, plugin)
        }
    }

    /**
     * Records the hop [step] follows through a view file, and returns the node it leads to, unless
     * its prefix already rules it out.
     */
    private fun addEdge(step: Step, inBudget: Boolean): Node? {
        val reference = step.reference
        val current = step.node
        val including = viewFileOf(project, settings, reference.file) ?: return null
        val prefix = reference.pluginPrefix
        if (prefix != null && !resolution.resolvesTo(prefix, current.viewFile)) {
            return null
        }
        val next = Node(including, if (prefix == null) current.pending + current.viewFile else current.pending)
        val elementData = if (current == rootNode)
            ViewVariableSource.ElementCallData(elementDataKey(root.templateKey), reference.file, reference.offset)
        else
            null
        edges += Edge(next, current, elementData, if (inBudget) viewSetOf(reference, including) else null)
        return next
    }

    // The view-set key stays literal, as the source is only ever read from its own file:
    private fun viewSetOf(reference: PsiElementAndPath, including: ViewFile): ViewVariableSource? {
        if (including == root) {
            return null
        }
        val templatesDir = templatesDirectoryOfViewFile(project, settings, reference.file) ?: return null
        val key = ViewFileIndexService.canonicalizeFilenameToKey(templatesDir, settings, reference.path)
        return ViewVariableSource.ViewSet(viewSetKey(key), reference.file, reference.offset)
    }

    private fun decide(node: Node, plugin: String?, decided: MutableSet<Node>) {
        if (pendingResolve(node, plugin)) {
            decided += node
        }
    }

    private fun pendingResolve(node: Node, plugin: String?): Boolean =
        node.pending.all { resolution.resolvesTo(plugin, it) }

    private fun collectSources(): List<ViewVariableSource> {
        propagate(accepted)
        acceptedPastBudget += accepted
        propagate(acceptedPastBudget)
        val result = linkedSetOf<ViewVariableSource>()
        edges.filter { it.to in acceptedPastBudget }.mapNotNullTo(result) { it.elementData }
        edges.filter { it.to in accepted }.mapNotNullTo(result) { it.viewSet }
        result += actions
        return result.toList()
    }

    // A node leading to an accepted node is reached along that path, so it is accepted too:
    private fun propagate(nodes: MutableSet<Node>) {
        do {
            val added = edges.filter { it.to in nodes }.map { it.from }
        } while (nodes.addAll(added))
    }

    private companion object {
        const val MAX_LOOKUPS = 15
    }
}
