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

    companion object {
        private const val VIEW_FILE_MARKER = 'v'
    }

    override fun getKey(): Char {
        return '茕'
    }

    override fun complete(expression: String, project: Project): PhpType? {
        if (DumbService.getInstance(project).isDumb) {
            return null
        }
        val signature = expression.substringOrNull(2) ?: return null
        val settings = Settings.getInstance(project)
        if (signature.firstOrNull() == VIEW_FILE_MARKER) {
            val viewFile = ViewHelperReceivers.decode(signature.substring(1).substringBeforeLast(key), key)
                ?: return null
            val fieldName = signature.substringAfterLast(key, "").ifEmpty { return null }
            // A view loads any helper by name, so one its receivers do not load keeps its convention type:
            return configuredHelperType(
                project, settings, ViewHelperReceivers.of(project, viewFile), fieldName, loadsAnyHelper = true
            )
        }
        return configuredHelperType(project, settings, viewType(settings).types, signature)
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
        // A view's helpers can depend on what renders it, so the signature carries the view
        // file for complete() to find its receivers:
        val viewFile = psiElement.containingFile.originalFile.virtualFile?.let {
            ViewHelperReceivers.viewFileOf(psiElement.project, settings, it)
        }
        if (viewFile != null) {
            return PhpType().add(
                "#" + getKey() + VIEW_FILE_MARKER + ViewHelperReceivers.encode(viewFile, getKey()) +
                        getKey() + fieldReferenceName
            )
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
 * With [loadsAnyHelper], a helper the receivers do not load still has its convention type.
 */
fun configuredHelperType(
    project: Project,
    settings: Settings,
    receiverClasses: Collection<String>,
    fieldName: String,
    loadsAnyHelper: Boolean = false
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
    if (configured == null || !configured.hasInitializeLoad || (loadsAnyHelper && configured.classFqns.isEmpty())) {
        result.add(viewHelperTypeFromFieldName(settings, fieldName))
    }
    return result
}
