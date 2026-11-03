package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.effectivePluginName
import com.daveme.chocolateCakePHP.findPluginConfigByName
import com.daveme.chocolateCakePHP.getControllerSubclasses
import com.daveme.chocolateCakePHP.isAnyCellClass
import com.daveme.chocolateCakePHP.isAnyControllerClass
import com.daveme.chocolateCakePHP.latinCapitalize
import com.daveme.chocolateCakePHP.underscoreToCamelCase
import com.daveme.chocolateCakePHP.view.RenderingControllers
import com.daveme.chocolateCakePHP.view.viewFileOf
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

const val CONTROLLER_PARENT_CLASS = "\\Cake\\Controller\\Controller"

private val routeScopeMethods = setOf("scope", "prefix", "plugin")

/**
 * The `plugin` or `prefix` key of a route target array: left out, explicitly none, or a name.
 */
sealed class RouteKey {
    object Absent : RouteKey()
    object None : RouteKey()
    data class Named(val name: String) : RouteKey()
}

/**
 * A route target as written: the keys of a target array, or the parts of a
 * `[Plugin.][Prefix/]Controller::action` string.
 */
data class RouteTarget(
    val plugin: RouteKey = RouteKey.Absent,
    val prefix: RouteKey = RouteKey.Absent,
    val controller: String? = null,
    val action: String? = null,
)

/**
 * The plugin and prefix the `scope()`, `prefix()` and `plugin()` closures around a route give
 * the targets connected inside them.
 */
data class RouteScope(val plugin: String? = null, val prefix: String? = null)

/**
 * One controller a URL-building array may run under, standing in for the request the URL is
 * built in: its plugin, prefix and name as route keys, and the action when it is known.
 */
data class RouteContextController(
    val plugin: String?,
    val prefix: String?,
    val controller: String,
    val action: String?,
)

/**
 * A plugin or prefix of a resolved target: unknown, or known to be none or a name.
 */
sealed class RouteValue {
    object Unknown : RouteValue()
    data class Known(val name: String?) : RouteValue()
}

/**
 * A route target with the defaults of where it is written applied.
 */
data class ResolvedRouteTarget(
    val plugin: RouteValue,
    val prefix: RouteValue,
    val controller: String?,
    val action: String?,
)

/**
 * A controller class as a route target names it: the plugin it belongs to, the prefix path
 * of the namespaces between `Controller` and the class, and the class name without `Controller`.
 */
data class RouteControllerName(val plugin: String?, val prefix: String?, val name: String)

private val stringRouteTarget =
    Regex("""^(?:([A-Za-z0-9]+(?:/[A-Za-z0-9]+)*)\.)?(?:([A-Za-z0-9]+(?:/[A-Za-z0-9]+)*)/)?([A-Za-z0-9]+)::([A-Za-z0-9_]+)$""")

/**
 * The route target an array literal spells out. Keys and values that are not literals are left
 * out, and `null` or `false` for `plugin` or `prefix` is an explicit none.
 */
fun routeTargetFromArray(array: ArrayCreationExpression): RouteTarget {
    var target = RouteTarget()
    for (element in array.hashElements) {
        val key = (element.key as? StringLiteralExpression)?.contents ?: continue
        val value = element.value
        val literal = (value as? StringLiteralExpression)?.contents
        val isNone = value is ConstantReference && value.name?.lowercase() in setOf("null", "false")
        target = when (key) {
            "plugin" -> target.copy(plugin = routeKey(literal, isNone))
            "prefix" -> target.copy(prefix = routeKey(literal, isNone))
            "controller" -> target.copy(controller = literal)
            "action" -> target.copy(action = literal)
            else -> target
        }
    }
    return target
}

private fun routeKey(literal: String?, isNone: Boolean): RouteKey = when {
    isNone -> RouteKey.None
    literal != null -> RouteKey.Named(literal)
    else -> RouteKey.Absent
}

/**
 * The route target a `[Plugin.][Prefix/]Controller::action` string spells out, or null when the
 * string is not one.
 */
