package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.ParenthesizedExpression
import com.jetbrains.php.lang.psi.elements.PhpMatchArm
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.TernaryExpression
import com.jetbrains.php.lang.psi.elements.Variable

class TemplateGotoDeclarationHandler : GotoDeclarationHandler {

    /**
     * Resolves template view paths, handling plugin-prefixed paths.
     *
     * @param path The template path, possibly with plugin prefix
     * @param settings Plugin settings
     * @param controllerPath The controller path for non-plugin lookups
     * @param allTemplatesPaths All available template paths
     * @param existingActionNames Optional pre-built ActionNames to use for non-plugin case.
     *                            If null, ActionNames will be created from the path.
     */
    private fun resolveTemplateViewPaths(
        path: String,
        settings: Settings,
        controllerPath: ControllerPath,
        allTemplatesPaths: AllTemplatePaths,
        existingActionNames: ActionNames? = null
    ): TemplatePathResolution {
        val result = parseAndLookupPlugin(path, settings)
        val allViewPaths = when (result) {
            is PluginLookupResult.PluginFound -> {
                val pluginActionName = actionNameFromPath(result.resourcePath)
                val pluginActionNames = ActionNames(defaultActionName = pluginActionName)
                allViewPathsFromPluginTemplate(allTemplatesPaths, settings, pluginActionNames, result.pluginConfig)
            }
            is PluginLookupResult.NoPlugin -> {
                val actionNames = existingActionNames
                    ?: ActionNames(defaultActionName = actionNameFromPath(result.originalPath))
                allViewPathsFromController(controllerPath, allTemplatesPaths, settings, actionNames)
            }
        }
        return TemplatePathResolution(allViewPaths, result)
    }

    override fun getGotoDeclarationTargets(
        psiElement: PsiElement?,
        i: Int,
        editor: Editor
    ): Array<PsiElement>? {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        if (DumbService.getInstance(psiElement.project).isDumb) {
            return PsiElement.EMPTY_ARRAY
        }
        val project = psiElement.project
        val settings = Settings.getInstance(project)
        if (!settings.enabled) {
            return PsiElement.EMPTY_ARRAY
        }

        // Try to handle $this->render() calls
        val renderTargets = handleRenderCall(psiElement, settings)
        if (renderTargets != null) {
            return renderTargets
        }

        // Try to handle $this->view = 'template' assignments (CakePHP 2)
        val viewFieldTargets = handleViewFieldAssignment(psiElement, settings)
        if (viewFieldTargets != null) {
            return viewFieldTargets
        }

        // Try to handle $this->viewBuilder()->setTemplate() (CakePHP 3+)
        val viewBuilderTargets = handleViewBuilderCall(psiElement, settings)
        if (viewBuilderTargets != null) {
            return viewBuilderTargets
        }

        return PsiElement.EMPTY_ARRAY
    }

    private fun handleRenderCall(psiElement: PsiElement, settings: Settings): Array<PsiElement>? {
        // Pattern: $this->render('template_name')
        // The literal may sit inside a ternary / match / parentheses in the first parameter.
        val stringLiteral = psiElement.context as? StringLiteralExpression ?: return null
        val argument = templateArgumentFromLiteral(stringLiteral) ?: return null
        val parameterList = argument.parent as? ParameterList ?: return null
        val method = parameterList.parent as? MethodReference ?: return null
        if (!RenderMethodPattern.accepts(method, ProcessingContext())) {
            return null
        }
        if (parameterList.parameters.getOrNull(0) != argument) {
            return null
        }

        // Navigate to the branch that was clicked, not every branch
        return navigateToViews(psiElement, settings, listOf(stringLiteral.contents))
    }

    /**
     * Walk up from a clicked string literal through ternary branches, match arm bodies and
     * parentheses to the outermost expression that forms the whole template argument.
     *
     * Returns null when the literal is in a position that is never a template name, such as
     * the condition of a ternary or the condition of a match arm.
     */
    private fun templateArgumentFromLiteral(stringLiteral: StringLiteralExpression): PsiElement? {
        var current: PsiElement = stringLiteral
        while (true) {
            val parent = current.parent ?: return null
            current = when (parent) {
                is TernaryExpression -> {
                    if (parent.condition == current) return null
                    parent
                }
                is ParenthesizedExpression -> parent
                is PhpMatchArm -> {
                    if (parent.bodyExpression != current) return null
                    // The arm's parent is the match expression itself
                    parent.parent ?: return null
                }
                else -> return current
            }
        }
    }

