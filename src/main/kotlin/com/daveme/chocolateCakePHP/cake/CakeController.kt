package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.*
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

data class ActionName(
    val name: String,
    val pathPrefix: String
) {
    val isAbsolute: Boolean
        get() = pathPrefix.startsWith('/')

    val path: String
        get() = "${pathPrefix}${name}"

    fun getViewFilename(
        templatesDirectory: TemplatesDir,
        settings: Settings,
        convertCase: Boolean
    ): String {
        return when (templatesDirectory) {
            is CakeFourTemplatesDir ->
                "${pathPrefix}${name.conditionalCamelCaseToUnderscore(convertCase)}.php" // cake 4+
            is CakeThreeTemplatesDir ->
                "${pathPrefix}${name.conditionalCamelCaseToUnderscore(convertCase)}.${settings.cakeTemplateExtension}"
            is CakeTwoTemplatesDir ->
                "${pathPrefix}${name}.${settings.cake2TemplateExtension}"
        }
    }
}

fun actionNameFromPath(path: String): ActionName {
    val filename = path.substringAfterLast('/', path)
    val prefix = if (path.contains('/'))
        path.substring(0, path.lastIndexOf('/') + 1)
    else
        ""
    return ActionName(
        name=filename,
        pathPrefix = prefix
    )
}

fun actionNameFromMethod(method: Method): ActionName {
    return ActionName(
        method.name,
        pathPrefix = ""
    )
}

data class ActionNames(
    val defaultActionName: ActionName,
    val otherActionNames: List<ActionName> = listOf(),
) {
}

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

    val defaultActionName = actionNameFromMethod(method)
    val otherActionNames: List<ActionName> = renderCalls.mapNotNull {
        if (it.name != "render") {
            return@mapNotNull null
        }
        val firstParameter = it.parameterList?.getParameter(0) as? StringLiteralExpression
            ?: return@mapNotNull null
        return@mapNotNull actionNameFromPath(firstParameter.contents)
    }

    return ActionNames(
        defaultActionName = defaultActionName,
        otherActionNames = otherActionNames
    )
}

/**
 * Get an ActionNames from a single render call.
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
    val actionName = actionNameFromPath(renderParameter)
    return ActionNames(
        defaultActionName = actionName,
        otherActionNames = listOf()
    )
}

fun viewFilenameToActionName(
    viewFilename: String,
    settings: Settings,
    templatesDir: TemplatesDir,
): ActionNames {
    when (templatesDir) {
        is CakeFourTemplatesDir, is CakeThreeTemplatesDir -> {
            val extension = if (templatesDir is CakeThreeTemplatesDir)
                settings.cakeTemplateExtension
            else
                "php"
            val trimmed = viewFilename.removeFromEnd(".${extension}", ignoreCase = true)
            val camelCaseActionName = trimmed.underscoreToCamelCase()
            val actionName = ActionName(camelCaseActionName, "")
            return ActionNames(
                defaultActionName = actionName,
            )
        }
        is CakeTwoTemplatesDir -> {
            val extension = settings.cake2TemplateExtension
            val trimmed = viewFilename.removeFromEnd(".${extension}", ignoreCase = true)
            val actionName = ActionName(trimmed, "")
            return ActionNames(
                defaultActionName = actionName,
            )
        }
    }
}

fun getControllerClassesOfPotentialControllerName(
    project: Project,
    settings: Settings,
    potentialControllerName: String
): Collection<PhpClass> {
    val phpIndex = PhpIndex.getInstance(project)
    val controllerType = controllerTypeFromControllerName(settings, potentialControllerName)
    val controllerClasses = phpIndex.phpClassesFromType(controllerType)
    return controllerClasses
}

fun controllerMethodFromViewFilename(
    controllerClasses: Collection<PhpClass>,
    settings: Settings,
    viewFilename: String,
    templatesDir: TemplatesDir
): Method? {
    val actionNames = viewFilenameToActionName(viewFilename, settings, templatesDir)
    val method = controllerClasses.findFirstMethodWithName(actionNames.defaultActionName.name)
    return method
}