fun routeTargetFromString(target: String): RouteTarget? {
    val match = stringRouteTarget.matchEntire(target) ?: return null
    val (plugin, prefix, controller, action) = match.destructured
    return RouteTarget(
        plugin = if (plugin.isEmpty()) RouteKey.Absent else RouteKey.Named(plugin),
        prefix = if (prefix.isEmpty()) RouteKey.Absent else RouteKey.Named(prefix),
        controller = controller,
        action = action,
    )
}

/**
 * The scope the `scope()`, `prefix()` and `plugin()` closures enclosing [element] give it. Only
 * closures written as arguments of those calls count; outer prefixes join inner ones with `/`.
 */
fun routeScopeOf(element: PsiElement): RouteScope {
    var plugin: String? = null
    val prefixes = ArrayDeque<String>()
    var closure: Function? = PsiTreeUtil.getParentOfType(element, Function::class.java)
    while (closure != null) {
        val call = enclosingScopeCall(closure)
        if (call != null) {
            val name = call.name
            val firstArgument = (call.parameterList?.getParameter(0) as? StringLiteralExpression)?.contents
            val params = call.parameterList?.parameters?.getOrNull(1) as? ArrayCreationExpression
            when (name) {
                "prefix" -> firstArgument?.let { prefixes.addFirst(it) }
                "plugin" -> if (plugin == null) plugin = firstArgument
                "scope" -> {
                    val target = params?.let { routeTargetFromArray(it) } ?: RouteTarget()
                    (target.prefix as? RouteKey.Named)?.let { prefixes.addFirst(it.name) }
                    if (plugin == null) {
                        plugin = (target.plugin as? RouteKey.Named)?.name
                    }
                }
            }
        }
        closure = PsiTreeUtil.getParentOfType(closure, Function::class.java)
    }
    return RouteScope(plugin, prefixes.joinToString("/").ifEmpty { null })
}

/**
 * Whether [closure] is the callback of a `scope()`, `prefix()` or `plugin()` call, whose
 * parameter is a route builder.
 */
fun isRouteScopeClosure(closure: Function): Boolean = enclosingScopeCall(closure) != null

// The closure may sit inside a wrapper expression node, so find the argument that ends with it:
private fun enclosingScopeCall(closure: Function): MethodReference? {
    val parameterList = PsiTreeUtil.getParentOfType(closure, ParameterList::class.java) ?: return null
    val isArgument = parameterList.parameters.any {
        PsiTreeUtil.isAncestor(it, closure, false) && it.textRange.endOffset == closure.textRange.endOffset
    }
    if (!isArgument) {
        return null
    }
    val call = parameterList.parent as? MethodReference ?: return null
    return call.takeIf { it.name in routeScopeMethods }
}

/**
 * The controllers a URL-building array at [element] may run under: the enclosing controller
 * class, with the enclosing public method as the action, or every controller that renders the
 * view file the array is in, with an unknown action. Cells render in their caller's request, so
 * they are never candidates. Empty anywhere else.
 */
fun routeContextOf(settings: Settings, element: PsiElement): List<RouteContextController> {
    val project = element.project
    val enclosingClass = PsiTreeUtil.getParentOfType(element, PhpClass::class.java)
    if (enclosingClass != null) {
        if (!enclosingClass.fqn.isAnyControllerClass()) {
            return emptyList()
        }
        val name = routeControllerNameFromClass(settings, enclosingClass) ?: return emptyList()
        val method = PsiTreeUtil.getParentOfType(element, Method::class.java)
            ?.takeIf { it.access.isPublic && !it.isStatic }
        return listOf(RouteContextController(name.plugin, name.prefix, name.name, method?.name))
    }
    val file = element.containingFile?.virtualFile ?: return emptyList()
    val viewFile = viewFileOf(project, settings, file) ?: return emptyList()
    val phpIndex = PhpIndex.getInstance(project)
    return RenderingControllers.of(project, viewFile)
        .filter { it.isAnyControllerClass() && !it.isAnyCellClass() }
        .flatMap { phpIndex.getClassesByFQN(it) }
        .mapNotNull { routeControllerNameFromClass(settings, it) }
        .map { RouteContextController(it.plugin, it.prefix, it.name, null) }
}

