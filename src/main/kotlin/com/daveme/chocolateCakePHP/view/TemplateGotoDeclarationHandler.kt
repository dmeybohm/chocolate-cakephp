package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.*
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

class TemplateGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        psiElement: PsiElement?,
        i: Int,
        editor: Editor
    ): Array<PsiElement>? {
        if (psiElement == null) {
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
        val stringLiteralPattern = psiElement(StringLiteralExpression::class.java)
            .withParent(
                psiElement(ParameterList::class.java)
                    .withParent(
                        psiElement(MethodReference::class.java)
                            .with(RenderMethodPattern)
                    )
            )
        if (!stringLiteralPattern.accepts(psiElement.context)) {
            return null
        }
        val containingFile = psiElement.containingFile
        val virtualFile = containingFile.virtualFile
        val controllerPath = controllerPathFromControllerFile(virtualFile)
            ?: return null

        val method = findParentWithClass(psiElement, MethodReference::class.java)
                as? MethodReference ?: return null
        if (method.name != "render") {
            return null
        }

        val actionNames = actionNamesFromRenderCall(method)
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

        val allViewPaths = allViewPathsFromController(
            controllerPath,
            allTemplatesPaths,
            settings,
            actionNames
        )
        val files = viewFilesFromAllViewPaths(
            project = psiElement.project,
            allTemplatesPaths = allTemplatesPaths,
            allViewPaths = allViewPaths
        )
        return files.toTypedArray()
    }

    private fun handleViewFieldAssignment(psiElement: PsiElement, settings: Settings): Array<PsiElement>? {
        // Pattern: $this->view = 'template_name'
        // We want to match when clicking on the string literal in the assignment
        val stringLiteral = psiElement.context as? StringLiteralExpression ?: return null
        val assignment = stringLiteral.parent as? AssignmentExpression ?: return null
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
        val actionName = actionNameFromPath(viewName)
        val actionNames = ActionNames(defaultActionName = actionName)

        val topSourceDirectory = topSourceDirectoryFromSourceFile(
            settings,
            containingFile
        ) ?: return null
        val allTemplatesPaths = allTemplatePathsFromTopSourceDirectory(
            psiElement.project,
            settings,
            topSourceDirectory
        ) ?: return null

        val allViewPaths = allViewPathsFromController(
            controllerPath,
            allTemplatesPaths,
            settings,
            actionNames
        )
        val files = viewFilesFromAllViewPaths(
            project = psiElement.project,
            allTemplatesPaths = allTemplatesPaths,
            allViewPaths = allViewPaths
        )
        return files.toTypedArray()
    }

    private fun handleViewBuilderCall(psiElement: PsiElement, settings: Settings): Array<PsiElement>? {
        // Patterns:
        // 1. $this->viewBuilder()->setTemplate('template_name')
        // 2. $this->viewBuilder()->setTemplatePath('path')->setTemplate('name')  (chained)
        // We want to match when clicking on the string literal
        val stringLiteral = psiElement.context as? StringLiteralExpression ?: return null
        val parameterList = stringLiteral.parent as? ParameterList ?: return null
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
            // User clicked on template in a chained call
            val pathParam = receiverMethodRef.parameterList?.parameters?.getOrNull(0) as? StringLiteralExpression
                ?: return null

            // Verify chain goes back to viewBuilder()
            val viewBuilderRef = receiverMethodRef.classReference as? MethodReference ?: return null
            if (viewBuilderRef.name != "viewBuilder") return null
            val thisVar = viewBuilderRef.classReference as? Variable ?: return null
            if (thisVar.name != "this") return null

            // Navigate with combined path
            val viewContents = stringLiteral.contents
            val viewName = "/${pathParam.contents}/${viewContents}"

            return navigateToView(psiElement, settings, viewName)
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
                // This is chained - navigate to the final view
                val templateParam = chainedSetTemplate.parameterList?.parameters?.getOrNull(0) as? StringLiteralExpression
                    ?: return null
                val viewContents = stringLiteral.contents
                val viewName = "/${viewContents}/${templateParam.contents}"

                return navigateToView(psiElement, settings, viewName)
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

        val containingFile = psiElement.containingFile
        val virtualFile = containingFile.virtualFile
        val controllerPath = controllerPathFromControllerFile(virtualFile)
            ?: return null

        // Get the template name or path, as well as
        // the previous `setTemplatePath call, if any:
        val viewContents = stringLiteral.contents
        val templatePathLiteral = getTemplatePathPreceeding(stringLiteral)
        val viewName = if (templatePathLiteral != null) {
            // Prepend "/" to make it absolute so the controller path is not prepended
            "/${templatePathLiteral.contents}/${viewContents}"
        } else {
            viewContents
        }

        // For now, only handle setTemplate calls (not setTemplatePath)
        if (methodName != "setTemplate") {
            return null
        }

        val actionName = actionNameFromPath(viewName)
        val actionNames = ActionNames(defaultActionName = actionName)

        val topSourceDirectory = topSourceDirectoryFromSourceFile(
            settings,
            containingFile
        ) ?: return null
        val allTemplatesPaths = allTemplatePathsFromTopSourceDirectory(
            psiElement.project,
            settings,
            topSourceDirectory
        ) ?: return null

        val allViewPaths = allViewPathsFromController(
            controllerPath,
            allTemplatesPaths,
            settings,
            actionNames
        )
        val files = viewFilesFromAllViewPaths(
            project = psiElement.project,
            allTemplatesPaths = allTemplatesPaths,
            allViewPaths = allViewPaths
        )
        return files.toTypedArray()
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
     * Helper to navigate to a view file with the given view name.
     */
    private fun navigateToView(
        psiElement: PsiElement,
        settings: Settings,
        viewName: String
    ): Array<PsiElement>? {
        val containingFile = psiElement.containingFile
        val virtualFile = containingFile.virtualFile
        val controllerPath = controllerPathFromControllerFile(virtualFile)
            ?: return null

        val actionName = actionNameFromPath(viewName)
        val actionNames = ActionNames(defaultActionName = actionName)

        val topSourceDirectory = topSourceDirectoryFromSourceFile(
            settings,
            containingFile
        ) ?: return null
        val allTemplatesPaths = allTemplatePathsFromTopSourceDirectory(
            psiElement.project,
            settings,
            topSourceDirectory
        ) ?: return null

        val allViewPaths = allViewPathsFromController(
            controllerPath,
            allTemplatesPaths,
            settings,
            actionNames
        )
        val files = viewFilesFromAllViewPaths(
            project = psiElement.project,
            allTemplatesPaths = allTemplatesPaths,
            allViewPaths = allViewPaths
        )
        return files.toTypedArray()
    }

    private fun getTemplatePathPreceeding(
        stringLiteral: StringLiteralExpression
    ): StringLiteralExpression? {
        // Find the containing method to limit our search scope
        val containingMethod = PsiTreeUtil.getParentOfType(
            stringLiteral,
            com.jetbrains.php.lang.psi.elements.Method::class.java
        ) ?: return null

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

        // Extract the string literal from the parameter list
        if (closestCall != null) {
            val parameterList = closestCall.parameterList
            val firstParam = parameterList?.parameters?.getOrNull(0)
            return firstParam as? StringLiteralExpression
        }

        return null
    }

    override fun getActionText(dataContext: DataContext): String? = null
}

