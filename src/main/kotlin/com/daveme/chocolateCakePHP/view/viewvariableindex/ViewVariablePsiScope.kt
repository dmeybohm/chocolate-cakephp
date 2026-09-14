package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.SelfAssignmentExpression
import com.jetbrains.php.lang.psi.elements.Variable

/*
 * PSI scope helpers for resolving the value side of an indexed view variable. The indexed
 * source may be a controller method, or a view file (template, layout, element) whose
 * top-level code has no enclosing method, so every helper treats "nearest Function, else
 * the file" as the variable scope. Mirrors the scope rule in CakeController.kt.
 */

/** The nearest enclosing method, function or closure, else the containing file. */
internal fun enclosingScope(element: PsiElement): PsiElement =
    PsiTreeUtil.getParentOfType(element, Function::class.java) ?: element.containingFile

/**
 * The last plain `$symbolName = ...` assignment that starts before [offset] in the scope that
 * contains [offset]. Assignments inside nested functions or closures belong to their own scope
 * and are not considered; compound assignments (`.=`, `+=`) are a different PSI type and skipped.
 */
internal fun lastAssignmentBefore(sourceFile: PsiFile, symbolName: String, offset: Int): AssignmentExpression? {
    val at = sourceFile.findElementAt(offset) ?: return null
    val scope = enclosingScope(at)
    return PsiTreeUtil.findChildrenOfType(scope, AssignmentExpression::class.java)
        .filter { assignment ->
            assignment !is SelfAssignmentExpression &&
                (assignment.variable as? Variable)?.name == symbolName &&
                assignment.textRange.startOffset < offset &&
                enclosingScope(assignment) == scope
        }
        .maxByOrNull { it.textRange.startOffset }
}

/** The parameter named [symbolName] of the function enclosing [offset]; null at file scope. */
internal fun scopeParameter(sourceFile: PsiFile, symbolName: String, offset: Int): Parameter? {
    val at = sourceFile.findElementAt(offset) ?: return null
    val function = PsiTreeUtil.getParentOfType(at, Function::class.java) ?: return null
    return function.parameters.firstOrNull { it.name == symbolName }
}

/**
 * A `$symbolName` variable usable for type inference: the variable at [offset] itself when it
 * is that variable (a `$value` passed in an array or a `set()` pair), else the first such
 * variable in the enclosing scope (for `compact('name')`, where the offset is on a string).
 * Only variables at or before [offset] are considered, and variables inside nested functions or
 * closures are excluded. Null when the scope has not mentioned the variable before the use.
 */
internal fun scopeVariable(sourceFile: PsiFile, symbolName: String, offset: Int): Variable? {
    val at = sourceFile.findElementAt(offset) ?: return null
    val here = PsiTreeUtil.getParentOfType(at, Variable::class.java, false)
    if (here != null && here.name == symbolName) {
        return here
    }
    val scope = enclosingScope(at)
    return PsiTreeUtil.findChildrenOfType(scope, Variable::class.java)
        .firstOrNull {
            it.name == symbolName && it.textRange.startOffset <= offset &&
                enclosingScope(it) == scope
        }
}
