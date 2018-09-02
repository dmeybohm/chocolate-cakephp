package com.daveme.intellij.chocolateCakePHP.typeProvider

import com.daveme.intellij.chocolateCakePHP.util.startsWithUppercaseCharacter
import com.intellij.openapi.project.Project
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
        val fieldReferenceName = psiElement.name
        if (!fieldReferenceName.startsWithUppercaseCharacter()) {
            return null
        }
        for (type in referenceType.types) {
            if (type.contains("Controller")) {
                return PhpType().add("\\" + fieldReferenceName)
                        .add("\\" + fieldReferenceName + "Component")
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
