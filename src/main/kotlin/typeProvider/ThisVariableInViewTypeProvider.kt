package com.daveme.chocolateCakePHP.typeProvider

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.isCakeTemplate
import com.daveme.chocolateCakePHP.viewType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class ThisVariableInViewTypeProvider : PhpTypeProvider4 {

    override fun getKey(): Char {
        return 0.toChar()
    }

    override fun complete(p0: String?, p1: Project?): PhpType? = null

    override fun getType(psiElement: PsiElement): PhpType? {
        if (psiElement !is Variable) {
            return null
        }
        val settings = Settings.getInstance(psiElement.project)
        if (!settings.enabled) {
            return null
        }
        if (!psiElement.containingFile.name.isCakeTemplate(settings)) {
            return null
        }
        if (!psiElement.textMatches("\$this")) {
            return null
        }
        return viewType(settings)
    }

    override fun getBySignature(
        expression: String,
        set: Set<String>,
        i: Int,
        project: Project
    ): Collection<PhpNamedElement> {
        // We use the default signature processor exclusively:
        return emptyList()
    }

}
