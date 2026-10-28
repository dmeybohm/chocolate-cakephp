package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.isCakeViewFile
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class ViewHelperInViewTypeProvider : PhpTypeProvider4 {

    override fun getKey(): Char {
        return '茕'
    }

    override fun complete(expression: String, project: Project): PhpType? {
        if (DumbService.getInstance(project).isDumb) {
            return null
        }
        val fieldName = expression.substringOrNull(2) ?: return null
        val settings = Settings.getInstance(project)
        return configuredHelperType(project, settings, viewType(settings).types, fieldName)
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
        if (!classReference.textMatches("\$this")) {
            return null
        }
        if (!isCakeViewFile(psiElement.project, settings, psiElement.containingFile)) {
            return null
        }
        // Defer lookup to complete() to consult the helpers the view loads.
        return PhpType().add("#" + getKey() + fieldReferenceName)
    }

    override fun getBySignature(s: String, set: Set<String>, i: Int, project: Project): Collection<PhpNamedElement>? {
        return null
    }
}

/**
 * The type of helper property [fieldName] on receivers of type [receiverClasses]: the helpers
 * they load, plus the helpers by convention unless an initialize load determined the type.
 */
fun configuredHelperType(
    project: Project,
    settings: Settings,
    receiverClasses: Collection<String>,
    fieldName: String
): PhpType {
    val result = PhpType()
    val configured = ConfiguredMemberService.memberType(
        project,
        receiverClasses,
        setOf(MemberKind.HELPER),
        fieldName
    )
    if (configured != null) {
        result.add(configured.type)
    }
    if (configured == null || !configured.hasInitializeLoad) {
        result.add(viewHelperTypeFromFieldName(settings, fieldName))
    }
    return result
}
