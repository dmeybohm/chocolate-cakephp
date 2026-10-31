package com.daveme.chocolateCakePHP

import com.daveme.chocolateCakePHP.configuredmemberindex.MemberTypeResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.lang.psi.elements.PhpClass

class CompleteStringParameter(
    private val advanceBeyondClosingParen: Boolean = false
) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        val lookupString = item.lookupString

        document.replaceString(
            context.startOffset,
            context.tailOffset,
            lookupString
        )

        context.editor.caretModel.moveToOffset(context.tailOffset)
        val elementStart = context.startOffset - 1

        // Add close quote that matches the opening quote:
        val startChar = if (elementStart >= 0)
            document.charsSequence[elementStart]
        else
            null
        val closeQuote = when (startChar) {
            '\'' -> "'"
            '"' ->  "\""
            else -> null
        }

        var nextTailOffset = context.startOffset + lookupString.length
        if (closeQuote != null) {
            nextTailOffset++
        }

        if (advanceBeyondClosingParen && document.textLength > nextTailOffset) {
            val advancedChar = document.charsSequence[nextTailOffset]
            if (advancedChar == ')' || advancedChar == ',') {
                // advance past the closing parenthesis/comma
                nextTailOffset++
            }
        }

        // Go one char after the comma or parentheses
        context.editor.caretModel.moveToOffset(nextTailOffset)
    }
}

fun CompletionResultSet.completeFromClasses(
    classes: Collection<PhpClass>,
    removeFromEnd: String = "",
    containingClasses: List<PhpClass> = arrayListOf(),
) {
    classes.map { klass ->
        val targetName = klass.name.removeFromEnd(removeFromEnd, ignoreCase = true)
        if (hasFieldAlready(containingClasses, targetName)) {
            return@map
        }
        val lookupElement = LookupElementBuilder.create(targetName)
                .withIcon(PhpIcons.FIELD)
                .withTypeText(klass.type.toString().substring(1))
        this.addElement(lookupElement)
    }
}

fun CompletionResultSet.completeFromLoadedMembers(
    members: Map<String, MemberTypeResult>,
    containingClasses: List<PhpClass> = arrayListOf(),
) {
    for ((alias, member) in members) {
        if (hasFieldAlready(containingClasses, alias)) {
            continue
        }
        val lookupElement = LookupElementBuilder.create(alias)
                .withIcon(PhpIcons.FIELD)
                .withTypeText(member.classFqns.joinToString("|") { it.removePrefix("\\") })
        this.addElement(lookupElement)
    }
}

private fun hasFieldAlready(containingClasses: List<PhpClass>, propertyName: String): Boolean =
    containingClasses.any {
        val hasField = it.findFieldByName(propertyName, true) != null
        if (hasField) { return@any true }
        val docComment = it.docComment ?: return@any false
        // todo: filter private properties
        return@any docComment.propertyTags.any {
            it.property?.name == propertyName
        }
    }