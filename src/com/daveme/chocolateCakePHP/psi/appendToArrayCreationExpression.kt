package com.daveme.chocolateCakePHP.psi

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.IncorrectOperationException
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.PhpPsiElementFactory
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

fun appendToArrayCreationExpression(
    file: PhpFile,
    document: Document,
    valueToAdd: String,
    expr: ArrayCreationExpression
): Boolean {
    if (checkIfArrayHasValue(expr, valueToAdd)) {
        return true
    }
    val project = expr.project
    val newText = createLiteralString(project, valueToAdd) ?: return false
    val (lastArrayElement, nodeText) = lastArrayElementAndNodeText(expr.lastChild) ?: return false

    return try {
        val addingLen = appendToExpression(expr, lastArrayElement, nodeText, newText, document)
        val codeStyleManager = CodeStyleManager.getInstance(project)
        reformat(codeStyleManager, file, expr, addingLen)
        true
    }
    catch (e: IncorrectOperationException) {
        println("IncorrectOperationException")
        false
    }
}

private fun reformat(codeStyleManager: CodeStyleManager, file: PhpFile, expr: ArrayCreationExpression, addedLen: Int) {
    val exprTextRange = expr.textRange
    val end = exprTextRange.endOffset + addedLen
    codeStyleManager.reformatText(file, exprTextRange.startOffset, end)
}

private fun lastArrayElementAndNodeText(lastElement: PsiElement): Pair<PsiElement, String>? {
    var prevSibling = getPrevSiblingSkippingWhitespaceAndComments(lastElement)
            ?: return null
    val prevSiblingText = prevSibling.text
    if (prevSiblingText == ",") {
        prevSibling = getPrevSiblingSkippingWhitespaceAndComments(prevSibling) ?: return null
    }
    return Pair(prevSibling, prevSiblingText)
}

private fun appendToExpression(
    expr: ArrayCreationExpression,
    lastArrayElement: PsiElement,
    lastNodeText: String,
    newText: StringLiteralExpression,
    document: Document
): Int {
    var moreLen = 0
    if (lastNodeText != "(" && lastNodeText != "[") {
        val comma = PhpPsiElementFactory.createComma(expr.project)
        lastArrayElement.addAfter(comma, expr)
        moreLen += comma.text.length
        lastArrayElement.addAfter(newText, expr)
    }
    else {
        val lastElementTextRange = lastArrayElement.textRange
        document.insertString(lastElementTextRange.endOffset, newText.text)
    }
    return newText.text.length + moreLen
}

private fun checkIfArrayHasValue(expr: ArrayCreationExpression, value: String): Boolean {
    for (child in expr.children) {
        if (child !is PhpPsiElement) {
            continue
        }
        val firstPsiChild = child.firstPsiChild
        if (firstPsiChild is StringLiteralExpression) {
            if (value == firstPsiChild.contents) {
                return true
            }
        }
    }
    return false
}

private fun getPrevSiblingSkippingWhitespaceAndComments(element: PsiElement): PsiElement? {
    var nullableElement: PsiElement? = element
    while (nullableElement != null) {
        nullableElement = nullableElement.prevSibling
        if (nullableElement !is PsiWhiteSpace && nullableElement !is PsiComment) {
            break
        }
    }
    return nullableElement
}