    private fun handleViewFieldAssignment(psiElement: PsiElement, settings: Settings): Array<PsiElement>? {
        // Pattern: $this->view = 'template_name'
        // We want to match when clicking on the string literal in the assignment.
        // The literal may sit inside a ternary / match / parentheses.
        val stringLiteral = psiElement.context as? StringLiteralExpression ?: return null
        val argument = templateArgumentFromLiteral(stringLiteral) ?: return null
        val assignment = argument.parent as? AssignmentExpression ?: return null
        if (assignment.value != argument) {
            return null
        }
        val fieldRef = assignment.variable as? FieldReference ?: return null

        // Check it's $this->view
        val variable = fieldRef.classReference as? Variable ?: return null
        if (variable.name != "this" || fieldRef.name != "view") {
            return null
        }

        val containingFile = psiElement.containingFile
        val virtualFile = containingFile.virtualFile
        val controllerPath = controllerPathFromControllerFile(virtualFile)
            ?: return null

        // Get the assigned view name
        val viewName = stringLiteral.contents

        val topSourceDirectory = topSourceDirectoryFromSourceFile(
            settings,
            containingFile
        ) ?: return null
        val allTemplatesPaths = allTemplatePathsFromTopSourceDirectory(
            psiElement.project,
            settings,
            topSourceDirectory
        ) ?: return null

        val resolution = resolveTemplateViewPaths(
            viewName,
            settings,
            controllerPath,
            allTemplatesPaths
        )
        return resolution.toFiles(psiElement.project, allTemplatesPaths).toTypedArray()
    }

    private fun handleViewBuilderCall(psiElement: PsiElement, settings: Settings): Array<PsiElement>? {
        // Patterns:
        // 1. $this->viewBuilder()->setTemplate('template_name')
        // 2. $this->viewBuilder()->setTemplatePath('path')->setTemplate('name')  (chained)
        // We want to match when clicking on the string literal.
        // The literal may sit inside a ternary / match / parentheses.
        val stringLiteral = psiElement.context as? StringLiteralExpression ?: return null
        val argument = templateArgumentFromLiteral(stringLiteral) ?: return null
        val parameterList = argument.parent as? ParameterList ?: return null
        val methodRef = parameterList.parent as? MethodReference ?: return null

        // Check if this is a setTemplate or setTemplatePath call
        val methodName = methodRef.name
        if (methodName != "setTemplate" && methodName != "setTemplatePath") {
            return null
        }

        val receiverMethodRef = methodRef.classReference as? MethodReference ?: return null
        val receiverMethodName = receiverMethodRef.name

        // Handle chained calls: ->setTemplatePath('path')->setTemplate('name')
        if (methodName == "setTemplate" && receiverMethodName == "setTemplatePath") {
            // User clicked on template in a chained call. The path may be a ternary / match.
            val pathValues = templateNamesFromExpression(receiverMethodRef.parameterList?.parameters?.getOrNull(0))
            if (pathValues.isEmpty()) return null

            // Verify chain goes back to viewBuilder()
            val viewBuilderRef = receiverMethodRef.classReference as? MethodReference ?: return null
            if (viewBuilderRef.name != "viewBuilder") return null
            val thisVar = viewBuilderRef.classReference as? Variable ?: return null
            if (thisVar.name != "this") return null

            // Navigate with combined paths
            val viewContents = stringLiteral.contents
            val viewNames = pathValues.map { "/" + joinViewPath(it, viewContents) }

            return navigateToViews(psiElement, settings, viewNames)
        }

        // Handle setTemplatePath in a chain (user clicked on the path)
        if (methodName == "setTemplatePath" && receiverMethodName == "viewBuilder") {
            val receiverVariable = receiverMethodRef.classReference as? Variable ?: return null
            if (receiverVariable.name != "this") return null

            // Check if this setTemplatePath is chained with a setTemplate
            val containingMethod = PsiTreeUtil.getParentOfType(
                methodRef,
                com.jetbrains.php.lang.psi.elements.Method::class.java
            )
            val chainedSetTemplate = findChainedSetTemplate(methodRef, containingMethod)

            if (chainedSetTemplate != null) {
                // This is chained - navigate to the final view(s). The template may be a ternary / match.
                val templateValues = templateNamesFromExpression(chainedSetTemplate.parameterList?.parameters?.getOrNull(0))
                if (templateValues.isEmpty()) return null
                val viewContents = stringLiteral.contents
                val viewNames = templateValues.map { "/" + joinViewPath(viewContents, it) }

                return navigateToViews(psiElement, settings, viewNames)
            }

            // Standalone setTemplatePath - not supported for goto-declaration
            return null
        }

        // Normal (non-chained) setTemplate call
        if (receiverMethodName != "viewBuilder") {
            return null
        }
        val receiverVariable = receiverMethodRef.classReference as? Variable ?: return null
        if (receiverVariable.name != "this") {
            return null
        }

        // For now, only handle setTemplate calls (not setTemplatePath)
        if (methodName != "setTemplate") {
            return null
        }

        // Get the template name or path, as well as
        // the previous `setTemplatePath call, if any (which may itself be a ternary / match):
        val viewContents = stringLiteral.contents
        val templatePaths = getTemplatePathsPreceding(stringLiteral)
        val viewNames = if (templatePaths.isNotEmpty()) {
            // Prepend "/" to make it absolute so the controller path is not prepended
            // Use joinViewPath to normalize the path (handles whitespace, slashes, etc.)
            templatePaths.map { "/" + joinViewPath(it, viewContents) }
        } else {
            listOf(viewContents)
        }

        return navigateToViews(psiElement, settings, viewNames)
    }

