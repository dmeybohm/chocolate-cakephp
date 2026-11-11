package com.daveme.chocolateCakePHP.routing

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.ResolvedRouteTarget
import com.daveme.chocolateCakePHP.cake.RouteTarget
import com.daveme.chocolateCakePHP.cake.RouteValue
import com.daveme.chocolateCakePHP.cake.matches
import com.daveme.chocolateCakePHP.cake.resolveInContext
import com.daveme.chocolateCakePHP.cake.resolveInScope
import com.daveme.chocolateCakePHP.cake.routeActions
import com.daveme.chocolateCakePHP.cake.routeContextOf
import com.daveme.chocolateCakePHP.cake.routeControllerNameFromClass
import com.daveme.chocolateCakePHP.cake.routeScopeOf
import com.daveme.chocolateCakePHP.cake.routeTargetControllers
import com.daveme.chocolateCakePHP.cake.routeTargetFromArray
import com.daveme.chocolateCakePHP.cake.routeTargetFromString
import com.daveme.chocolateCakePHP.getControllerSubclasses
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Navigates from the strings of a route target: a `controller` key or name to the controller
 * class, an `action` key to the method, and a `prefix` key to the directory of the prefix's
 * namespace.
 */
class RouteTargetGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(psiElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        val project = psiElement.project
        if (DumbService.getInstance(project).isDumb) {
            return PsiElement.EMPTY_ARRAY
        }
        val settings = Settings.getInstance(project)
        if (!settings.cake3Enabled) {
            return PsiElement.EMPTY_ARRAY
        }
        val literal = psiElement.context as? StringLiteralExpression ?: return PsiElement.EMPTY_ARRAY
        val position = routeTargetPositionOf(literal) ?: return PsiElement.EMPTY_ARRAY
        val phpIndex = PhpIndex.getInstance(project)

        val targets: List<PsiElement> = when (position) {
            is RouteTargetPosition.ArrayKey -> arrayKeyTargets(phpIndex, settings, position)
            is RouteTargetPosition.StringTarget -> stringTargets(phpIndex, settings, position, literal, offset)
            is RouteTargetPosition.Resources -> {
                val resolved = resolveInScope(settings, RouteTarget(controller = literal.contents), routeScopeOf(position.call))
                resolved?.let { routeTargetControllers(phpIndex, settings, it).toList() }.orEmpty()
            }
        }
        return targets.distinct().toTypedArray()
    }

    private fun arrayKeyTargets(
        phpIndex: PhpIndex,
        settings: Settings,
        position: RouteTargetPosition.ArrayKey,
    ): List<PsiElement> {
        val target = routeTargetFromArray(position.array)
        val resolved = when (position.kind) {
            RouteTargetCallKind.ROUTE_BUILDER -> listOfNotNull(resolveInScope(settings, target, routeScopeOf(position.array)))
            RouteTargetCallKind.URL_BUILDER -> resolveInContext(target, routeContextOf(settings, position.array))
        }
        return when (position.key) {
            "controller" -> resolved.flatMap { controllersOf(phpIndex, settings, it) }
            "action" -> resolved.flatMap { actionsOf(phpIndex, settings, it) }
            "prefix" -> resolved.flatMap { prefixDirectoriesOf(phpIndex, settings, it) }
            else -> emptyList()
        }
    }

    private fun stringTargets(
        phpIndex: PhpIndex,
        settings: Settings,
        position: RouteTargetPosition.StringTarget,
        literal: StringLiteralExpression,
        offset: Int,
    ): List<PsiElement> {
        val contents = literal.contents
        val target = routeTargetFromString(contents) ?: return emptyList()
        val resolved = resolveInScope(settings, target, routeScopeOf(position.call)) ?: return emptyList()
        val separator = contents.indexOf("::")
        val caretInValue = offset - literal.textRange.startOffset - literal.valueRange.startOffset
        return if (separator >= 0 && caretInValue > separator + 1)
            actionsOf(phpIndex, settings, resolved)
        else
            controllersOf(phpIndex, settings, resolved)
    }

    private fun controllersOf(phpIndex: PhpIndex, settings: Settings, target: ResolvedRouteTarget): List<PhpClass> =
        routeTargetControllers(phpIndex, settings, target.copy(action = null)).toList()

    private fun actionsOf(phpIndex: PhpIndex, settings: Settings, target: ResolvedRouteTarget): List<PsiElement> {
        val action = target.action ?: return emptyList()
        return controllersOf(phpIndex, settings, target).flatMap { controllerClass ->
            routeActions(phpIndex, controllerClass).filter { it.name == action }
        }
    }

    // The directory of the prefix's namespace, found through the controllers in it:
    private fun prefixDirectoriesOf(phpIndex: PhpIndex, settings: Settings, target: ResolvedRouteTarget): List<PsiElement> {
        if (target.prefix !is RouteValue.Known || target.prefix.name == null) {
            return emptyList()
        }
        val inPrefix = target.copy(controller = null, action = null)
        return phpIndex.getControllerSubclasses(settings)
            .filter { phpClass -> routeControllerNameFromClass(settings, phpClass)?.let { inPrefix.matches(settings, it) } ?: false }
            .mapNotNull { it.containingFile?.containingDirectory }
            .distinct()
    }

    override fun getActionText(dataContext: DataContext): String? = null
}