/**
 * [target] as connected inside [scope]: the scope fills in the plugin and prefix the target
 * leaves out, and an explicit none in the target wins.
 */
fun resolveInScope(target: RouteTarget, scope: RouteScope): ResolvedRouteTarget =
    ResolvedRouteTarget(
        plugin = resolveKey(target.plugin, RouteValue.Known(scope.plugin)),
        prefix = resolveKey(target.prefix, RouteValue.Known(scope.prefix)),
        controller = target.controller,
        action = target.action,
    )

/**
 * [target] as built in the request of each of [candidates], following `Router::url()`: the
 * request fills in the plugin, prefix and controller the target leaves out, and the action when
 * the target's controller is the request's. With no candidate the plugin and prefix stay
 * unknown, as the request may belong to any, and only the explicit keys resolve.
 */
fun resolveInContext(target: RouteTarget, candidates: List<RouteContextController>): List<ResolvedRouteTarget> {
    if (candidates.isEmpty()) {
        return listOf(
            ResolvedRouteTarget(
                plugin = resolveKey(target.plugin, RouteValue.Unknown),
                prefix = resolveKey(target.prefix, RouteValue.Unknown),
                controller = target.controller,
                action = target.action,
            )
        )
    }
    return candidates.map { candidate ->
        val controller = target.controller ?: candidate.controller
        val sameController = target.controller == null ||
            target.controller.equals(candidate.controller, ignoreCase = true)
        ResolvedRouteTarget(
            plugin = resolveKey(target.plugin, RouteValue.Known(candidate.plugin)),
            prefix = resolveKey(target.prefix, RouteValue.Known(candidate.prefix)),
            controller = controller,
            action = target.action ?: if (sameController) candidate.action else "index",
        )
    }.distinct()
}

private fun resolveKey(key: RouteKey, default: RouteValue): RouteValue = when (key) {
    is RouteKey.Named -> RouteValue.Known(key.name)
    RouteKey.None -> RouteValue.Known(null)
    RouteKey.Absent -> default
}

/**
 * The namespaces controllers live under, paired with the plugin name a route target uses for
 * each: the app namespace (null) and each configured plugin's namespace.
 */
private fun controllerNamespaces(settings: Settings): List<Pair<String?, String>> =
    listOf(null to settings.appNamespace) +
        settings.cakeThreePluginConfigs.map { it.effectivePluginName() to it.namespace }

/**
 * How a route target names [phpClass], or null when the class is not a controller under a
 * `Controller` namespace. A namespace not configured as a plugin is taken as the plugin's name
 * with `\` as `/`, which is how CakePHP maps a plugin name to its namespace.
 */
fun routeControllerNameFromClass(settings: Settings, phpClass: PhpClass): RouteControllerName? {
    val fqn = phpClass.fqn
    if (!fqn.endsWith("Controller") || fqn.length <= "Controller".length) {
        return null
    }
    val separatorIndex = fqn.indexOf("\\Controller\\")
    if (separatorIndex < 0) {
        return null
    }
    val namespace = fqn.substring(0, separatorIndex)
    val plugin = when {
        namespace.equals(settings.appNamespace, ignoreCase = true) -> null
        else -> controllerNamespaces(settings)
            .firstOrNull { (name, ns) -> name != null && ns.equals(namespace, ignoreCase = true) }
            ?.first
            ?: namespace.removePrefix("\\").replace('\\', '/').ifEmpty { return null }
    }
    val rest = fqn.substring(separatorIndex + "\\Controller\\".length)
    val segments = rest.split('\\')
    val name = segments.last().removeSuffix("Controller")
    if (name.isEmpty()) {
        return null
    }
    val prefix = segments.dropLast(1).joinToString("/").ifEmpty { null }
    return RouteControllerName(plugin, prefix, name)
}

/**
 * The class name a route target resolves to when its plugin, prefix and controller are all
 * known, or null otherwise.
 */
