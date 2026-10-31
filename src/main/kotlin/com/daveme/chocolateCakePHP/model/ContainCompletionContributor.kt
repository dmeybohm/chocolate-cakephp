package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ContainCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            ContainMethodPatterns.stringForCompletion,
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

            val literal = PsiTreeUtil.getParentOfType(position, StringLiteralExpression::class.java) ?: return
            val pathPosition = associationPathPosition(literal) ?: return

            val contentsStart = literal.textRange.startOffset + literal.valueRange.startOffset
            val typed = literal.contents.take(completionParameters.offset - contentsStart).split('.')
            val hops = pathPosition.enclosingSegments + typed.dropLast(1)
            val prefixed = result.withPrefixMatcher(typed.last())

            val tables = AssociationPathResolver.walk(project, queryReceiverTables(pathPosition.method), hops)
            if (tables.isEmpty() && hops.isNotEmpty()) {
                return
            }
            val loaded = ConfiguredMemberService.loadedMembers(project, tables, setOf(MemberKind.ASSOCIATION))
            for ((alias, member) in loaded.members) {
                val tableNames = member.classFqns.joinToString("|") { it.substringAfterLast('\\') }
                prefixed.addElement(LookupElementBuilder.create(alias).withTypeText(tableNames))
            }
            if (MemberKind.ASSOCIATION !in loaded.initializeKinds) {
                addAllTables(prefixed, settings, PhpIndex.getInstance(project), loaded.members.keys)
            }
        }

        private fun addAllTables(
            result: CompletionResultSet,
            settings: Settings,
            phpIndex: PhpIndex,
            excludedNames: Set<String>
        ) {
            val tableClasses = phpIndex.getAllSubclasses("\\Cake\\ORM\\Table")

            // Collect allowed namespace prefixes: app + all plugins
            val allowedPrefixes = mutableListOf(settings.appNamespace)
            settings.cakeThreePluginConfigs.forEach { allowedPrefixes.add(it.namespace) }

            tableClasses.forEach { phpClass ->
                val fqn = phpClass.fqn
                if (allowedPrefixes.any { fqn.startsWith(it) }) {
                    val className = phpClass.name
                    val tableName = className.removeSuffix("Table")

                    if (tableName.isNotEmpty() && tableName != className && tableName !in excludedNames) {
                        result.addElement(LookupElementBuilder.create(tableName).withTypeText("Table"))
                    }
                }
            }
        }
    }
}
