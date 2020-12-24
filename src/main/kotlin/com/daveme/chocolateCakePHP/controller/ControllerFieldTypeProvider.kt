package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.componentOrModelTypeFromFieldName
import com.daveme.chocolateCakePHP.isControllerClass
import com.daveme.chocolateCakePHP.startsWithUppercaseCharacter
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class ControllerFieldTypeProvider : PhpTypeProvider4 {

    override fun getKey(): Char {
        return '\u8312'
    }

    override fun complete(str: String?, project: Project?): PhpType? = null

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

        // don't add types for nested types ($this->FooBar->FooBar):
        if (psiElement.firstChild is FieldReference) {
            return null
        }

        if (!fieldReferenceName.startsWithUppercaseCharacter()) {
            return null
        }

        for (type in referenceType.types) {
            if (type.isControllerClass()) {
                return componentOrModelTypeFromFieldName(settings, fieldReferenceName)
            }
        }
        return null
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
