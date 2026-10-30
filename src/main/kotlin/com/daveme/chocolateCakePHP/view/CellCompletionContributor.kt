package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.cellClassFqn
import com.daveme.chocolateCakePHP.cake.cellNameFromClassFqn
import com.daveme.chocolateCakePHP.cake.cellNamespaces
import com.daveme.chocolateCakePHP.cake.parseCellName
import com.daveme.chocolateCakePHP.getCellSubclasses
import com.daveme.chocolateCakePHP.isCustomizableViewMethod
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class CellCompletionContributor : CompletionContributor() {

    init {
        val completionProvider = CellCompletionProvider()

        // $this->cell('<caret>')
        val stringLiteralPattern = psiElement(LeafPsiElement::class.java)
            .withParent(
                psiElement(StringLiteralExpression::class.java)
                    .withParent(
                        psiElement(ParameterList::class.java)
                            .withParent(
                                psiElement(MethodReference::class.java)
                                    .with(CellMethodPattern)
                            )
                    )
            )
        extend(CompletionType.BASIC, stringLiteralPattern, completionProvider)
        extend(CompletionType.SMART, stringLiteralPattern, completionProvider)
    }

    class CellCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            completionParameters: CompletionParameters,
            context: ProcessingContext,
            completionResultSet: CompletionResultSet
        ) {
            val stringLiteral = completionParameters.position.parent as? StringLiteralExpression ?: return
            val parameterList = stringLiteral.parent as? ParameterList ?: return
            if (parameterList.parameters.getOrNull(0) != stringLiteral) {
                return
            }
            val project = stringLiteral.project
            if (DumbService.getInstance(project).isDumb) {
                return
            }
            val settings = Settings.getInstance(project)
            if (!settings.cake3Enabled) {
                return
            }

            // The cell string has `.`, `/` and `::` separators, so match against all of it
            val valueRange = stringLiteral.valueRange
            val caretInLiteral = (completionParameters.offset - stringLiteral.textRange.startOffset)
                .coerceIn(valueRange.startOffset, valueRange.endOffset)
            val prefix = stringLiteral.text.substring(valueRange.startOffset, caretInLiteral)
            val resultSet = completionResultSet.withPrefixMatcher(prefix)
            val phpIndex = PhpIndex.getInstance(project)

            if (prefix.contains("::")) {
                val pluginAndName = prefix.substringBefore("::")
                val cellName = parseCellName(pluginAndName) ?: return
                val classes = phpIndex.getClassesByFQN(cellClassFqn(settings, cellName))
                classes.flatMap { it.methods }
                    .filter { it.isCustomizableViewMethod() && !it.isStatic }
                    .distinctBy { it.name }
                    .forEach { method ->
                        resultSet.addElement(
                            LookupElementBuilder.create("${pluginAndName}::${method.name}")
                                .withPresentableText(method.name)
                                .withIcon(PhpIcons.METHOD)
                                .withTypeText(method.containingClass?.name)
                        )
                    }
                return
            }

            val namespaces = cellNamespaces(settings)
            for (cellClass in phpIndex.getCellSubclasses(settings)) {
                for ((plugin, namespace) in namespaces) {
                    val name = cellNameFromClassFqn(namespace, cellClass.fqn) ?: continue
                    val lookupString = if (plugin == null) name else "${plugin}.${name}"
                    resultSet.addElement(
                        LookupElementBuilder.create(lookupString)
                            .withIcon(PhpIcons.CLASS)
                            .withTypeText(cellClass.fqn.removePrefix("\\"))
                    )
                }
            }
        }
    }

}
