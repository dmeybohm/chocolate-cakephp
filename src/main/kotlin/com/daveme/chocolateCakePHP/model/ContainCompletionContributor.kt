package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.Settings
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex

class ContainCompletionContributor : CompletionContributor() {
    init {
        // Pattern for string literal directly in parameter list: $query->contain('Authors')
        extend(
            CompletionType.BASIC,
            ContainMethodPatterns.stringForCompletion,
            ContainCompletionProvider()
        )

        // Pattern for string literal inside array: $query->contain(['Authors', 'Comments'])
        extend(
            CompletionType.BASIC,
            ContainMethodPatterns.arrayForCompletion,
            ContainCompletionProvider()
        )
    }

    class ContainCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            completionParameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val position = completionParameters.position
            val project = position.project
            val settings = Settings.getInstance(project)

            if (!settings.cake3Enabled) {
                return
            }
            if (DumbService.getInstance(project).isDumb) {
                return
            }

            val phpIndex = PhpIndex.getInstance(project)

            // Get all Table subclasses
            val tableBaseClass = "\\Cake\\ORM\\Table"
            val tableClasses = phpIndex.getAllSubclasses(tableBaseClass)

            // Collect allowed namespace prefixes: app + all plugins
            val allowedPrefixes = mutableListOf(settings.appNamespace)
            settings.pluginConfigs.forEach { allowedPrefixes.add(it.namespace) }

            tableClasses.forEach { phpClass ->
                val fqn = phpClass.fqn
                if (allowedPrefixes.any { fqn.startsWith(it) }) {
                    val className = phpClass.name
                    val tableName = className.removeSuffix("Table")

                    if (tableName.isNotEmpty() && tableName != className) {
                        val lookupElement = LookupElementBuilder.create(tableName)
                            .withTypeText("Table")
                        result.addElement(lookupElement)
                    }
                }
            }
        }
    }
}
