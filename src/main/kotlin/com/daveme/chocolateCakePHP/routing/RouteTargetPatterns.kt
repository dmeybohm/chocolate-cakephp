package com.daveme.chocolateCakePHP.routing

import com.daveme.chocolateCakePHP.cake.isRouteScopeClosure
import com.daveme.chocolateCakePHP.isAnyControllerClass
import com.daveme.chocolateCakePHP.isProbablyControllerClass
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

private const val ROUTE_BUILDER_CLASS = "\\Cake\\Routing\\RouteBuilder"
private const val ROUTER_CLASS = "\\Cake\\Routing\\Router"

/**
 * The route builder methods whose second argument is a route target, as an array or a
 * `Controller::action` string.
 */
private val routeBuilderTargetMethods = setOf("connect", "get", "post", "put", "patch", "delete", "head", "options")

/**
 * The keys of a route target array the plugin completes and navigates.
 */
val routeTargetKeys = setOf("controller", "action", "plugin", "prefix")

/**
 * Where a route target is written: in a route builder call, which the enclosing scopes
 * default, or in a URL-building call, which the current request defaults.
 */
enum class RouteTargetCallKind { ROUTE_BUILDER, URL_BUILDER }

/**
 * A string literal's place in a route target.
 */
sealed class RouteTargetPosition {
    abstract val call: MethodReference
    abstract val kind: RouteTargetCallKind

    /**
     * The value of one key of a target array.
     */
    data class ArrayKey(
        val key: String,
        val array: ArrayCreationExpression,
        override val call: MethodReference,
        override val kind: RouteTargetCallKind,
    ) : RouteTargetPosition()

    /**
     * A whole `Controller::action` string given to a route builder method.
     */
    data class StringTarget(override val call: MethodReference) : RouteTargetPosition() {
        override val kind get() = RouteTargetCallKind.ROUTE_BUILDER
    }

    /**
     * The controller name given to `resources()`.
     */
    data class Resources(override val call: MethodReference) : RouteTargetPosition() {
        override val kind get() = RouteTargetCallKind.ROUTE_BUILDER
    }
}

/**
 * The route target position of [literal], or null when it is not part of a route target. The
 * calls recognized are:
 *
 * - `connect()`, the HTTP verb methods and `redirect()` on a route builder, with the target as
 *   the second argument, and `resources()` with a controller name as the first;
 * - `Router::url()` and `Router::normalize()`, `$this->Url->build()` and a controller's
 *   `$this->redirect()`, with the target as the first argument;
 * - `$this->Html->link()` and `$this->Form->postLink()`, with the target as the second argument;
 * - the `url` option of `$this->Form->create()`.
 */
fun routeTargetPositionOf(literal: StringLiteralExpression): RouteTargetPosition? {
    val hashElement = hashElementWithValue(literal)
    if (hashElement != null) {
        val key = (hashElement.key as? StringLiteralExpression)?.contents ?: return null
        if (key !in routeTargetKeys) {
            return null
        }
        val array = hashElement.parent as? ArrayCreationExpression ?: return null
        val (call, kind) = targetArrayCall(array) ?: return null
        return RouteTargetPosition.ArrayKey(key, array, call, kind)
    }
    val parameterList = literal.parent as? ParameterList ?: return null
    val call = parameterList.parent as? MethodReference ?: return null
    val index = parameterList.parameters.indexOf(literal)
    val name = call.name ?: return null
    return when {
        index == 0 && name == "resources" && isRouteBuilderCall(call) -> RouteTargetPosition.Resources(call)
        index == 1 && name in routeBuilderTargetMethods && isRouteBuilderCall(call) -> RouteTargetPosition.StringTarget(call)
        else -> null
    }
}

/**
 * The call [array] is the route target of, with where it is written, or null.
 */
