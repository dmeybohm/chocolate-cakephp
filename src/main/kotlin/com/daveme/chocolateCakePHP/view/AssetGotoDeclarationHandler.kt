package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.AssetDirectory
import com.daveme.chocolateCakePHP.cake.assetDirectoryFromSourceFile
import com.daveme.chocolateCakePHP.findRelativeFile
import com.daveme.chocolateCakePHP.virtualFilesToPsiFiles
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.jetbrains.annotations.Nls
import java.util.HashSet

class AssetGotoDeclarationHandler : GotoDeclarationHandler {
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
        if (!settings.enabled) {
            return PsiElement.EMPTY_ARRAY
        }

        // When typing $table->find(' or $this->fetchTable("Movies"->find(', with a quote
        val stringLiteralPattern = psiElement(StringLiteralExpression::class.java)
            .withParent(
                psiElement(ParameterList::class.java)
                    .withParent(
                        psiElement(MethodReference::class.java)
                            .with(AssetMethodPattern)
                    )
            )
        if (!stringLiteralPattern.accepts(sourceElement.context)) {
            return PsiElement.EMPTY_ARRAY
        }
        val method = sourceElement.context?.parent?.parent as? MethodReference ?: return null
        val stringLiteralArg = sourceElement.context as? StringLiteralExpression ?: return null
        val assetDir = assetDirectoryFromSourceFile(
            settings,
            sourceElement.containingFile.virtualFile
        ) ?: return null
        val virtualFile = extractAssetPathFromMethodCall(assetDir, method, stringLiteralArg)
            ?: return null

        val files = HashSet<VirtualFile>()
        files.add(virtualFile)
        return virtualFilesToPsiFiles(project, files).toTypedArray()
    }

    override fun getActionText(context: DataContext): @Nls(capitalization = Nls.Capitalization.Title) String? = null

    fun extractAssetPathFromMethodCall(
        assetDir: AssetDirectory,
        method: MethodReference,
        stringArg: StringLiteralExpression
    ): VirtualFile? {
        val prefix = when (method.name) {
            "css" -> "css"
            "script" -> "js"
            "image" -> "img"
            else -> return null
        }
        return findRelativeFile(assetDir.directory, "${prefix}/${stringArg.contents}")
    }
}
