package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.virtualFilesToPsiFiles
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ContainGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        if (sourceElement == null) {
            return PsiElement.EMPTY_ARRAY
        }
        val project = sourceElement.project
        val settings = Settings.getInstance(project)
        if (!settings.cake3Enabled) {
            return PsiElement.EMPTY_ARRAY
        }
        if (DumbService.getInstance(project).isDumb) {
            return PsiElement.EMPTY_ARRAY
        }

        if (!ContainMethodPatterns.stringForGotoDeclaration.accepts(sourceElement.context)) {
            return PsiElement.EMPTY_ARRAY
        }
        val literal = sourceElement.context as? StringLiteralExpression ?: return null
        val position = associationPathPosition(literal) ?: return null

        // Find the segment under the caret, and the segments before it:
        val contentsStart = literal.textRange.startOffset + literal.valueRange.startOffset
        val segments = literal.contents.split('.')
        var segmentEnd = -1
        val caretIndex = segments.indexOfFirst { segment ->
            segmentEnd += segment.length + 1
            offset - contentsStart <= segmentEnd
        }
        val alias = segments.getOrNull(caretIndex) ?: return PsiElement.EMPTY_ARRAY
        if (alias.isEmpty()) {
            return PsiElement.EMPTY_ARRAY
        }

        val tables = AssociationPathResolver.walk(
            project,
            queryReceiverTables(position.method),
            position.enclosingSegments + segments.take(caretIndex)
        )
        val hasHops = position.enclosingSegments.isNotEmpty() || caretIndex > 0
        if (tables.isEmpty() && hasHops) {
            return PsiElement.EMPTY_ARRAY
        }
        val phpIndex = PhpIndex.getInstance(project)
        val tableClasses = AssociationPathResolver.targetTables(project, tables, alias)
            .flatMap { phpIndex.getClassesByFQN(it) }
        if (tableClasses.isEmpty()) {
            return PsiElement.EMPTY_ARRAY
        }

        // Convert to PsiElements
        val virtualFiles = tableClasses.mapNotNull { it.containingFile?.virtualFile }.toSet()
        return virtualFilesToPsiFiles(project, virtualFiles).toTypedArray()
    }

    override fun getActionText(context: DataContext): String? = null
}
