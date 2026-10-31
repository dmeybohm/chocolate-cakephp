package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberTypeResult
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.php.PhpIndex

/**
 * A view file, by the conventions of its structure: its template key, and the plugin it belongs
 * to, or null for the app.
 */
data class ViewFile(val structure: TemplateDirStructure, val templateKey: String, val plugin: String?)

/**
 * The classes whose configuration supplies the helpers of a view file.
 */
object ViewHelperReceivers {

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
     * The receivers of [viewFile]: its structure's view classes, and the controllers that render
     * it when the structure uses them.
     */
    fun of(project: Project, viewFile: ViewFile): List<String> {
        val settings = Settings.getInstance(project)
        val structure = viewFile.structure
        if (!structure.usesRenderingControllers) {
            return structure.viewClassReceivers(settings)
        }
        val controllers = RenderingControllers.of(project, viewFile).ifEmpty { structure.fallbackReceivers }
        return structure.viewClassReceivers(settings) + controllers
    }

    /**
     * The receivers of [file] when it is a view file.
     */
    fun ofFile(project: Project, settings: Settings, file: PsiFile): List<String>? {
        val virtualFile = file.originalFile.virtualFile ?: return null
        return viewFileOf(project, settings, virtualFile)?.let { of(project, it) }
    }

    /**
     * The configured type of helper [fieldName] in a view rendered by [receivers]. A view loads
     * any helper by name, so the result only determines the type when every receiver loads it.
     */
    fun helperMemberType(project: Project, receivers: Collection<String>, fieldName: String): MemberTypeResult? {
        val perReceiver = receivers.map {
            ConfiguredMemberService.memberType(project, listOf(it), setOf(MemberKind.HELPER), fieldName)
        }
        if (perReceiver.all { it == null || it.classFqns.isEmpty() }) {
            return null
        }
        val fqns = perReceiver.flatMap { it?.classFqns.orEmpty() }.toSet()
        val phpIndex = PhpIndex.getInstance(project)
        return MemberTypeResult(
            classFqns = fqns,
            classes = fqns.flatMap { phpIndex.getClassesByFQN(it) },
            hasInitializeLoad = perReceiver.all { it != null && it.classFqns.isNotEmpty() && it.hasInitializeLoad },
        )
    }

    /**
     * [viewFile] as text for a type signature, its parts separated by [separator].
     */
    fun encode(viewFile: ViewFile, separator: Char): String =
        "${viewFile.structure.id}${viewFile.templateKey}$separator${viewFile.plugin.orEmpty()}"

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
