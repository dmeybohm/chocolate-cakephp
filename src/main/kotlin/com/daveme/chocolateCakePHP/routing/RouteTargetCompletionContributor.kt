package com.daveme.chocolateCakePHP.routing

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.ResolvedRouteTarget
import com.daveme.chocolateCakePHP.cake.RouteControllerName
import com.daveme.chocolateCakePHP.cake.RouteKey
import com.daveme.chocolateCakePHP.cake.RouteScope
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
import com.daveme.chocolateCakePHP.effectivePluginName
import com.daveme.chocolateCakePHP.getControllerSubclasses
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Completes the strings of a route target: the `controller`, `action`, `plugin` and `prefix`
 * keys of a target array, the `Controller::action` string of a route, and the controller name
 * of `resources()`.
 */
class RouteTargetCompletionContributor : CompletionContributor() {

    init {
        val provider = RouteTargetCompletionProvider()
        extend(CompletionType.BASIC, RouteTargetPatterns.stringForCompletion, provider)
        extend(CompletionType.SMART, RouteTargetPatterns.stringForCompletion, provider)
    }

    private class RouteTargetCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            completionResultSet: CompletionResultSet
        ) {
            val literal = parameters.position.parent as? StringLiteralExpression ?: return
            val project = literal.project
            if (DumbService.getInstance(project).isDumb) {
                return
            }
            val settings = Settings.getInstance(project)
            if (!settings.cake3Enabled) {
                return
            }
            val position = routeTargetPositionOf(literal) ?: return

            // Target strings contain `.`, `/` and `::`, so match against all of the typed text:
            val valueRange = literal.valueRange
            val caretInLiteral = (parameters.offset - literal.textRange.startOffset)
                .coerceIn(valueRange.startOffset, valueRange.endOffset)
            val typed = literal.text.substring(valueRange.startOffset, caretInLiteral)
            val resultSet = completionResultSet.withPrefixMatcher(typed)
            val completion = Completion(project, settings, resultSet)

            when (position) {
                is RouteTargetPosition.ArrayKey -> completion.arrayKey(position)
                is RouteTargetPosition.StringTarget -> completion.stringTarget(position, typed)
                is RouteTargetPosition.Resources -> completion.resources(position)
            }
        }
    }

    private class Completion(
        project: com.intellij.openapi.project.Project,
        private val settings: Settings,
        private val resultSet: CompletionResultSet,
    ) {
        private val phpIndex = PhpIndex.getInstance(project)

        private val controllers: List<Pair<PhpClass, RouteControllerName>> by lazy {
            phpIndex.getControllerSubclasses(settings).mapNotNull { phpClass ->
                routeControllerNameFromClass(settings, phpClass)?.let { phpClass to it }
            }
        }

        fun arrayKey(position: RouteTargetPosition.ArrayKey) {
            // The key being completed holds the typed text, so resolve without it:
            val target = routeTargetFromArray(position.array).let {
                when (position.key) {
                    "controller" -> it.copy(controller = null)
                    "action" -> it.copy(action = null)
                    "plugin" -> it.copy(plugin = RouteKey.Absent)
                    else -> it.copy(prefix = RouteKey.Absent)
                }
            }
            val targets = when (position.kind) {
                RouteTargetCallKind.ROUTE_BUILDER -> listOf(resolveInScope(target, routeScopeOf(position.array)))
                RouteTargetCallKind.URL_BUILDER -> resolveInContext(target, routeContextOf(settings, position.array))
            }
            when (position.key) {
                "controller" -> controllerNames(targets)
                "action" -> actions(targets)
                "plugin" -> plugins()
                "prefix" -> prefixes(targets)
            }
        }

        private fun controllerNames(targets: List<ResolvedRouteTarget>) {
            for ((phpClass, name) in controllers) {
                if (targets.any { it.copy(controller = null).matches(name) }) {
                    addController(name.name, phpClass)
                }
            }
        }

        private fun actions(targets: List<ResolvedRouteTarget>) {
            val seen = HashSet<String>()
            for (target in targets) {
                for (controllerClass in routeTargetControllers(phpIndex, settings, target.copy(action = null))) {
                    for (method in routeActions(phpIndex, controllerClass)) {
                        if (seen.add(method.name)) {
                            resultSet.addElement(
                                LookupElementBuilder.create(method.name)
                                    .withIcon(PhpIcons.METHOD)
                                    .withTypeText(method.containingClass?.name)
                            )
                        }
                    }
                }
            }
        }

        private fun plugins() {
            val names = linkedSetOf<String>()
            settings.cakeThreePluginConfigs.mapTo(names) { it.effectivePluginName() }
            controllers.mapNotNullTo(names) { it.second.plugin }
            for (name in names.filter { it.isNotBlank() }) {
                resultSet.addElement(LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Folder))
            }
        }

        private fun prefixes(targets: List<ResolvedRouteTarget>) {
            val prefixes = linkedSetOf<String>()
            for ((_, name) in controllers) {
                val prefix = name.prefix ?: continue
                if (targets.any { it.copy(controller = null, prefix = RouteValue.Unknown).matches(name) }) {
                    prefixes.add(prefix)
                }
            }
            for (prefix in prefixes) {
                resultSet.addElement(LookupElementBuilder.create(prefix).withIcon(AllIcons.Nodes.Folder))
            }
        }

        /**
         * `[Plugin.][Prefix/]Controller::action`. A plugin part is only written outside a plugin
         * scope, and a prefix part replaces the scope's prefix, so the spelling offered is the one
         * that reaches each controller from the scope.
         */
        fun stringTarget(position: RouteTargetPosition.StringTarget, typed: String) {
            val scope = routeScopeOf(position.call)
            if (typed.contains("::")) {
                val controllerPart = typed.substringBefore("::")
                val target = routeTargetFromString("$controllerPart::index") ?: return
                val resolved = resolveInScope(target, scope)
                val seen = HashSet<String>()
                for (controllerClass in routeTargetControllers(phpIndex, settings, resolved)) {
                    for (method in routeActions(phpIndex, controllerClass)) {
                        if (seen.add(method.name)) {
                            resultSet.addElement(
                                LookupElementBuilder.create("$controllerPart::${method.name}")
                                    .withPresentableText(method.name)
                                    .withIcon(PhpIcons.METHOD)
                                    .withTypeText(method.containingClass?.name)
                            )
                        }
                    }
                }
                return
            }
            for ((phpClass, name) in controllers) {
                val spelling = stringSpelling(scope, name) ?: continue
                addController(spelling, phpClass)
            }
        }

        private fun stringSpelling(scope: RouteScope, name: RouteControllerName): String? {
            val plugin = when {
                scope.plugin == null -> name.plugin?.let { "$it." } ?: ""
                name.plugin.equals(scope.plugin, ignoreCase = true) -> ""
                else -> return null
            }
            val prefix = when {
                name.prefix == null -> if (scope.prefix == null) "" else return null
                samePrefix(scope.prefix, name) -> ""
                else -> "${name.prefix}/"
            }
            return "$plugin$prefix${name.name}"
        }

        private fun samePrefix(scopePrefix: String?, name: RouteControllerName): Boolean =
            ResolvedRouteTarget(RouteValue.Unknown, RouteValue.Known(scopePrefix), null, null).matches(name)

        fun resources(position: RouteTargetPosition.Resources) {
            val resolved = resolveInScope(RouteTarget(), routeScopeOf(position.call))
            for ((phpClass, name) in controllers) {
                if (resolved.matches(name)) {
                    addController(name.name, phpClass)
                }
            }
        }

        private fun addController(lookupString: String, phpClass: PhpClass) {
            resultSet.addElement(
                LookupElementBuilder.create(lookupString)
                    .withIcon(PhpIcons.CLASS)
                    .withTypeText(phpClass.fqn.removePrefix("\\"))
            )
        }
    }
}
