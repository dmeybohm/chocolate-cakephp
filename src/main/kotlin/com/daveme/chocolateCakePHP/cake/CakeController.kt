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
import com.jetbrains.php.lang.psi.elements.ParenthesizedExpression
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpMatchExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.TernaryExpression
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
 * Build ActionNames from a list of template paths: the first is the default, the rest are "other".
 * Returns null when the list is empty.
 */
fun actionNamesFromTemplateNames(templateNames: List<String>): ActionNames? {
    if (templateNames.isEmpty()) {
        return null
    }
    val actionNames = templateNames.map { actionNameFromPath(it) }
    return ActionNames(
        defaultActionName = actionNames.first(),
        otherActionNames = actionNames.drop(1)
    )
}

/**
 * Collect every literal string a template expression can evaluate to.
 *
 * Handles:
 *   'literal'                               -> ["literal"]
 *   $cond ? 'a' : 'b'                       -> ["a", "b"]   (the condition is never inspected)
 *   $cond ?: 'b'                            -> ["b"]
 *   match ($x) { 1 => 'a', default => 'b' } -> ["a", "b"]  (arm conditions are never inspected)
 *   ('a')                                   -> ["a"]
 * These nest, so a ternary inside a match arm works too. Anything else yields no values.
 */
fun templateNamesFromExpression(expression: PsiElement?): List<String> {
    return when (expression) {
        null -> emptyList()
        is StringLiteralExpression -> listOf(expression.contents)
        is TernaryExpression ->
            templateNamesFromExpression(expression.trueVariant) +
                templateNamesFromExpression(expression.falseVariant)
        is PhpMatchExpression ->
            // getMatchArms() may or may not include the default arm depending on plugin version
            (expression.matchArms + listOfNotNull(expression.defaultMatchArm))
                .distinct()
                .flatMap { templateNamesFromExpression(it.bodyExpression) }
        is ParenthesizedExpression -> templateNamesFromExpression(expression.argument)
        else -> emptyList()
    }
}

/**
 * Represents a ViewBuilder method call (setTemplate or setTemplatePath).
 */
data class ViewBuilderCall(
    val methodName: String,            // "setTemplate" or "setTemplatePath"
    val parameterValues: List<String>, // The template names or paths (several for ternary / match)
    val offset: Int                    // Text offset for ordering
)

/**
 * The ActionNames produced by one setTemplate() call.
 */
data class ViewBuilderCallActionNames(
    val call: ViewBuilderCall,
    val actionNames: List<ActionName>
)

