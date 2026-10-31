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
     * The configured type of helper [fieldName] in [viewFile]. A view loads any helper by name, so
     * the result only determines the type when the view class or every controller loads it.
     */
    fun helperMemberType(project: Project, viewFile: ViewFile, fieldName: String): MemberTypeResult? {
        val settings = Settings.getInstance(project)
        val viewClasses = viewFile.structure.viewClassReceivers(settings)
        val viewClassType = helperType(project, viewClasses, fieldName)
        // View::initialize() runs after the controller's helpers reach the view, so its load wins:
        if (viewClassType != null && viewClassType.hasInitializeLoad) {
            return viewClassType
        }
        val perController = (of(project, viewFile) - viewClasses.toSet()).map {
            helperType(project, listOf(it), fieldName)
        }
        val fqns = viewClassType?.classFqns.orEmpty() + perController.flatMap { it?.classFqns.orEmpty() }
        if (fqns.isEmpty()) {
            return null
        }
        val phpIndex = PhpIndex.getInstance(project)
        return MemberTypeResult(
            classFqns = fqns,
            classes = fqns.flatMap { phpIndex.getClassesByFQN(it) },
            hasInitializeLoad = perController.isNotEmpty() && perController.all { it != null && it.hasInitializeLoad },
        )
    }

    private fun helperType(project: Project, receivers: List<String>, fieldName: String): MemberTypeResult? =
        ConfiguredMemberService.memberType(project, receivers, setOf(MemberKind.HELPER), fieldName)
            ?.takeIf { it.classFqns.isNotEmpty() }
}
