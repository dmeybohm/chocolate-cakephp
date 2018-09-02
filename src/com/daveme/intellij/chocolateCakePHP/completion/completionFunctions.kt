package com.daveme.intellij.chocolateCakePHP.completion

import com.daveme.intellij.chocolateCakePHP.util.chopFromEnd
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiDirectory
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.completion.PhpVariantsUtil
import com.jetbrains.php.completion.UsageContext
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpModifier

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
        val helperNameAsPropertyName = klass.name.chopFromEnd(replaceName)
        val lookupElement = LookupElementBuilder.create(helperNameAsPropertyName)
                .withIcon(PhpIcons.FIELD)
                .withTypeText(klass.type.toString())
        completionResultSet.addElement(lookupElement)
    }
}

fun complete(
        classes: Collection<PhpClass>,
        fromClass: PhpClass,
        completionResultSet: CompletionResultSet) {
    if (classes.isEmpty()) {
        return
    }
    val usageContext = UsageContext(PhpModifier.State.DYNAMIC)
    usageContext.targetObjectClass = fromClass
    for (klass in classes) {
        try {
            val lookupItems = PhpVariantsUtil.getLookupItems(klass.methods, false, usageContext)
            completionResultSet.addAllElements(lookupItems)
        } catch (e: Exception) {

        }

        try {
            val lookupElements = PhpVariantsUtil.getLookupItems(klass.fields, false, usageContext)
            completionResultSet.addAllElements(lookupElements)
        } catch (e: Exception) {

        }

    }
}