/**
 * Find all ViewBuilder calls (setTemplate and setTemplatePath) in a PSI element.
 *
 * This function finds calls matching the patterns:
 *   $this->viewBuilder()->setTemplate('name')
 *   $this->viewBuilder()->setTemplatePath('path')
 *   $this->viewBuilder()->setTemplatePath('path')->setTemplate('name')  (chained)
 *
 * For chained calls, the path is combined structurally at detection time. Only the
 * setTemplate call is returned with the combined path (e.g., "path/name"). This
 * eliminates the need for offset-based proximity detection in processing.
 *
 * The structural detection works regardless of whitespace, comments, or newlines
 * between chained methods, as the PSI tree preserves the structural relationships.
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
        // Matches: $this->viewBuilder()->setTemplate('name')
        // Matches: $this->viewBuilder()->setTemplatePath('path')
        // Does NOT match setTemplatePath when it's part of a chain (handled by Pattern 2)
        if (receiverMethodName == "viewBuilder") {
            // The classReference of viewBuilder() could be either:
            // - A Variable ($this) for simple cases
            // - A FieldReference ($this->) which contains the Variable
            val classRef = receiverMethodRef.classReference
            val isThisVariable = when (classRef) {
                is Variable -> classRef.name == "this"
                is FieldReference -> (classRef.classReference as? Variable)?.name == "this"
                else -> false
            }

            if (!isThisVariable) {
                return@mapNotNull null
            }

            // Skip setTemplatePath if it's part of a chained call
            // Check if this method is used as a receiver for another method
            if (methodName == "setTemplatePath") {
                // Check if methodRef appears as a classReference in any other MethodReference
                val isPartOfChain = methodRefs.any { otherRef ->
                    otherRef.classReference == methodRef
                }
                if (isPartOfChain) {
                    // This setTemplatePath is part of a chain, skip it
                    // Pattern 2 will handle the complete chain
                    return@mapNotNull null
                }
            }

            // Extract the string parameter (or every branch of a ternary / match)
            val parameterList = methodRef.parameterList
            val parameterValues = templateNamesFromExpression(parameterList?.parameters?.getOrNull(0))
            if (parameterValues.isEmpty()) {
                return@mapNotNull null
            }

            return@mapNotNull ViewBuilderCall(
                methodName = methodName,
                parameterValues = parameterValues,
                offset = methodRef.textRange.startOffset
            )
        }

        // Pattern 2: Chained calls - setTemplate's receiver is setTemplatePath
        // For chains like: $this->viewBuilder()->setTemplatePath('path')->setTemplate('name')
        // We detect this structurally and combine the paths immediately
        if (methodName == "setTemplate" && receiverMethodName == "setTemplatePath") {
            // Verify the chain goes back to viewBuilder()
            val viewBuilderRef = receiverMethodRef.classReference as? MethodReference ?: return@mapNotNull null
            if (viewBuilderRef.name != "viewBuilder") return@mapNotNull null

            // Check if the viewBuilder() is called on $this
            val classRef = viewBuilderRef.classReference
            val isThisVariable = when (classRef) {
                is Variable -> classRef.name == "this"
                is FieldReference -> (classRef.classReference as? Variable)?.name == "this"
                else -> false
            }
            if (!isThisVariable) return@mapNotNull null

            // Extract BOTH path and template parameters structurally.
            // Either may be a ternary / match, so combine every path with every template.
            val pathValues = templateNamesFromExpression(receiverMethodRef.parameterList?.parameters?.getOrNull(0))
            val templateValues = templateNamesFromExpression(methodRef.parameterList?.parameters?.getOrNull(0))
            if (pathValues.isEmpty() || templateValues.isEmpty()) {
                return@mapNotNull null
            }

            // Combine the paths immediately using joinViewPath for normalization
            // This handles whitespace, comments, newlines - all preserved in PSI structure
            val combinedPaths = pathValues.flatMap { path ->
                templateValues.map { template -> joinViewPath(path, template) }
            }

            // Return a setTemplate call with the COMBINED paths
            // This eliminates the need for offset-based proximity detection later
            return@mapNotNull ViewBuilderCall(
                methodName = "setTemplate",
                parameterValues = combinedPaths,
                offset = methodRef.textRange.startOffset
            )
        }

        // Pattern 3: Chained setTemplatePath in a chain like:
        // $this->viewBuilder()->setTemplatePath()->setTemplate()
        // The setTemplatePath will be found by checking if any method has it as a receiver
        // This is already handled by Pattern 1 above

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
 *   - If the call already has a path (from chained detection): use it as-is
 *   - Else if we have a current prefix: combine prefix + name
 *   - Otherwise: use name as-is
 *
 * The "/" prefix makes the path absolute (see ActionName.isAbsolute).
 *
 * Note: For chained calls like ->setTemplatePath('X')->setTemplate('Y'), the path
 * is already combined by findViewBuilderCalls() using structural detection, so no
 * offset-based proximity checks are needed.
 *
 * @param viewBuilderCalls List of ViewBuilder calls sorted by offset
 * @return List of ActionName objects
 */
fun actionNamesFromViewBuilderCalls(viewBuilderCalls: List<ViewBuilderCall>): List<ActionName> {
    return actionNamesBySetTemplateCall(viewBuilderCalls).flatMap { it.actionNames }
}

/**
 * Like actionNamesFromViewBuilderCalls, but keeps the ActionNames grouped by the
 * setTemplate() call that produced them, so a caller can look up one specific call.
 *
 * setTemplatePath() state is a list because the path itself may be a ternary or
 * match expression; every current path is combined with every template name.
 */
