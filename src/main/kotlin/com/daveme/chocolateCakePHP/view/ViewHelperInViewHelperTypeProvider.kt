package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.viewHelperTypeFromFieldName
import com.daveme.chocolateCakePHP.startsWithUppercaseCharacter
import com.daveme.chocolateCakePHP.Settings
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class ViewHelperInViewHelperTypeProvider : PhpTypeProvider4 {

    override fun getKey(): Char {
        return '\u8314'
    }

    override fun complete(p0: String?, p1: Project?): PhpType? = null

    override fun getType(psiElement: PsiElement): PhpType? {
        if (psiElement !is FieldReference) {
            return null
        }
        val settings = Settings.getInstance(psiElement.project)
        if (!settings.enabled) {
            return null
        }
        val classReference = psiElement.classReference ?: return null
        val referenceType = classReference.type
        val fieldReferenceName = psiElement.name ?: return null
        if (!fieldReferenceName.startsWithUppercaseCharacter()) {
            return null
        }
        for (type in referenceType.types) {
            if (type.contains("Helper")) {
                return viewHelperTypeFromFieldName(settings, fieldReferenceName)
            }
        }
        return null
    }

    override fun getBySignature(s: String, set: Set<String>, i: Int, project: Project): Collection<PhpNamedElement> {
        // We use the default signature processor exclusively:
        return emptyList()
    }
}
