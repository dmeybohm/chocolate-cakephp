package com.daveme.chocolateCakePHP.completion

import com.daveme.chocolateCakePHP.util.chopFromEnd
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiDirectory
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.lang.psi.elements.PhpClass

fun completeFromFilesInDir(
    completionResultSet: CompletionResultSet,
    appDir: PsiDirectory,
    subDir: String,
    replaceName: String = "",
    insertHandler: InsertHandler<LookupElement>? = null
) {
    val psiSubdirectory = appDir.findSubdirectory(subDir) ?: return

    for (file in psiSubdirectory.files) {
        val virtualFile = file.virtualFile ?: continue
        val name = virtualFile.nameWithoutExtension
        val replaceText = name.chopFromEnd(replaceName)
        var lookupElement = LookupElementBuilder.create(replaceText)
            .withIcon(PhpIcons.FIELD)
            .withTypeText(name)
        if (insertHandler != null) {
            lookupElement = lookupElement.withInsertHandler(insertHandler)
        }
        completionResultSet.addElement(lookupElement)
    }
}

fun completeFromClasses(
    completionResultSet: CompletionResultSet,
    classes: Collection<PhpClass>,
    replaceName: String = ""
) {
    classes.map { klass ->
        val replacedName = klass.name.chopFromEnd(replaceName)
        val lookupElement = LookupElementBuilder.create(replacedName)
            .withIcon(PhpIcons.FIELD)
            .withTypeText(klass.type.toString())
        completionResultSet.addElement(lookupElement)
    }
}