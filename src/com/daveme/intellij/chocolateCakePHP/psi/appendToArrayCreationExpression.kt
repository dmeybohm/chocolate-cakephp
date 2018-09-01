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
    val lastElement = expr.lastChild
    for (child in expr.children) {
        if (child !is PhpPsiElement) {
            continue
        }
        val firstPsiChild = child.firstPsiChild
        if (firstPsiChild is StringLiteralExpression) {
            if (valueToAdd == firstPsiChild.contents) {
                // already exists:
                return true
            }
        }
    }
    val project = expr.project
    val codeStyleManager = CodeStyleManager.getInstance(project)
    val fromText = createLiteralString(project, valueToAdd) ?: return false
    var prevSibling: PsiElement? = getPrevSiblingSkippingWhitespaceAndComments(lastElement)
            ?: return false
    val prevSiblingText = prevSibling!!.text
    if (prevSiblingText == ",") {
        prevSibling = getPrevSiblingSkippingWhitespaceAndComments(prevSibling)
        if (prevSibling == null) {
            return false
        }
    }
    var extraLen = fromText.text.length
    try {
        if (prevSiblingText != "(" && prevSiblingText != "[") {
            val comma = PhpPsiElementFactory.createComma(project)
            prevSibling.addAfter(comma, expr)
            extraLen += comma.text.length
            prevSibling.addAfter(fromText, expr)
        } else {
            val prevSiblingTextRange = prevSibling.getTextRange()
            document.insertString(prevSiblingTextRange.getEndOffset(), fromText.text)
        }
    } catch (e: IncorrectOperationException) {
        println("IncorrectOperationException")
        return false
    }

    val exprTextRange = expr.textRange
    val end = exprTextRange.endOffset + extraLen
    codeStyleManager.reformatText(file, exprTextRange.startOffset, end)
    return true
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
