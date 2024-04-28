package com.daveme.chocolateCakePHP.cake

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

data class ActionNames(
    val defaultActionName: String,
    val allActionNames: List<String>
)

/**
 * Get all the action names from a PHP method.
 *
 * The name itself is included, and only if the string is a literal. (so $this->render($dynamic) is filtered out).
 *
 * @param method The method element to search for action names.
 */
fun actionNamesFromControllerMethod(method: Method): ActionNames {
    // Collect $this->render("some_file") calls:
    val renderCalls = PsiTreeUtil.findChildrenOfAnyType(method, false, MethodReference::class.java)
            as Collection<MethodReference>

    val actionNames: List<String> = renderCalls.mapNotNull {
        if (it.name != "render") {
            return@mapNotNull null
        }
        val firstParameter = it.parameterList?.getParameter(0) as? StringLiteralExpression
            ?: return@mapNotNull null
        return@mapNotNull firstParameter.contents
    } + listOf(method.name)

    return ActionNames(
        defaultActionName = method.name,
        allActionNames = actionNames
    )
}

/**
 * Get an ActionNames from the a single render call.
 *
 * If the string param to the render call is not a constant string, or if the name is not render, null is returned.
 *
 * @param methodReference The method reference to search for the action name.
 */
fun actionNamesFromSingleConstantRenderExpression(methodReference: MethodReference): ActionNames? {
    if (methodReference.name != "render") {
        return null
    }
    val firstParameter = methodReference.parameterList?.getParameter(0) as? StringLiteralExpression
        ?: return null

    val renderParameter = firstParameter.contents
    return ActionNames(
        defaultActionName = renderParameter,
        allActionNames = listOf(renderParameter)
    )
}