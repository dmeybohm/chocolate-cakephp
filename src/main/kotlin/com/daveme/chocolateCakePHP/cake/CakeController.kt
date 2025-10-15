package com.daveme.chocolateCakePHP.cake

import com.daveme.chocolateCakePHP.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

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
        return pathPrefix + addViewFilenameExtension(
            templatesDirectory,
            name,
            settings,
            convertCase
        )
    }
}

fun addViewFilenameExtension(
    templatesDirectory: TemplatesDir,
    name: String,
    settings: Settings,
    convertCase: Boolean
): String {
    return when (templatesDirectory) {
        is CakeFourTemplatesDir ->
            "${name.conditionalCamelCaseToUnderscore(convertCase)}.php" // cake 4+
        is CakeThreeTemplatesDir ->
            "${name.conditionalCamelCaseToUnderscore(convertCase)}.${settings.cakeTemplateExtension}"
        is CakeTwoTemplatesDir ->
            "${name}.${settings.cake2TemplateExtension}"
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
 * Represents a ViewBuilder method call (setTemplate or setTemplatePath).
 */
data class ViewBuilderCall(
    val methodName: String,        // "setTemplate" or "setTemplatePath"
    val parameterValue: String,     // The template name or path
    val offset: Int                 // Text offset for ordering
)

/**
 * Find all ViewBuilder calls (setTemplate and setTemplatePath) in a PSI element.
 *
 * This function finds calls matching the patterns:
 *   $this->viewBuilder()->setTemplate('name')
 *   $this->viewBuilder()->setTemplatePath('path')
 *   $this->viewBuilder()->setTemplatePath('path')->setTemplate('name')  (chained)
 *
 * For chained calls, both setTemplatePath and setTemplate are returned as separate
 * ViewBuilderCall objects to preserve state tracking behavior.
 *
 * @param element The PSI element to search (typically a Method)
 * @return List of ViewBuilderCall objects sorted by offset
 */
fun findViewBuilderCalls(element: PsiElement): List<ViewBuilderCall> {
    val methodRefs = PsiTreeUtil.findChildrenOfType(element, MethodReference::class.java)

    return methodRefs.mapNotNull { methodRef ->
        // Check if this is a setTemplate or setTemplatePath call
        val methodName = methodRef.name
        if (methodName != "setTemplate" && methodName != "setTemplatePath") {
            return@mapNotNull null
        }

        val receiverMethodRef = methodRef.classReference as? MethodReference ?: return@mapNotNull null
        val receiverMethodName = receiverMethodRef.name

        // Pattern 1: Normal calls - receiver is viewBuilder()
        if (receiverMethodName == "viewBuilder") {
            val receiverVariable = receiverMethodRef.classReference as? Variable ?: return@mapNotNull null
            if (receiverVariable.name != "this") {
                return@mapNotNull null
            }

            // Extract the string parameter
            val parameterList = methodRef.parameterList
            val firstParam = parameterList?.parameters?.getOrNull(0) as? StringLiteralExpression
                ?: return@mapNotNull null

            return@mapNotNull ViewBuilderCall(
                methodName = methodName,
                parameterValue = firstParam.contents,
                offset = methodRef.textRange.startOffset
            )
        }

        // Pattern 2: Chained calls - setTemplate's receiver is setTemplatePath
        if (methodName == "setTemplate" && receiverMethodName == "setTemplatePath") {
            // Verify the chain goes back to viewBuilder()
            val viewBuilderRef = receiverMethodRef.classReference as? MethodReference ?: return@mapNotNull null
            if (viewBuilderRef.name != "viewBuilder") return@mapNotNull null
            val thisVar = viewBuilderRef.classReference as? Variable ?: return@mapNotNull null
            if (thisVar.name != "this") return@mapNotNull null

            // Extract the template parameter
            val templateParam = methodRef.parameterList?.parameters?.getOrNull(0) as? StringLiteralExpression
                ?: return@mapNotNull null

            // Return as normal setTemplate - state tracking will handle the path
            return@mapNotNull ViewBuilderCall(
                methodName = "setTemplate",
                parameterValue = templateParam.contents,  // Just the template name, not combined!
                offset = methodRef.textRange.startOffset
            )
        }

        // Pattern 3: Chained setTemplatePath - will be processed normally by Pattern 1
        // The PSI tree visits it separately, so no special handling needed

        return@mapNotNull null
    }.sortedBy { it.offset }
}

/**
 * Convert a list of ViewBuilder calls into ActionName objects.
 *
 * This function processes setTemplate and setTemplatePath calls in order,
 * tracking the "current" template path and applying it to subsequent setTemplate calls.
 *
 * Algorithm:
 * - Iterate through calls in order (by offset)
 * - When we see setTemplatePath('path'), store 'path' as the current prefix
 * - When we see setTemplate('name'):
 *   - If we have a current prefix: create ActionName with prefix "/path/" + name
 *   - If no prefix: create ActionName with just name
 *
 * The "/" prefix makes the path absolute (see ActionName.isAbsolute).
 *
 * @param viewBuilderCalls List of ViewBuilder calls sorted by offset
 * @return List of ActionName objects
 */
fun actionNamesFromViewBuilderCalls(viewBuilderCalls: List<ViewBuilderCall>): List<ActionName> {
    val result = mutableListOf<ActionName>()
    var currentTemplatePath: String? = null

    for (call in viewBuilderCalls) {
        when (call.methodName) {
            "setTemplatePath" -> {
                // Update the current template path for subsequent setTemplate calls
                currentTemplatePath = call.parameterValue
            }
            "setTemplate" -> {
                // Build the final path combining setTemplatePath (if any) with setTemplate
                val viewName = if (currentTemplatePath != null) {
                    // Prepend "/" to make it absolute so the controller path is not prepended
                    // This matches the behavior in TemplateGotoDeclarationHandler and ViewFileDataIndexer
                    "/$currentTemplatePath/${call.parameterValue}"
                } else {
                    call.parameterValue
                }

                val actionName = actionNameFromPath(viewName)
                result.add(actionName)
            }
        }
    }

    return result
}

/**
 * Get all the action names from a PHP method.
 *
 * Collects action names from:
 * - The method name itself (default action)
 * - $this->render('template') calls
 * - $this->viewBuilder()->setTemplate('template') calls (with setTemplatePath() support)
 * - $this->view = 'template' field assignments
 *
 * Only literal string parameters are included (dynamic values like $this->render($var) are filtered out).
 *
 * @param method The method element to search for action names.
 */
fun actionNamesFromControllerMethod(method: Method): ActionNames {
    // Collect $this->render("some_file") calls:
    val renderCalls = PsiTreeUtil.findChildrenOfAnyType(method, false, MethodReference::class.java)
            as Collection<MethodReference>

    val defaultActionName = actionNameFromMethod(method)

    // Collect render() action names
    val renderActionNames: List<ActionName> = renderCalls.mapNotNull {
        if (it.name != "render") {
            return@mapNotNull null
        }
        val firstParameter = it.parameterList?.getParameter(0) as? StringLiteralExpression
            ?: return@mapNotNull null
        return@mapNotNull actionNameFromPath(firstParameter.contents)
    }

    // Collect ViewBuilder action names (setTemplate/setTemplatePath)
    val viewBuilderCalls = findViewBuilderCalls(method)
    val viewBuilderActionNames = actionNamesFromViewBuilderCalls(viewBuilderCalls)

    // Collect $this->view = 'template' field assignments (CakePHP 2)
    val fieldAssignments = PsiTreeUtil.findChildrenOfType(method, AssignmentExpression::class.java)
    val fieldAssignmentActionNames: List<ActionName> = fieldAssignments.mapNotNull { assignment ->
        val fieldRef = assignment.variable as? FieldReference ?: return@mapNotNull null
        val variable = fieldRef.classReference as? Variable ?: return@mapNotNull null

        // Check it's $this->view
        if (variable.name != "this" || fieldRef.name != "view") {
            return@mapNotNull null
        }

        // Get the assigned value
        val stringLiteral = assignment.value as? StringLiteralExpression ?: return@mapNotNull null
        return@mapNotNull actionNameFromPath(stringLiteral.contents)
    }

    // Combine all action names
    val allOtherActionNames = renderActionNames + viewBuilderActionNames + fieldAssignmentActionNames

    return ActionNames(
        defaultActionName = defaultActionName,
        otherActionNames = allOtherActionNames
    )
}

/**
 * Get an ActionNames from a single render call.
 *
 * If the string param to the render call is not a constant string, or if the name is not render, null is returned.
 *
 * @param methodReference The method reference to search for the action name.
 */
fun actionNamesFromRenderCall(methodReference: MethodReference): ActionNames? {
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

fun findNavigableControllerMethod(
    project: Project,
    settings: Settings,
    templatesDir: TemplatesDir,
    potentialControllerName: String,
    viewFilename: String
): PsiElement? {
    val controllerClasses = getControllerClassesOfPotentialControllerName(project, settings, potentialControllerName)
    val method = controllerMethodFromViewFilename(controllerClasses, settings, viewFilename, templatesDir)

    if (method == null || !method.canNavigate()) {
        return null
    } else {
        return method
    }
}