private fun targetArrayCall(array: ArrayCreationExpression): Pair<MethodReference, RouteTargetCallKind>? {
    val parent = hashElementWithValue(array)
    if (parent != null) {
        // $this->Form->create($context, ['url' => [...]])
        if ((parent.key as? StringLiteralExpression)?.contents != "url") {
            return null
        }
        val options = parent.parent as? ArrayCreationExpression ?: return null
        val (call, index) = argumentCall(options) ?: return null
        return if (index == 1 && call.name == "create" && isHelperCall(call, "Form"))
            call to RouteTargetCallKind.URL_BUILDER
        else
            null
    }
    val (call, index) = argumentCall(array) ?: return null
    val name = call.name ?: return null
    return when {
        index == 1 && (name in routeBuilderTargetMethods || name == "redirect") && isRouteBuilderCall(call) ->
            call to RouteTargetCallKind.ROUTE_BUILDER
        index == 0 && (name == "url" || name == "normalize") && isRouterCall(call) ->
            call to RouteTargetCallKind.URL_BUILDER
        index == 0 && name == "build" && isHelperCall(call, "Url") ->
            call to RouteTargetCallKind.URL_BUILDER
        index == 0 && name == "redirect" && isControllerCall(call) ->
            call to RouteTargetCallKind.URL_BUILDER
        index == 1 && name == "link" && isHelperCall(call, "Html") ->
            call to RouteTargetCallKind.URL_BUILDER
        index == 1 && name == "postLink" && isHelperCall(call, "Form") ->
            call to RouteTargetCallKind.URL_BUILDER
        else -> null
    }
}

// A key or value sits inside a wrapper node of the hash element, which getValue() unwraps:
private fun hashElementWithValue(element: PsiElement): ArrayHashElement? =
    PsiTreeUtil.getParentOfType(element, ArrayHashElement::class.java, true)?.takeIf { it.value == element }

private fun argumentCall(argument: PsiElement): Pair<MethodReference, Int>? {
    val parameterList = argument.parent as? ParameterList ?: return null
    val call = parameterList.parent as? MethodReference ?: return null
    return call to parameterList.parameters.indexOf(argument)
}

/**
 * Whether [call] is on a route builder: a receiver typed `Cake\Routing\RouteBuilder`, the
 * parameter of a `scope()`, `prefix()` or `plugin()` closure, which is often untyped, or the
 * static `Router::` form.
 */
private fun isRouteBuilderCall(call: MethodReference): Boolean {
    return when (val receiver = call.classReference) {
        is ClassReference -> isRouterReference(receiver)
        is Variable -> isRouteBuilderVariable(receiver)
        is PhpTypedElement -> receiver.type.types.any { it.equals(ROUTE_BUILDER_CLASS, ignoreCase = true) }
        else -> false
    }
}

private fun isRouteBuilderVariable(variable: Variable): Boolean {
    if (variable.type.types.any { it.equals(ROUTE_BUILDER_CLASS, ignoreCase = true) }) {
        return true
    }
    val parameter = variable.resolve() as? Parameter ?: return false
    val closure = parameter.parent?.parent as? Function ?: return false
    return isRouteScopeClosure(closure)
}

private fun isRouterCall(call: MethodReference): Boolean =
    (call.classReference as? ClassReference)?.let { isRouterReference(it) } ?: false

private fun isRouterReference(reference: ClassReference): Boolean =
    reference.fqn.equals(ROUTER_CLASS, ignoreCase = true) || reference.name == "Router"

// $this->Html->link(...), or a helper reached through any receiver, such as $view->Html
private fun isHelperCall(call: MethodReference, helper: String): Boolean =
    (call.classReference as? FieldReference)?.name == helper

// $this->redirect() in a controller, or on a receiver typed as one
private fun isControllerCall(call: MethodReference): Boolean {
    val receiver = call.classReference as? Variable ?: return false
    if (receiver.name == "this") {
        val phpClass = PsiTreeUtil.getParentOfType(call, PhpClass::class.java) ?: return false
        return phpClass.fqn.isAnyControllerClass()
    }
    return receiver.type.isProbablyControllerClass()
}

/**
 * Patterns for the string literals of route targets.
 */
object RouteTargetPatterns {

    private object InRouteTarget : PatternCondition<StringLiteralExpression>("InRouteTarget") {
        override fun accepts(literal: StringLiteralExpression, context: ProcessingContext): Boolean =
            routeTargetPositionOf(literal) != null
    }

    /**
     * The token the caret is in while typing a route target string, for completion.
     */
    val stringForCompletion: ElementPattern<LeafPsiElement> = psiElement(LeafPsiElement::class.java)
        .withParent(psiElement(StringLiteralExpression::class.java).with(InRouteTarget))

    /**
     * A route target string, for navigation.
     */
    val stringForGotoDeclaration: ElementPattern<StringLiteralExpression> =
        psiElement(StringLiteralExpression::class.java).with(InRouteTarget)
}
