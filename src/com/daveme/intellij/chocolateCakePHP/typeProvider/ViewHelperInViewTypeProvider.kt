package com.daveme.intellij.chocolateCakePHP.typeProvider

import com.daveme.intellij.chocolateCakePHP.cake.isCakeTemplate
import com.daveme.intellij.chocolateCakePHP.util.startsWithUppercaseCharacter
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider3

class ViewHelperInViewTypeProvider : PhpTypeProvider3 {

    override fun getKey(): Char {
        return 0.toChar()
    }

    override fun getType(psiElement: PsiElement): PhpType? {
        if (psiElement !is FieldReference) {
            return null
        }
        if (!isCakeTemplate(psiElement.containingFile.name)) {
            return null
        }
        val classReference = psiElement.classReference ?: return null
        val fieldReferenceName = psiElement.name
        if (!fieldReferenceName.startsWithUppercaseCharacter()) {
            return null
        }
        if (classReference.text == "\$this") {
            // @todo make this configurable
            return PhpType().add("\\" + fieldReferenceName + "Helper")
                .add("\\Cake\\View\\Helper\\" + fieldReferenceName + "Helper")
                .add("\\App\\View\\Helper\\" + fieldReferenceName + "Helper")
                .add("\\DebugKit\\View\\Helper\\" + fieldReferenceName + "Helper")
        }
        return null
    }

    override fun getBySignature(s: String, set: Set<String>, i: Int, project: Project): Collection<PhpNamedElement>? {
        return null
    }
}
