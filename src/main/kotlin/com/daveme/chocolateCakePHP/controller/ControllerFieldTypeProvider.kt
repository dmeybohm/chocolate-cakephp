package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.CakeModel.isCakeTwoModelClass
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4


class ControllerFieldTypeProvider : PhpTypeProvider4 {

    override fun getKey(): Char {
        return '\u8312'
    }

    override fun getType(psiElement: PsiElement): PhpType? {
        if (psiElement !is FieldReference) {
            return null
        }
        val settings = Settings.getInstance(psiElement.project)
        if (!settings.enabled) {
            return null
        }
        val classReference = psiElement.classReference ?: return null
        val fieldReferenceName = psiElement.name ?: return null

        if (!fieldReferenceName.startsWithUppercaseCharacter()) {
            return null
        }

        // don't add types for nested types ($this->FooBar->FooBar) on cake 3+:
        if (psiElement.firstChild is FieldReference) {
            if (settings.cake2Enabled) {
                return cakeTwoNestedModelCompletion(psiElement)
            } else {
                return null
            }
        }

        val referenceType = classReference.type.filterUnknown()
        for (type in referenceType.types) {
            if (type.isControllerClass()) {
                return componentOrModelTypeFromFieldName(settings, fieldReferenceName)
            }
        }
        return null
    }

    //
    // Look for a series of nested model reference like $this->Movie->Writer->Script...
    //
    private fun cakeTwoNestedModelCompletion(
        psiElement: FieldReference
    ): PhpType? {
        var element : FieldReference? = psiElement
        while (element?.firstChild != null) {
            if (!(element.name?.startsWithUppercaseCharacter() ?: false)) {
                return null
            }
            val child = element.firstChild
            if (child is FieldReference)
                element = child
            else
                break
        }
        // Defer lookup to complete.
        // First verify that first index extends from AppModel, and then that the last one is also a model.
        return PhpType().add("#" + getKey() + element!!.name + getKey() + psiElement.name)
    }

    override fun complete(expression: String, project: Project): PhpType? {
        val indexOfSign  = expression.indexOf(getKey())
        val indexOfDelimiter = expression.indexOf(getKey(), indexOfSign + 1)
        val firstFieldName = expression.substring(indexOfSign + 1, indexOfDelimiter)
        val targetFieldName = expression.substring(indexOfDelimiter + 1)

        val index = PhpIndex.getInstance(project)

        val firstClasses = index.getClassesByFQN("\\" + firstFieldName);
        if (!isCakeTwoModelClass(firstClasses)) {
            return null
        }

        val targetClasses = index.getClassesByFQN("\\" + targetFieldName)
        if (!isCakeTwoModelClass(targetClasses)) {
            return null
        }

        return PhpType().add("\\" + targetFieldName)
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
