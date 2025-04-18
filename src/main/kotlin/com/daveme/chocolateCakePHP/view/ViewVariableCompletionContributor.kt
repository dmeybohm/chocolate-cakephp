package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexService
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndexService
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.elements.ConstantReference

class ViewVariableCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(ConstantReference::class.java),
            ViewVariableCompletionProvider()
        )
        extend(
            CompletionType.SMART,
            PlatformPatterns.psiElement().withParent(ConstantReference::class.java),
            ViewVariableCompletionProvider()
        )
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(Variable::class.java),
            ViewVariableCompletionProvider()
        )
        extend(
            CompletionType.SMART,
            PlatformPatterns.psiElement().withParent(Variable::class.java),
            ViewVariableCompletionProvider()
        )
    }

    class ViewVariableCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val element = parameters.position
            val project = element.project
            val settings = Settings.getInstance(project)
            if (!settings.enabled) {
                return
            }
            val psiFile = element.containingFile
                ?: return
            val templatesDir = templatesDirectoryOfViewFile(project, settings, psiFile)
                ?: return

            val path = psiFile.originalFile.virtualFile.path
            val filenameKey = ViewFileIndexService.canonicalizeFilenameToKey(
                templatesDir,
                settings,
                path
            )
            val viewVarValues = ViewVariableIndexService.lookupVariablesFromViewPath(
                project,
                settings,
                filenameKey
            )
            val phpIndex = PhpIndex.getInstance(project)
            viewVarValues.forEach { (viewVarName, viewVarType) ->
                val phpType = viewVarType.phpType.lookupCompleteType(
                    project,
                    phpIndex,
                    null
                ).filterUnknown()
                var completion = LookupElementBuilder.create("${'$'}${viewVarName}")
                    .withIcon(PhpIcons.VARIABLE)
                if (phpType.types.size > 0) {
                    completion = completion.withTypeText(phpType.toString())
                }
                result.addElement(completion)
            }
        }
    }

}
