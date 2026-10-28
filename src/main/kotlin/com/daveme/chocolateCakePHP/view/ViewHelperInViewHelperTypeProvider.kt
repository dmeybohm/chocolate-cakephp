package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.viewHelperTypeFromFieldName
import com.daveme.chocolateCakePHP.startsWithUppercaseCharacter
import com.daveme.chocolateCakePHP.Settings
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

// $this->Html in a template, from ViewHelperInViewTypeProvider:
private const val VIEW_HELPER_IN_VIEW_SIGNATURE = "#\u8315"

class ViewHelperInViewHelperTypeProvider : PhpTypeProvider4 {

    override fun getKey(): Char {
        return '茔'
    }

    override fun complete(expression: String, project: Project): PhpType? {
        if (DumbService.getInstance(project).isDumb) {
            return null
        }
        val signature = expression.substring(2)
        val receiverClass = signature.substringBefore(getKey())
        val fieldName = signature.substringAfter(getKey(), "")
        if (receiverClass.isEmpty() || fieldName.isEmpty()) {
            return null
        }
        val settings = Settings.getInstance(project)
        return configuredHelperType(project, settings, listOf(receiverClass), fieldName)
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
        val referenceType = classReference.type
        val knownType = referenceType.filterUnknown()

        val fieldReferenceName = psiElement.name ?: return null
        if (!fieldReferenceName.startsWithUppercaseCharacter()) {
            return null
        }
        // Each helper the receiver may be is resolved against its own configured helpers:
        val result = PhpType()
        for (type in knownType.types) {
            if (!type.contains("Helper") && !type.startsWith(VIEW_HELPER_IN_VIEW_SIGNATURE)) {
                continue
            }
            if (type.startsWith("\\")) {
                // Defer lookup to complete() to consult the helpers the helper loads.
                result.add("#" + getKey() + type + getKey() + fieldReferenceName)
            } else {
                result.add(viewHelperTypeFromFieldName(settings, fieldReferenceName))
            }
        }
        return if (result.isEmpty) null else result
    }

    override fun getBySignature(s: String, set: Set<String>, i: Int, project: Project): Collection<PhpNamedElement> {
        // We use the default signature processor exclusively:
        return emptyList()
    }
}
