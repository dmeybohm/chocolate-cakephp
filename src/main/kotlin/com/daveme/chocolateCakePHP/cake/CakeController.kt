package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.camelCaseToUnderscore
import com.daveme.chocolateCakePHP.removeFromStart
import com.daveme.chocolateCakePHP.underscoreToCamelCase
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

fun viewFileNameToActionName(
    viewFileName: String,
    settings: Settings,
    templatesDir: TemplatesDir,
): ActionNames {
    when (templatesDir) {
        is CakeFourTemplatesDir, is CakeThreeTemplatesDir -> {
            val extension = if (templatesDir is CakeThreeTemplatesDir)
                settings.cakeTemplateExtension
            else
                "php"
            val trimmed = viewFileName.removeFromStart(".${extension}", ignoreCase = true)
            val camelCaseActionName = trimmed.underscoreToCamelCase()
            return ActionNames(
                defaultActionName = camelCaseActionName,
                allActionNames = listOf(camelCaseActionName)
            )
        }
        is CakeTwoTemplatesDir -> {
            val extension = settings.cake2TemplateExtension
            val trimmed = viewFileName.removeFromStart(".${extension}", ignoreCase = true)
            return ActionNames(
                defaultActionName = trimmed,
                allActionNames = listOf(trimmed)
            )
        }
    }
}

fun actionNameToViewFilename(
    templatesDirectory: TemplatesDir,
    settings: Settings,
    actionName: String
): String {
    return when (templatesDirectory) {
        is CakeFourTemplatesDir -> "${actionName.camelCaseToUnderscore()}.php" // cake 4+
        is CakeThreeTemplatesDir -> "${actionName.camelCaseToUnderscore()}.${settings.cakeTemplateExtension}"
        is CakeTwoTemplatesDir -> "${actionName}.${settings.cake2TemplateExtension}"
    }
}