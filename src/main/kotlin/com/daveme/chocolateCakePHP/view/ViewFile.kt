package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.view.viewfileindex.PsiElementAndPath
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * A view file, by the conventions of its structure: its template key, and the plugin it belongs
 * to, or null for the app.
 */
data class ViewFile(val structure: TemplateDirStructure, val templateKey: String, val plugin: String?) {

    /**
     * This view file as text for a type signature, its parts separated by [separator].
     */
    fun encode(separator: Char): String =
        "${structure.id}$templateKey$separator${plugin.orEmpty()}"

    companion object {
        /**
         * The view file [encode] gave [text].
         */
        fun decode(text: String, separator: Char): ViewFile? {
            val structure = text.firstOrNull()?.let { TemplateDirStructure.ofId(it) } ?: return null
            val templateKey = text.substring(1).substringBefore(separator)
            if (templateKey.isEmpty()) {
                return null
            }
            val plugin = text.substringAfter(separator, "").ifEmpty { null }
            return ViewFile(structure, templateKey, plugin)
        }
    }
}

/**
 * [file] as a view file, when it is one. A file in the app's override directory for a
 * plugin belongs to that plugin, under the key it has in the plugin.
 */
fun viewFileOf(project: Project, settings: Settings, file: VirtualFile): ViewFile? {
    val templatesDir = templatesDirectoryOfViewFile(project, settings, file) ?: return null
    val structure = TemplateDirStructure.of(templatesDir)
    val relativePath = VfsUtil.getRelativePath(file, templatesDir.directory) ?: return null
    val key = ViewFileIndexService.canonicalizeFilenameToKey(templatesDir, settings, relativePath)
    val plugin = structure.pluginOf(file, project.guessProjectDir(), settings)
        ?: return ViewFile(structure, key, null)
    return ViewFile(structure, key.removePrefix(structure.overridePrefix(plugin)), plugin)
}

/**
 * The framework's rule for whether a view name reaches a view file, remembering what it finds.
 */
class ViewFileResolution(private val project: Project, private val settings: Settings) {
    private val projectDir = project.guessProjectDir()
    private val existing = HashMap<ViewFile, Boolean>()

    /**
     * The plugin [file] belongs to, by the conventions of [structure].
     */
    fun pluginOf(structure: TemplateDirStructure, file: VirtualFile): String? =
        structure.pluginOf(file, projectDir, settings)

    /**
     * Whether a name resolving in [referencePlugin], or in the app when null, finds [viewFile]:
     * a name resolving in a plugin finds the plugin's file first, and the app's otherwise.
     */
    fun resolvesTo(referencePlugin: String?, viewFile: ViewFile): Boolean =
        when {
            viewFile.plugin != null -> referencePlugin.equals(viewFile.plugin, ignoreCase = true)
            referencePlugin == null -> true
            else -> !exists(viewFile.copy(plugin = referencePlugin))
        }

    /**
     * Whether [reference] reaches [viewFile], taken as one hop: an unprefixed name resolves in the
     * plugin of the file it is in, except that a `cell()` name resolves in the app.
     */
    fun directlyReaches(reference: PsiElementAndPath, viewFile: ViewFile): Boolean {
        val plugin = reference.pluginPrefix
            ?: if (reference.isCellRender) null else pluginOf(viewFile.structure, reference.file)
        return resolvesTo(plugin, viewFile)
    }

    private fun exists(viewFile: ViewFile): Boolean = existing.getOrPut(viewFile) {
        val name = viewFile.templateKey.substringAfterLast('/')
        val extensions = listOf(settings.cake2TemplateExtension, settings.cakeTemplateExtension, "php").distinct()
        extensions.any { extension ->
            FilenameIndex.getVirtualFilesByName("$name.$extension", GlobalSearchScope.projectScope(project)).any {
                viewFileOf(project, settings, it) == viewFile
            }
        }
    }
}

/**
 * The references that reach [viewFile], each taken as one hop.
 */
fun directReferencesTo(project: Project, settings: Settings, viewFile: ViewFile): List<PsiElementAndPath> {
    val resolution = ViewFileResolution(project, settings)
    return ViewFileIndexService.referencingElementsInSmartReadAction(project, viewFile.templateKey)
        .filter { resolution.directlyReaches(it, viewFile) }
}
