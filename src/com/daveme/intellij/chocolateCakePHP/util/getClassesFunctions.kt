package com.daveme.intellij.chocolateCakePHP.util

import com.daveme.intellij.chocolateCakePHP.cake.viewHelperClassesFiltered
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass

private const val VIEW_HELPER_PARENT_CLASS = "\\AppHelper"

fun getClassesForViewHelper(project: Project, fieldName: String): Collection<PhpClass> {
    return getClasses(project, "\\" + fieldName + "Helper")
}

fun getAllViewHelperSubclassesFiltered(project: Project): Collection<PhpClass> {
    val index = PhpIndex.getInstance(project)
    val allSubclasses = index.getAllSubclasses(VIEW_HELPER_PARENT_CLASS)
    return viewHelperClassesFiltered(allSubclasses)
}

fun getClassesAsArray(project: Project, className: String): Array<PsiElement> {
    return getClasses(project, className).toTypedArray()
}

private fun getClasses(project: Project, className: String): Collection<PhpClass> {
    val phpIndex = PhpIndex.getInstance(project)
    return phpIndex.getClassesByFQN(className)
}

