package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberTypeResult
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.php.PhpIndex

/**
 * The classes whose configuration supplies the helpers of a view file.
 */
object ViewHelperReceivers {

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
}
