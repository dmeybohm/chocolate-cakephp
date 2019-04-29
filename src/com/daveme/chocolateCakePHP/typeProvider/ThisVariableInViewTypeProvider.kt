package com.daveme.chocolateCakePHP.typeProvider

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.isCakeTemplate
import com.daveme.chocolateCakePHP.viewType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider3

class ThisVariableInViewTypeProvider : PhpTypeProvider3 {

    override fun getKey(): Char {
        return 0.toChar()
    }

    override fun getType(psiElement: PsiElement): PhpType? {
        if (psiElement !is Variable) {
            return null
        }
        val settings = Settings.getInstance(psiElement.project)
        if (!psiElement.containingFile.name.isCakeTemplate(settings)) {
            return null
        }
        if (psiElement.text != "\$this") {
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
