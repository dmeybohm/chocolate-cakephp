package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.getPossibleTableClasses
import com.daveme.chocolateCakePHP.cake.isCakeTwoModel
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4


private val nonModelClassSuffixes = listOf("Controller", "Component", "Helper", "View")

class ControllerFieldTypeProvider : PhpTypeProvider4 {

    companion object {
        const val TYPE_PROVIDER_CHAR = '\u8312'
        private const val CONFIGURED_FIELD_MARKER = 'c'
        private const val MODEL_CHAIN_MARKER = 'm'
        private const val MAX_MODEL_CHAIN_LENGTH = 10

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
            return nestedPropertyType(psiElement, settings)
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
        if (result.isEmpty && settings.cake2Enabled && isThisInPossibleModel(psiElement)) {
            // $this->Author inside a CakePHP 2 model:
            return PhpType().add(modelChainSignature(listOf(fieldReferenceName), classReference))
        }
        return if (result.isEmpty) null else result
    }

    //
    // Look for a series of nested model reference like $this->Movie->Writer->Script...
    //
    private fun nestedPropertyType(
        psiElement: FieldReference,
        settings: Settings
    ): PhpType? {
        var element = psiElement as? FieldReference ?: return null
        val fieldNames = mutableListOf<String>()
        while (element.firstChild != null) {
            val name = element.name
            if (name == null || !name.startsWithUppercaseCharacter()) {
                return null
            }
            fieldNames.add(0, name)
            val child = element.firstChild
            if (child is FieldReference)
                element = child
            else
                break
        }
        val result = PhpType()
        if (settings.cake3Enabled) {
            // Defer lookup to complete.
            result.add("#" + getKey() + element.name + getKey() + psiElement.name)
        }
        if (settings.cake2Enabled) {
            val receiver = element.classReference ?: return result.takeUnless { it.isEmpty }
            result.add(modelChainSignature(fieldNames, receiver))
        }
        return result.takeUnless { it.isEmpty }
    }

    // The fields of a chain such as $this->Movie->Director, and the types of the receiver it
    // starts from, so complete() can resolve each link against the class the previous one gave:
    private fun modelChainSignature(fieldNames: List<String>, receiver: PhpTypedElement): String {
        val receiverTypes = receiver.type.types
            .filter { !it.contains(getKey()) && !it.contains('|') }
        return "#" + getKey() + MODEL_CHAIN_MARKER + fieldNames.joinToString(".") +
                getKey() + receiverTypes.joinToString("|")
    }

    private fun isThisInPossibleModel(fieldReference: FieldReference): Boolean {
        val variable = fieldReference.classReference as? Variable ?: return false
        if (variable.name != "this") {
            return false
        }
        val className = PsiTreeUtil.getParentOfType(fieldReference, PhpClass::class.java)?.name
            ?: return false
        return nonModelClassSuffixes.none { className.endsWith(it, ignoreCase = true) }
    }

    override fun complete(expression: String, project: Project): PhpType? {
        if (DumbService.getInstance(project).isDumb) {
            return null
        }

        val indexOfSign  = expression.indexOf(getKey())
        if (expression.getOrNull(indexOfSign + 1) == CONFIGURED_FIELD_MARKER) {
            return completeConfiguredField(expression.substring(indexOfSign + 2), project)
        }
        if (expression.getOrNull(indexOfSign + 1) == MODEL_CHAIN_MARKER) {
            return completeModelChain(expression.substring(indexOfSign + 2), project)
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
        return configuredFieldType(receiverClass, fieldName, project)
    }

    private fun configuredFieldType(receiverClass: String, fieldName: String, project: Project): PhpType {
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
            result.add(controllerConventionType(settings, fieldName, configured?.excludedKinds.orEmpty()))
        }
        return result
    }

    private fun completeModelChain(signature: String, project: Project): PhpType? {
        val fieldNames = signature.substringBefore(getKey()).split('.')
        val receiverSignature = signature.substringAfter(getKey(), "")
        if (fieldNames.any { it.isEmpty() } || fieldNames.size > MAX_MODEL_CHAIN_LENGTH ||
            receiverSignature.isEmpty()
        ) {
            return null
        }
        val phpIndex = PhpIndex.getInstance(project)
        val receiverType = PhpType()
        receiverSignature.split('|').filter { it.isNotEmpty() }.forEach { receiverType.add(it) }
        var receivers = receiverType.lookupCompleteType(project, phpIndex, null).types
            .filter { it.startsWith("\\") }
        var result: PhpType? = null
        for (fieldName in fieldNames) {
            val linkType = chainLinkType(project, phpIndex, receivers, fieldName) ?: return null
            receivers = linkType.types.toList()
            result = linkType
        }
        return result
    }

    // A controller resolves the link through its configured members, and a CakePHP 2 model
    // through its associations:
    private fun chainLinkType(project: Project, phpIndex: PhpIndex, receivers: List<String>, fieldName: String): PhpType? {
        val result = PhpType()
        receivers.filter { it.isAnyControllerClass() }
            .forEach { result.add(configuredFieldType(it, fieldName, project)) }
        val models = receivers.filter { phpIndex.isCakeTwoModel(it) }
        if (models.isNotEmpty()) {
            val configured = ConfiguredMemberService.memberType(
                project, models, setOf(MemberKind.ASSOCIATION), fieldName
            )
            configured?.let { result.add(it.type) }
            // An association bound from another receiver with bindModel() still resolves by name:
            if ((configured == null || !configured.hasInitializeLoad) && phpIndex.isCakeTwoModel("\\$fieldName")) {
                result.add("\\$fieldName")
            }
        }
        return result.takeUnless { it.isEmpty }
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