fun actionNamesBySetTemplateCall(viewBuilderCalls: List<ViewBuilderCall>): List<ViewBuilderCallActionNames> {
    val result = mutableListOf<ViewBuilderCallActionNames>()
    var currentTemplatePaths: List<String>? = null

    for (call in viewBuilderCalls) {
        when (call.methodName) {
            "setTemplatePath" -> {
                // Update the current template path for subsequent separate setTemplate calls
                // Example: $this->viewBuilder()->setTemplatePath('path');
                //          $this->viewBuilder()->setTemplate('name');
                currentTemplatePaths = call.parameterValues
            }
            "setTemplate" -> {
                val viewNames = call.parameterValues.flatMap { parameterValue ->
                    if (parameterValue.contains('/')) {
                        // Path already combined (from chained detection)
                        // Prepend "/" to make it absolute
                        listOf("/${parameterValue}")
                    } else if (currentTemplatePaths != null) {
                        // Separate calls - combine with current state
                        // Prepend "/" to make it absolute so the controller path is not prepended
                        currentTemplatePaths.map { "/$it/${parameterValue}" }
                    } else {
                        // No path, just the template name
                        listOf(parameterValue)
                    }
                }
                result.add(ViewBuilderCallActionNames(call, viewNames.map { actionNameFromPath(it) }))
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
    val renderActionNames: List<ActionName> = renderCalls.flatMap {
        if (it.name != "render") {
            return@flatMap emptyList()
        }
        templateNamesFromExpression(it.parameterList?.getParameter(0)).map { name -> actionNameFromPath(name) }
    }

    // Collect ViewBuilder action names (setTemplate/setTemplatePath)
    val viewBuilderCalls = findViewBuilderCalls(method)
    val viewBuilderActionNames = actionNamesFromViewBuilderCalls(viewBuilderCalls)

    // Collect $this->view = 'template' field assignments (CakePHP 2)
    val fieldAssignments = PsiTreeUtil.findChildrenOfType(method, AssignmentExpression::class.java)
    val fieldAssignmentActionNames: List<ActionName> = fieldAssignments.flatMap { assignment ->
        val fieldRef = assignment.variable as? FieldReference ?: return@flatMap emptyList()
        val variable = fieldRef.classReference as? Variable ?: return@flatMap emptyList()

        // Check it's $this->view
        if (variable.name != "this" || fieldRef.name != "view") {
            return@flatMap emptyList()
        }

        // Get the assigned value(s)
        templateNamesFromExpression(assignment.value).map { name -> actionNameFromPath(name) }
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
    val templateNames = templateNamesFromExpression(methodReference.parameterList?.getParameter(0))
    return actionNamesFromTemplateNames(templateNames)
}

/**
 * Get ActionNames from a single viewBuilder()->setTemplate() call.
 *
 * This function handles both normal and chained ViewBuilder calls:
 * - $this->viewBuilder()->setTemplate('name')
 * - $this->viewBuilder()->setTemplatePath('path')->setTemplate('name')
 *
 * For state tracking (when there's a preceding setTemplatePath in the same method),
 * we use findViewBuilderCalls() and actionNamesFromViewBuilderCalls() to properly
 * combine the path with the template.
 *
 * @param methodReference The MethodReference to the setTemplate call
 * @return ActionNames or null if not a valid setTemplate call
 */
fun actionNamesFromViewBuilderCall(methodReference: MethodReference): ActionNames? {
    if (methodReference.name != "setTemplate") {
        return null
    }

    // Get the containing method to check for preceding setTemplatePath calls
    val containingMethod = PsiTreeUtil.getParentOfType(
        methodReference,
        Method::class.java
    ) ?: return null

    // Find all ViewBuilder calls in the method (preserves state tracking)
    val allViewBuilderCalls = findViewBuilderCalls(containingMethod)

    // Convert to ActionNames using state tracking, then pick out this specific setTemplate call
    val currentOffset = methodReference.textRange.startOffset
    val matching = actionNamesBySetTemplateCall(allViewBuilderCalls).find {
        it.call.methodName == "setTemplate" && it.call.offset == currentOffset
    } ?: return null

    val actionNames = matching.actionNames
    if (actionNames.isEmpty()) {
        return null
    }
    return ActionNames(
        defaultActionName = actionNames.first(),
        otherActionNames = actionNames.drop(1)
    )
}

/**
 * Get ActionNames from a single $this->view field assignment.
 *
 * Used in CakePHP 2 for specifying view templates:
 *   $this->view = 'template_name';
 *
 * @param assignmentExpression The AssignmentExpression to check
 * @return ActionNames or null if not a valid $this->view assignment
 */
fun actionNamesFromViewAssignment(assignmentExpression: AssignmentExpression): ActionNames? {
    val fieldRef = assignmentExpression.variable as? FieldReference ?: return null
    val variable = fieldRef.classReference as? Variable ?: return null

    // Check it's $this->view
    if (variable.name != "this" || fieldRef.name != "view") {
        return null
    }

    // Get the assigned value(s)
    val templateNames = templateNamesFromExpression(assignmentExpression.value)
    return actionNamesFromTemplateNames(templateNames)
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