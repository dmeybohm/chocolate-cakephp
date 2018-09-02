package com.daveme.intellij.chocolateCakePHP.psi

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

fun appendToArrayCreationExpression(file: PhpFile, document: Document, valueToAdd: String, expr: ArrayCreationExpression): Boolean {
    if (checkIfArrayHasValue(expr, valueToAdd)) {
        return true
    }
    val fromText = createLiteralString(expr.project, valueToAdd) ?: return false
    val (prevSibling, prevSiblingText) = getLastElementOfArrayAndText(expr.lastChild) ?: return false

    return try {
        val addingLen = appendToExpression(expr, prevSiblingText, prevSibling, fromText, document)
        reformat(expr, addingLen, file)
        true
    } catch (e: IncorrectOperationException) {
        println("IncorrectOperationException")
        false
    }
}

private fun reformat(expr: ArrayCreationExpression, extraLen: Int, file: PhpFile) {
    val exprTextRange = expr.textRange
    val end = exprTextRange.endOffset + extraLen
    val codeStyleManager = CodeStyleManager.getInstance(expr.project)
    codeStyleManager.reformatText(file, exprTextRange.startOffset, end)
}

private fun getLastElementOfArrayAndText(lastElement: PsiElement): Pair<PsiElement, String>? {
    var prevSibling = getPrevSiblingSkippingWhitespaceAndComments(lastElement)
            ?: return null
    val prevSiblingText = prevSibling.text
    if (prevSiblingText == ",") {
        prevSibling = getPrevSiblingSkippingWhitespaceAndComments(prevSibling) ?: return null
    }
    return Pair(prevSibling, prevSiblingText)
}

private fun appendToExpression(expr: ArrayCreationExpression,
                               prevSiblingText: String,
                               prevSibling: PsiElement,
                               fromText: StringLiteralExpression,
                               document: Document
): Int {
    var moreLen = 0
    if (prevSiblingText != "(" && prevSiblingText != "[") {
        val comma = PhpPsiElementFactory.createComma(expr.project)
        prevSibling.addAfter(comma, expr)
        moreLen += comma.text.length
        prevSibling.addAfter(fromText, expr)
    } else {
        val prevSiblingTextRange = prevSibling.textRange
        document.insertString(prevSiblingTextRange.endOffset, fromText.text)
    }
    return fromText.text.length + moreLen
}

private fun checkIfArrayHasValue(expr: ArrayCreationExpression, value: String): Boolean {
    for (child in expr.children) {
        if (child !is PhpPsiElement) {
            continue
        }
        val firstPsiChild = child.firstPsiChild
        if (firstPsiChild is StringLiteralExpression) {
            if (value == firstPsiChild.contents) {
                // already exists:
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
