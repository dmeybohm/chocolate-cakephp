package com.daveme.chocolateCakePHP.psi

import com.intellij.openapi.project.Project
import com.jetbrains.php.lang.psi.PhpPsiElementFactory
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

fun createLiteralString(project: Project, str: CharSequence): StringLiteralExpression? {
    return PhpPsiElementFactory.createFromText(
        project,
        StringLiteralExpression::class.java,
        String.format("'%s'", str)
    )
}