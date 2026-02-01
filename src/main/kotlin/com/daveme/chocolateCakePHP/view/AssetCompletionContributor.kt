package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.PluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.assetDirectoryFromViewFile
import com.daveme.chocolateCakePHP.cake.rootDirectoryFromViewFile
import com.daveme.chocolateCakePHP.findRelativeFile
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class AssetCompletionContributor : CompletionContributor() {
    init {
        // Pattern for string literal directly in parameter list: $this->Html->css('movie')
        extend(
            CompletionType.BASIC,
            AssetMethodPatterns.stringForCompletion,
            AssetCompletionProvider()
        )

        // Pattern for string literal inside array: $this->Html->css(['movie', 'forms'])
        extend(
            CompletionType.BASIC,
            AssetMethodPatterns.arrayForCompletion,
            AssetCompletionProvider()
        )
    }

    class AssetCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            completionParameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val position = completionParameters.position
            val project = position.project
            val settings = Settings.getInstance(project)

            if (!settings.enabled) {
                return
            }

            // Get the method reference to determine which asset type
            val method = PsiTreeUtil.getParentOfType(position, MethodReference::class.java) ?: return
            val methodName = method.name ?: return

            // Get the string literal element
            val stringLiteral = PsiTreeUtil.getParentOfType(position, StringLiteralExpression::class.java) ?: return

            // Get the parameter list
            val parameterList = PsiTreeUtil.getParentOfType(stringLiteral, ParameterList::class.java) ?: return

            // Find which parameter this is - only complete in the first parameter
            val parameters = parameterList.parameters
            val paramIndex = parameters.indexOfFirst { param ->
                PsiTreeUtil.isAncestor(param, stringLiteral, false)
            }

            // Only provide completions for the first parameter (index 0)
            if (paramIndex != 0) {
                return
            }

            // Get the asset directory
            val virtualFile = completionParameters.originalFile.virtualFile ?: return
            val assetDir = assetDirectoryFromViewFile(
                project,
                settings,
                virtualFile
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
            // Use root directory from view file context to resolve plugin paths correctly
            val rootDir = rootDirectoryFromViewFile(project, settings, virtualFile)
            if (rootDir != null) {
                for (config in settings.pluginAndThemeConfigs) {
                    val pluginWebrootPath = "${config.pluginPath}/${config.assetPath}/$subdirectory"
                    val pluginWebroot = findRelativeFile(rootDir.directory, pluginWebrootPath)
                    if (pluginWebroot != null) {
                        // Extract plugin name from namespace (for PluginConfig) or use path for themes
                        val pluginName = when (config) {
                            is PluginConfig -> extractPluginNameFromNamespace(config.namespace)
                            else -> null
                        }

                        for (file in pluginWebroot.children) {
                            if (!file.isDirectory && !file.name.startsWith(".")) {
                                val baseName = if (stripExtension && file.name.endsWith(extension)) {
                                    file.name.substring(0, file.name.length - extension.length)
                                } else {
                                    file.name
                                }

                                // For plugins, create completion with plugin prefix (e.g., "MyPlugin.stylesheet")
                                // For themes (no pluginName), use just the base name
                                val displayName = if (pluginName != null) {
                                    "$pluginName.$baseName"
                                } else {
                                    baseName
                                }

                                val lookupElement = LookupElementBuilder.create(displayName as Any)
                                    .withIcon(file.fileType.icon)
                                    .withTypeText(file.name)
                                    .withTailText(if (pluginName != null) " (plugin)" else null, true)

                                result.addElement(lookupElement)
                            }
                        }
                    }
                }
            }
        }

        /**
         * Extracts the plugin name from a namespace.
         *
         * Examples:
         * - "\MyPlugin" -> "MyPlugin"
         * - "\App\MyPlugin" -> "MyPlugin"
         * - "MyPlugin" -> "MyPlugin"
         * - "" -> null
         */
        private fun extractPluginNameFromNamespace(namespace: String): String? {
            if (namespace.isEmpty()) {
                return null
            }
            val cleanNamespace = namespace.removePrefix("\\")
            val lastBackslash = cleanNamespace.lastIndexOf('\\')
            return if (lastBackslash >= 0) {
                cleanNamespace.substring(lastBackslash + 1)
            } else {
                cleanNamespace
            }
        }
    }
}
