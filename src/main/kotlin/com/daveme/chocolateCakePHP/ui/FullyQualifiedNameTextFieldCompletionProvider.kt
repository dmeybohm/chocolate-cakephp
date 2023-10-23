package com.daveme.chocolateCakePHP.ui

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.completion.PhpCompletionUtil
import com.jetbrains.php.completion.PhpCompletionUtil.PhpFullyQualifiedNameTextFieldCompletionProvider

//
// Completion provider for the class text fields.
//
class FullyQualifiedNameTextFieldCompletionProvider(
    private val project: Project,
    private val handler: FullyQualifiedNameInsertHandler
) : PhpFullyQualifiedNameTextFieldCompletionProvider() {

    override fun addCompletionVariants(
        namespaceName: String,
        prefix: String,
        completionResultSet: CompletionResultSet
    ) {
        val phpIndex = PhpIndex.getInstance(project)
        PhpCompletionUtil.addSubNamespaces(project, namespaceName + "\\", completionResultSet, handler, false, phpIndex)
        completionResultSet.stopHere()
    }

}