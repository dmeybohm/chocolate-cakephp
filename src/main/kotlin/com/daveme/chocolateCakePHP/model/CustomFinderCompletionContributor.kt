package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PatternCondition
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.lang.psi.elements.*

class CustomFinderCompletionContributor : CompletionContributor() {
    init {
        val methodMatcher = object : PatternCondition<MethodReference>("LocatorInterfaceGetCondition") {
            override fun accepts(methodReference: MethodReference, context: ProcessingContext): Boolean {
                if (!"find".equals(methodReference.name, ignoreCase = true)) {
                    return false
                }
                val settings =
                    Settings.getInstance(methodReference.project)
                if (!settings.cake3Enabled) {
                    return false
                }
                val classRefType = methodReference.classReference?.type ?: return false
                val type = if (classRefType.isComplete)
                    classRefType
                else {
                    val phpIndex = PhpIndex.getInstance(methodReference.project)
                    phpIndex.completeType(methodReference.project, classRefType, null)
                }
                return type.types.any { it.isTableClass() }
            }
        }

        val completionProvider = CustomFinderCompletionProvider()

        // When typing $table->find(' or $this->fetchTable("Movies"->find(', with a quote
        val stringLiteralPattern = psiElement(LeafPsiElement::class.java)
            .withParent(
                psiElement(StringLiteralExpression::class.java)
                    .withParent(
                        psiElement(ParameterList::class.java)
                            .withParent(
                                psiElement(MethodReference::class.java)
                                    .with(methodMatcher)
                            )
                    )
            )
        extend(
            CompletionType.BASIC,
            stringLiteralPattern,
            completionProvider,
        )
        extend(
            CompletionType.SMART,
            stringLiteralPattern,
            completionProvider,
        )
    }

    class CustomFinderCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            completionParameters: CompletionParameters,
            context: ProcessingContext,
            completionResultSet: CompletionResultSet
        ) {
            val methodReference = PsiTreeUtil.getParentOfType(
                completionParameters.position,
                MethodReference::class.java
            ) ?: return

            val settings =
                Settings.getInstance(methodReference.project)
            if (!settings.cake3Enabled) {
                return
            }
            // If the current element is not quoted, we need to quote it:
            // TODO
            val completeInsideString = completionParameters.position.parent is ConstantReference

            val phpIndex = PhpIndex.getInstance(methodReference.project)
            val classReference = methodReference.classReference ?: return
            val type = if (classReference.type.isComplete)
                classReference.type
            else
                phpIndex.completeType(methodReference.project, classReference.type, null)

            val tableClasses = type.types.filter {
                it.startsWith("\\") && it.isTableClass()
            }
            tableClasses.asSequence()
                .flatMap { className ->
                    phpIndex.getClassesByFQN(className)
                }
                .flatMap { klass ->
                    klass.methods
                }
                .filter { method ->
                    method.name.startsWith("find", ignoreCase = true)
                }
                .map { method ->
                        val targetName = method.name
                            .removeFromStart("find", ignoreCase = true)
                            .replaceFirstChar { it.lowercase() }
                        val lookupElement = LookupElementBuilder.create(targetName)
                            .withIcon(PhpIcons.PARAMETER)
                        completionResultSet.addElement(lookupElement)
                }
                .lastOrNull()

        }
    }
}