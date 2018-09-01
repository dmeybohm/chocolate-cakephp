package com.daveme.intellij.chocolateCakePHP.util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass

fun getViewHelperClasses(project: Project, fieldName: String): Collection<PhpClass> {
    return getClasses(project, "\\" + fieldName + "Helper")
}

fun getClassesAsArray(project: Project, className: String): Array<PsiElement> {
    return getClasses(project, className).toTypedArray()
}

private fun getClasses(project: Project, className: String): Collection<PhpClass> {
    val phpIndex = PhpIndex.getInstance(project)
    return phpIndex.getClassesByFQN(className)
}