    /**
     * Find a setTemplate call that is chained with the given setTemplatePath call.
     *
     * Looks for: $this->viewBuilder()->setTemplatePath('path')->setTemplate('name')
     * where methodRef is the setTemplatePath call.
     */
    private fun findChainedSetTemplate(
        setTemplatePathRef: MethodReference,
        containingMethod: com.jetbrains.php.lang.psi.elements.Method?
    ): MethodReference? {
        if (containingMethod == null) return null

        val allMethodRefs = PsiTreeUtil.findChildrenOfType(containingMethod, MethodReference::class.java)

        return allMethodRefs.find { methodRef ->
            if (methodRef.name != "setTemplate") return@find false

            // Check if its receiver is our setTemplatePath
            val receiver = methodRef.classReference as? MethodReference ?: return@find false
            receiver == setTemplatePathRef
        }
    }

    /**
     * Helper to navigate to the view files with the given view names.
     */
    private fun navigateToViews(
        psiElement: PsiElement,
        settings: Settings,
        viewNames: List<String>
    ): Array<PsiElement>? {
        val containingFile = psiElement.containingFile
        val virtualFile = containingFile.virtualFile
        val controllerPath = controllerPathFromControllerFile(virtualFile)
            ?: return null

        val topSourceDirectory = topSourceDirectoryFromSourceFile(
            settings,
            containingFile
        ) ?: return null
        val allTemplatesPaths = allTemplatePathsFromTopSourceDirectory(
            psiElement.project,
            settings,
            topSourceDirectory
        ) ?: return null

        return viewNames.flatMap { viewName ->
            val resolution = resolveTemplateViewPaths(
                viewName,
                settings,
                controllerPath,
                allTemplatesPaths
            )
            resolution.toFiles(psiElement.project, allTemplatesPaths)
        }.distinct().toTypedArray()
    }

    /**
     * Find the template path values set by the closest preceding
     * `$this->viewBuilder()->setTemplatePath(...)` call in the same method.
     * Returns an empty list when there is none.
     */
    private fun getTemplatePathsPreceding(
        stringLiteral: StringLiteralExpression
    ): List<String> {
        // Find the containing method to limit our search scope
        val containingMethod = PsiTreeUtil.getParentOfType(
            stringLiteral,
            com.jetbrains.php.lang.psi.elements.Method::class.java
        ) ?: return emptyList()

        // Get the text offset of the current setTemplate call
        val currentOffset = stringLiteral.textRange.startOffset

        // Find all method references in the containing method
        val allMethodRefs = PsiTreeUtil.findChildrenOfType(
            containingMethod,
            MethodReference::class.java
        )

        // Filter for setTemplatePath calls on $this->viewBuilder()
        val setTemplatePathCalls = allMethodRefs.filter { methodRef ->
            // Check if this is a setTemplatePath call
            if (methodRef.name != "setTemplatePath") {
                return@filter false
            }

            // Check if the receiver is $this->viewBuilder()
            val receiverMethodRef = methodRef.classReference as? MethodReference ?: return@filter false
            if (receiverMethodRef.name != "viewBuilder") {
                return@filter false
            }
            val receiverVariable = receiverMethodRef.classReference as? Variable ?: return@filter false
            receiverVariable.name == "this"
        }

        // Find the closest preceding setTemplatePath call
        // (the one with the highest offset that's still less than currentOffset)
        var closestCall: MethodReference? = null
        var closestOffset = -1

        for (call in setTemplatePathCalls) {
            val callOffset = call.textRange.startOffset
            if (callOffset < currentOffset && callOffset > closestOffset) {
                closestCall = call
                closestOffset = callOffset
            }
        }

        // Extract the path value(s) from the parameter list
        if (closestCall != null) {
            val parameterList = closestCall.parameterList
            return templateNamesFromExpression(parameterList?.parameters?.getOrNull(0))
        }

        return emptyList()
    }

    override fun getActionText(dataContext: DataContext): String? = null
}
