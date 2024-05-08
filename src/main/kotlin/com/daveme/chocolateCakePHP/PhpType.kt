package com.daveme.chocolateCakePHP

import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.resolve.types.PhpType

// Guesses for incomplete types:
fun PhpType.isProbablyControllerClass(): Boolean =
    if (this.isComplete)
        this.types.any { it.isAnyControllerClass() }
    else
        this.types.any { it.contains("Controller") }

fun PhpType.lookupCompleteType(
    project: Project,
    phpIndex: PhpIndex,
    visited: Set<String>?
): PhpType {
    return if (this.isComplete)
        this
    else
        phpIndex.completeType(project, this, visited)
}

fun PhpType.lookupCompleteType(
    project: Project,
    visited: Set<String>?
): PhpType {
    return if (this.isComplete)
        this
    else {
        val phpIndex = PhpIndex.getInstance(project)
        return lookupCompleteType(project, phpIndex, visited)
    }
}