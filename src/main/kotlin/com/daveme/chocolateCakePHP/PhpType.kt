package com.daveme.chocolateCakePHP

import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.resolve.types.PhpType

// Guesses for incomplete types:
fun PhpType.isProbablyControllerClass(): Boolean =
    if (this.isComplete)
        this.types.any { it.isAnyControllerClass() }
    else
        this.types.any { it.contains("Controller", ignoreCase = true) }

fun PhpType.isProbablyTableLocatorClass(): Boolean =
    if (this.isComplete) {
        this.types.any {
            it.equals("\\Cake\\ORM\\Locator\\LocatorInterface", ignoreCase = true)
        }
    } else {
        this.types.any { it.contains("Locator", ignoreCase = true) ||
                it.hasGetTableLocatorMethodCall()
        }
    }

fun PhpType.isProbablyTableRegistryClass(): Boolean =
    if (this.isComplete) {
        this.isDefinitelyTableRegistryClass()
    } else {
        this.types.any { it.contains("TableRegistry", ignoreCase = true) }
    }

fun PhpType.isDefinitelyTableClass(): Boolean =
    this.isComplete && this.types.any { it.isAnyTableClass() }

fun PhpType.isDefinitelyTableRegistryClass(): Boolean =
    this.isComplete && this.types.any { it.isTableRegistryClass() }

fun PhpType.isProbablyTableClass(): Boolean =
    if (this.isComplete)
        this.types.any { it.isAnyTableClass() }
    else
        this.types.any { it.contains("Table", ignoreCase = true) }

fun PhpType.isProbablyQueryObject(): Boolean =
    if (this.isComplete)
        this.types.any { it.isQueryObject() }
    else
        this.types.any { it.contains("Query", ignoreCase = true) }

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