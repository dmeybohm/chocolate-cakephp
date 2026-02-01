package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.AssetDirectory
import com.daveme.chocolateCakePHP.cake.assetDirectoryFromViewFile
import com.daveme.chocolateCakePHP.cake.parsePluginResourcePath
import com.daveme.chocolateCakePHP.cake.rootDirectoryFromViewFile
import com.daveme.chocolateCakePHP.findPluginConfigByName
import com.daveme.chocolateCakePHP.findRelativeFile
import com.daveme.chocolateCakePHP.virtualFilesToPsiFiles
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
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
        if (DumbService.getInstance(sourceElement.project).isDumb) {
            return PsiElement.EMPTY_ARRAY
        }
        val project = sourceElement.project
        val settings = Settings.getInstance(project)
        if (!settings.enabled) {
            return PsiElement.EMPTY_ARRAY
        }

        // Check if either pattern matches (string literal or array element)
        if (!AssetMethodPatterns.stringForGotoDeclaration.accepts(sourceElement.context)
            && !AssetMethodPatterns.arrayForGotoDeclaration.accepts(sourceElement.context)) {
            return PsiElement.EMPTY_ARRAY
        }

        // Use PsiTreeUtil to find MethodReference regardless of nesting level
        val stringLiteralArg = sourceElement.context as? StringLiteralExpression ?: return null
        val method = PsiTreeUtil.getParentOfType(stringLiteralArg, MethodReference::class.java) ?: return null

        // Get the parameter list and check position - only navigate in the first parameter
        val parameterList = PsiTreeUtil.getParentOfType(stringLiteralArg, ParameterList::class.java) ?: return null
        val parameters = parameterList.parameters
        val paramIndex = parameters.indexOfFirst { param ->
            PsiTreeUtil.isAncestor(param, stringLiteralArg, false)
        }

        // Only navigate for the first parameter (index 0)
        if (paramIndex != 0) {
            return PsiElement.EMPTY_ARRAY
        }

        // Don't navigate on empty strings
        if (stringLiteralArg.contents.isEmpty()) {
            return PsiElement.EMPTY_ARRAY
        }

        val viewFile = sourceElement.containingFile.virtualFile
        val assetDir = assetDirectoryFromViewFile(
            sourceElement.project,
            settings,
            viewFile
        ) ?: return null
        val virtualFile = extractAssetPathFromMethodCall(
            project,
            settings,
            viewFile,
            assetDir,
            method,
            stringLiteralArg
        ) ?: return PsiElement.EMPTY_ARRAY

        val files = HashSet<VirtualFile>()
        files.add(virtualFile)
        return virtualFilesToPsiFiles(project, files).toTypedArray()
    }

    override fun getActionText(context: DataContext): String? = null

    fun extractAssetPathFromMethodCall(
        project: Project,
        settings: Settings,
        viewFile: VirtualFile,
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
        val extension = if (prefix == "img") "" else ".${prefix}"

        val assetPath = stringArg.contents
        val pluginResourcePath = parsePluginResourcePath(assetPath)

        // If there's a potential plugin prefix, check if it matches a configured plugin
        if (pluginResourcePath.pluginName != null) {
            val pluginConfig = settings.findPluginConfigByName(pluginResourcePath.pluginName)
            if (pluginConfig != null) {
                // This is a valid plugin prefix - resolve from plugin's webroot
                // Get the root directory from the view file's context
                // This ensures we resolve relative to the correct root (e.g., cake5/ in tests)
                val rootDir = rootDirectoryFromViewFile(project, settings, viewFile) ?: return null
                val pluginWebrootPath = "${pluginConfig.pluginPath}/${pluginConfig.assetPath}"
                val pluginWebroot = findRelativeFile(rootDir.directory, pluginWebrootPath)
                if (pluginWebroot != null) {
                    val pluginAssetFile = findRelativeFile(
                        pluginWebroot,
                        "${prefix}/${pluginResourcePath.resourcePath}${extension}"
                    )
                    if (pluginAssetFile != null) {
                        return pluginAssetFile
                    }
                }
                // Plugin exists but asset not found - don't fall back to main directory
                return null
            }
            // Plugin name not recognized - fall through to treat as normal path
            // (e.g., "pluginIcon.svg" where "pluginIcon" is not a plugin name)
        }

        // No plugin prefix or unrecognized plugin name - use main asset directory
        return findRelativeFile(assetDir.directory, "${prefix}/${assetPath}${extension}")
    }
}
