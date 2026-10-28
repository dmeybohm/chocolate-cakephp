package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.getPossibleTableClasses
import com.daveme.chocolateCakePHP.cake.isCakeTwoModelClass
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4


class ControllerFieldTypeProvider : PhpTypeProvider4 {

    companion object {
        const val TYPE_PROVIDER_CHAR = '\u8312'
        private const val CONFIGURED_FIELD_MARKER = 'c'

        fun isConfiguredFieldSignature(type: String): Boolean =
            type.startsWith("#$TYPE_PROVIDER_CHAR$CONFIGURED_FIELD_MARKER")

        /**
         * The deferred signature of a field on a controller, completed against the
         * members the controller is configured to load.
         */
        fun configuredFieldSignature(controllerClass: String, fieldName: String): String =
            "#$TYPE_PROVIDER_CHAR$CONFIGURED_FIELD_MARKER$controllerClass$TYPE_PROVIDER_CHAR$fieldName"

        /**
         * The deferred signatures [getType] gives `$receiver->Field`, one for each controller
         * class the receiver may be.
         */
        fun configuredFieldSignatures(fieldReference: FieldReference): List<String> {
            val fieldName = fieldReference.name ?: return emptyList()
            if (!fieldName.startsWithUppercaseCharacter() || fieldReference.firstChild is FieldReference) {
                return emptyList()
            }
            return fieldReference.classReference?.type?.filterUnknown()?.types.orEmpty()
                .filter { it.isAnyControllerClass() && it.startsWith("\\") }
                .map { configuredFieldSignature(it, fieldName) }
        }
    }

    override fun getKey(): Char {
        return TYPE_PROVIDER_CHAR
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

        val childReference = psiElement.firstChild as? FieldReference
        if (childReference != null) {
            return nestedPropertyType(psiElement)
        }

        // Each controller the receiver may be is resolved against its own configured members:
        val result = PhpType()
        val referenceType = classReference.type.filterUnknown()
        for (type in referenceType.types) {
            if (!type.isAnyControllerClass()) {
                continue
            }
            if (type.startsWith("\\")) {
                // Defer lookup to complete() to consult the configured members of the controller.
                result.add(configuredFieldSignature(type, fieldReferenceName))
            } else {
                result.add(componentOrModelTypeFromFieldName(settings, fieldReferenceName))
            }
        }
        return if (result.isEmpty) null else result
    }

    //
    // Look for a series of nested model reference like $this->Movie->Writer->Script...
    //
    private fun nestedPropertyType(
        psiElement: FieldReference
    ): PhpType? {
        var element = psiElement as? FieldReference ?: return null
        while (element.firstChild != null) {
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
        return PhpType().add("#" + getKey() + element.name + getKey() + psiElement.name)
    }

    override fun complete(expression: String, project: Project): PhpType? {
        if (DumbService.getInstance(project).isDumb) {
            return null
        }

        val indexOfSign  = expression.indexOf(getKey())
        if (expression.getOrNull(indexOfSign + 1) == CONFIGURED_FIELD_MARKER) {
            return completeConfiguredField(expression.substring(indexOfSign + 2), project)
        }
        val indexOfDelimiter = expression.indexOf(getKey(), indexOfSign + 1)
        val firstFieldName = expression.substringOrNull(indexOfSign + 1, indexOfDelimiter)
            ?: return null
        val targetFieldName = expression.substringOrNull(indexOfDelimiter + 1)
            ?: return null

        val index = PhpIndex.getInstance(project)
        val settings = Settings.getInstance(project)

        val result = PhpType()

        if (settings.cake3Enabled) {
            val cakeThreeClasses = getCakeThreeClasses(index, settings, firstFieldName, targetFieldName)
            if (cakeThreeClasses.size > 0) {
                cakeThreeClasses.forEach { result.add(it.fqn) }
            }
        }

        if (settings.cake2Enabled) {
            val cakeTwoClass = getCakeTwoClass(index, firstFieldName, targetFieldName)
            if (cakeTwoClass != null) {
                result.add(cakeTwoClass)
            }
        }

        if (result.types.size > 0) {
            return result
        } else {
            return null
        }
    }

    private fun completeConfiguredField(signature: String, project: Project): PhpType? {
        val receiverClass = signature.substringBefore(getKey())
        val fieldName = signature.substringAfter(getKey(), "")
        if (receiverClass.isEmpty() || fieldName.isEmpty()) {
            return null
        }
        val settings = Settings.getInstance(project)
        val result = PhpType()
        val configured = ConfiguredMemberService.memberType(
            project,
            listOf(receiverClass),
            setOf(MemberKind.COMPONENT, MemberKind.TABLE),
            fieldName
        )
        if (configured != null) {
            result.add(configured.type)
        }
        if (configured == null || !configured.hasInitializeLoad) {
            result.add(componentOrModelTypeFromFieldName(settings, fieldName))
        }
        return result
    }

    private fun getCakeTwoClass(phpIndex: PhpIndex, firstFieldName: String, targetFieldName: String): String? {
        val firstClasses = phpIndex.getClassesByFQN("\\" + firstFieldName)
        if (!isCakeTwoModelClass(firstClasses)) {
            return null
        }

        val targetClasses = phpIndex.getClassesByFQN("\\" + targetFieldName)
        if (!isCakeTwoModelClass(targetClasses)) {
            return null
        }
        return "\\" + targetFieldName
    }

    private fun getCakeThreeClasses(phpIndex: PhpIndex, settings: Settings,
                                    firstFieldName: String, targetFieldName: String): Collection<PhpClass> {
        val firstClasses = phpIndex.getPossibleTableClasses(settings, firstFieldName)
        if (firstClasses.size == 0) {
            return listOf()
        }
        return phpIndex.getPossibleTableClasses(settings, targetFieldName)
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
