package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.*
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class CellGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(psiElement: PsiElement?, i: Int, editor: Editor): Array<PsiElement>? {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        if (DumbService.getInstance(psiElement.project).isDumb) {
            return PsiElement.EMPTY_ARRAY
        }
        val project = psiElement.project
        val settings = Settings.getInstance(project)
        if (!settings.cake3Enabled) {
            return PsiElement.EMPTY_ARRAY
        }

        val stringLiteral = psiElement.context as? StringLiteralExpression ?: return PsiElement.EMPTY_ARRAY
        val argument = templateArgumentFromLiteral(stringLiteral) ?: return PsiElement.EMPTY_ARRAY
        val parameterList = argument.parent as? ParameterList ?: return PsiElement.EMPTY_ARRAY
        val methodRef = parameterList.parent as? MethodReference ?: return PsiElement.EMPTY_ARRAY
        if (parameterList.parameters.getOrNull(0) != argument) {
            return PsiElement.EMPTY_ARRAY
        }
        if (methodRef.name.equals("render", ignoreCase = true)) {
            return cellTemplateTargets(settings, psiElement, methodRef, stringLiteral.contents)
        }
        if (!CellMethodPattern.accepts(methodRef, ProcessingContext())) {
            return PsiElement.EMPTY_ARRAY
        }

        val cellName = parseCellName(stringLiteral.contents) ?: return PsiElement.EMPTY_ARRAY
        val classes = PhpIndex.getInstance(project).getClassesByFQN(cellClassFqn(settings, cellName))
        val action = cellName.action
        if (action != null) {
            val methods = classes.mapNotNull { it.findMethodByName(action) }
            if (methods.isNotEmpty()) {
                return methods.toTypedArray()
            }
        }
        return classes.toTypedArray()
    }

    // $this->cell('Inventory')->render('compact'), or render() on a variable assigned a cell()
    private fun cellTemplateTargets(
        settings: Settings,
        psiElement: PsiElement,
        renderRef: MethodReference,
        template: String,
    ): Array<PsiElement> {
        val cellNames = cellCallsFromReceiver(renderRef.classReference)
            .flatMap { templateNamesFromExpression(it.parameterList?.parameters?.getOrNull(0)) }
            .mapNotNull { parseCellName(it) }
            .distinct()
        if (cellNames.isEmpty()) {
            return PsiElement.EMPTY_ARRAY
        }
        val project = psiElement.project
        val topSourceDirectory = topSourceDirectoryFromSourceFile(settings, psiElement.containingFile)
            ?: return PsiElement.EMPTY_ARRAY
        val allTemplatesPaths = allTemplatePathsFromTopSourceDirectory(project, settings, topSourceDirectory)
            ?: return PsiElement.EMPTY_ARRAY
        return cellNames.flatMap { cellName ->
            val allViewPaths = allViewPathsFromCellTemplate(
                allTemplatesPaths, settings, cellName, template, convertCase = false
            )
            TemplatePathResolution(allViewPaths, cellPluginLookupResult(settings, cellName))
                .toFiles(project, allTemplatesPaths)
        }.distinct().toTypedArray()
    }

    override fun getActionText(dataContext: DataContext): String? = null
}