fun controllerClassFqn(settings: Settings, target: ResolvedRouteTarget): String? {
    val plugin = when (val value = target.plugin) {
        is RouteValue.Known -> value.name
        RouteValue.Unknown -> return null
    }
    val prefix = when (val value = target.prefix) {
        is RouteValue.Known -> value.name
        RouteValue.Unknown -> return null
    }
    val controller = target.controller ?: return null
    val namespace = when (plugin) {
        null -> settings.appNamespace
        else -> (settings.findPluginConfigByName(plugin) as? CakeThreePluginConfig)?.namespace
            ?: ("\\" + plugin.replace('/', '\\'))
    }
    val prefixPath = prefix?.split('/')?.joinToString("\\") { camelize(it) }?.let { "$it\\" } ?: ""
    return "${namespace}\\Controller\\${prefixPath}${controller}Controller"
}

/**
 * Whether a controller named [name] is one [target] can resolve to. Unknown parts of the target
 * match anything; a prefix matches camelized per segment, which is how CakePHP 3 and 4 read it.
 */
fun ResolvedRouteTarget.matches(name: RouteControllerName): Boolean {
    if (controller != null && !controller.equals(name.name, ignoreCase = true)) {
        return false
    }
    val pluginMatches = when (val plugin = plugin) {
        RouteValue.Unknown -> true
        is RouteValue.Known -> plugin.name.equals(name.plugin, ignoreCase = true)
    }
    val prefixMatches = when (val prefix = prefix) {
        RouteValue.Unknown -> true
        is RouteValue.Known -> camelizePrefix(prefix.name) == camelizePrefix(name.prefix)
    }
    return pluginMatches && prefixMatches
}

/**
 * The controller classes [target] resolves to: by name when the target is fully known, and
 * otherwise every controller the unknown parts allow.
 */
fun routeTargetControllers(phpIndex: PhpIndex, settings: Settings, target: ResolvedRouteTarget): Collection<PhpClass> {
    controllerClassFqn(settings, target)?.let { fqn ->
        return phpIndex.getClassesByFQN(fqn)
    }
    if (target.controller == null) {
        return emptyList()
    }
    return phpIndex.getControllerSubclasses(settings).filter { phpClass ->
        routeControllerNameFromClass(settings, phpClass)?.let { target.matches(it) } ?: false
    }
}

/**
 * The actions of [controllerClass], as `Controller::isAction()` sees them: its public,
 * non-static methods, inherited ones included, except those `Cake\Controller\Controller` and its
 * traits declare. A method is listed once, from the class nearest to [controllerClass].
 */
fun routeActions(phpIndex: PhpIndex, controllerClass: PhpClass): List<Method> {
    val excluded = phpIndex.getClassesByFQN(CONTROLLER_PARENT_CLASS)
        .flatMap { methodsWithTraits(it) }
        .mapTo(HashSet()) { it.name.lowercase() }
    val seen = HashSet<String>()
    val result = mutableListOf<Method>()
    val visited = mutableSetOf<String>()
    var current: PhpClass? = controllerClass
    while (current != null && visited.add(current.fqn) && !current.fqn.equals(CONTROLLER_PARENT_CLASS, ignoreCase = true)) {
        for (method in methodsWithTraits(current)) {
            val key = method.name.lowercase()
            if (method.access.isPublic && !method.isStatic && key !in excluded && seen.add(key)) {
                result.add(method)
            }
        }
        current = current.superClass
    }
    return result
}

private fun methodsWithTraits(phpClass: PhpClass, visited: MutableSet<String> = mutableSetOf()): List<Method> {
    if (!visited.add(phpClass.fqn)) {
        return emptyList()
    }
    return phpClass.ownMethods.toList() + phpClass.traits.flatMap { methodsWithTraits(it, visited) }
}

private fun camelize(segment: String): String = segment.underscoreToCamelCase().latinCapitalize()

private fun camelizePrefix(prefix: String?): String? =
    prefix?.split('/')?.joinToString("/") { camelize(it) }
