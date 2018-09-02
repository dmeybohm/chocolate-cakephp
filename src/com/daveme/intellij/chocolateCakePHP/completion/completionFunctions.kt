package com.daveme.intellij.chocolateCakePHP.completion

import com.daveme.intellij.chocolateCakePHP.util.chopFromEnd
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiDirectory
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.lang.psi.elements.PhpClass

fun completeFromFilesInDir(completionResultSet: CompletionResultSet,
                           appDir: PsiDirectory,
                           subDir: String,
                           insertHandler: InsertHandler<LookupElement>,
                           replaceName: String = "") {
    val psiSubdirectory = appDir.findSubdirectory(subDir) ?: return
    for (file in psiSubdirectory.files) {
        val virtualFile = file.virtualFile ?: continue
        val name = virtualFile.nameWithoutExtension
        val replaceText = name.chopFromEnd(replaceName)
        val lookupElement = LookupElementBuilder.create(replaceText)
                .withIcon(PhpIcons.FIELD)
                .withTypeText(name)
                .withInsertHandler(insertHandler)
        completionResultSet.addElement(lookupElement)
    }
}

fun completeFromClasses(
        completionResultSet: CompletionResultSet,
        classes: Collection<PhpClass>,
        replaceName: String = ""
) {
    for (klass in classes) {
        val replacedName = klass.name.chopFromEnd(replaceName)
        val lookupElement = LookupElementBuilder.create(replacedName)
                .withIcon(PhpIcons.FIELD)
                .withTypeText(klass.type.toString())
        completionResultSet.addElement(lookupElement)
    }
}