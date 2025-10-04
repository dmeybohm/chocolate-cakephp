package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.assetDirectoryFromViewFile
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.guessProjectDir
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class AssetCompletionContributor : CompletionContributor() {
    init {
        val stringLiteralPattern = psiElement(LeafPsiElement::class.java)
            .withParent(
                psiElement(StringLiteralExpression::class.java)
                    .withParent(
                        psiElement(ParameterList::class.java)
                            .withParent(
                                psiElement(MethodReference::class.java)
                                    .with(AssetMethodPattern)
                            )
                    )
            )

        extend(
            CompletionType.BASIC,
            stringLiteralPattern,
            AssetCompletionProvider()
        )
    }

    class AssetCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val position = parameters.position
            val project = position.project
            val settings = Settings.getInstance(project)

            if (!settings.enabled) {
                return
            }

            // Get the method reference to determine which asset type
            val method = PsiTreeUtil.getParentOfType(position, MethodReference::class.java) ?: return
            val methodName = method.name ?: return

            // Get the asset directory
            val assetDir = assetDirectoryFromViewFile(
                project,
                settings,
                parameters.originalFile.virtualFile
            ) ?: return

            // Determine subdirectory and extension based on method name
            val (subdirectory, extension, stripExtension) = when (methodName.lowercase()) {
                "css" -> Triple("css", ".css", true)
                "script" -> Triple("js", ".js", true)
                "image" -> Triple("img", "", false)
                else -> return
            }

            // Scan the asset directory
            val assetSubdir = assetDir.directory.findChild(subdirectory)
            if (assetSubdir != null) {
                for (file in assetSubdir.children) {
                    if (!file.isDirectory && !file.name.startsWith(".")) {
                        val displayName = if (stripExtension && file.name.endsWith(extension)) {
                            file.name.substring(0, file.name.length - extension.length)
                        } else {
                            file.name
                        }

                        val lookupElement = LookupElementBuilder.create(displayName as Any)
                            .withIcon(file.fileType.icon)
                            .withTypeText(file.name)

                        result.addElement(lookupElement)
                    }
                }
            }

            // Also scan plugin and theme assets if configured
            val projectDir = project.guessProjectDir()
            if (projectDir != null) {
                for (config in settings.pluginAndThemeConfigs) {
                    val pluginWebrootPath = "${config.pluginPath}/${config.assetPath}/$subdirectory"
                    val pluginWebroot = projectDir.findFileByRelativePath(pluginWebrootPath)
                    if (pluginWebroot != null) {
                        for (file in pluginWebroot.children) {
                            if (!file.isDirectory && !file.name.startsWith(".")) {
                                val displayName = if (stripExtension && file.name.endsWith(extension)) {
                                    file.name.substring(0, file.name.length - extension.length)
                                } else {
                                    file.name
                                }

                                val lookupElement = LookupElementBuilder.create(displayName as Any)
                                    .withIcon(file.fileType.icon)
                                    .withTypeText(file.name)

                                result.addElement(lookupElement)
                            }
                        }
                    }
                }
            }
        }
    }
}
