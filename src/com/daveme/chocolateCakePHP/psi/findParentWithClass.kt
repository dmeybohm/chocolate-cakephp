package com.daveme.chocolateCakePHP.psi

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement

fun findParentWithClass(element: PsiElement, clazz: Class<out PsiElement>): PsiElement? {
    var iterationElement = element
    while (true) {
        val parent = iterationElement.parent ?: break
        if (PlatformPatterns.psiElement(clazz).accepts(parent)) {
            return parent
        }
        iterationElement = parent
    }
    return null
}
