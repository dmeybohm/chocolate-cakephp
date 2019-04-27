package com.daveme.chocolateCakePHP.typeProvider

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.componentOrModelTypeFromFieldReference
import com.daveme.chocolateCakePHP.isControllerClass
import com.daveme.chocolateCakePHP.startsWithUppercaseCharacter
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider3

class ControllerFieldTypeProvider : PhpTypeProvider3 {

    override fun getKey(): Char {
        return 0.toChar()
    }

    override fun getType(psiElement: PsiElement): PhpType? {
        if (psiElement !is FieldReference) {
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

        val settings = Settings.getInstance(psiElement.project)
        for (type in referenceType.types) {
            if (type.isControllerClass()) {
                return componentOrModelTypeFromFieldReference(settings, fieldReferenceName)
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
