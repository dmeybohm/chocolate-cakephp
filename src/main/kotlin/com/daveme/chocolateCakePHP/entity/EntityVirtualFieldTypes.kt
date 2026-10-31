package com.daveme.chocolateCakePHP.entity

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeSignatureKey

/**
 * The types of an entity's accessor-backed fields, from PhpStorm's own field signatures.
 */
object EntityVirtualFieldTypes {

    private val CLASS_RECEIVER_KEYS = setOf(
        PhpTypeSignatureKey.CLASS.key,
        PhpTypeSignatureKey.POLYMORPHIC_CLASS.key,
    )

    private val NON_ENTITY_SUFFIXES = listOf(
        "Controller", "Table", "Component", "Helper", "View", "Cell", "Behavior",
    )

    // An empty type would leave the field to native resolution, so this completes to nothing instead:
    private val UNRESOLVABLE_SIGNATURE = "#${PhpTypeSignatureKey.FIELD.key}"

    private val FIELD_NAME = Regex("[a-z_][A-Za-z0-9_]*")

    fun complete(expression: String, project: Project): PhpType? {
        val (receiver, fieldName) = parseFieldSignature(expression) ?: return null
        val receiverClass = classOfReceiver(receiver)
        if (receiverClass != null && receiverClass.isNeverAnEntity()) {
            return null
        }
        val settings = Settings.getInstance(project)
        if (!settings.enabled || !settings.cake3Enabled || DumbService.getInstance(project).isDumb) {
            return null
        }
        val phpIndex = PhpIndex.getInstance(project)
        val classNames = if (receiverClass != null)
            listOf(receiverClass)
        else
            PhpType().add(receiver).lookupCompleteType(project, phpIndex, null).types
                .filter { it.startsWith("\\") && !PhpType.isPrimitiveType(it) }
        if (classNames.isEmpty()) {
            return null
        }
        val entities = EntityAccessors.entityClasses(phpIndex, classNames) ?: return null
        if (entities.isEmpty()) {
            return frameworkReceiverType(phpIndex, receiverClass, classNames, fieldName)
        }
        // A non-null result here stops PhpStorm from resolving the field natively, for every
        // receiver class, so one class without an answer leaves them all to native resolution:
        val result = PhpType()
        for (entity in entities) {
            if (entity.findFieldByName(fieldName, false) != null) {
                return null
            }
            val getter = EntityAccessors.tableOf(project, entity).getter(entity, fieldName) ?: return null
            result.add(getterType(project, phpIndex, getter))
        }
        return result.takeUnless { it.isEmpty }
    }

    // A table method declares a framework entity type, and the entity it really returns reaches
    // the field through another signature, so its __get() should not add to that answer:
    private fun frameworkReceiverType(
        phpIndex: PhpIndex,
        receiverClass: String?,
        classNames: List<String>,
        fieldName: String
    ): PhpType? {
        val declaresField = classNames.any { name ->
            phpIndex.getAnyByFQN(name).any { it.findFieldByName(fieldName, false) != null }
        }
        return if (receiverClass != null || declaresField)
            null
        else
            PhpType().add(UNRESOLVABLE_SIGNATURE)
    }

    /**
     * The type of the field a getter backs: its stated return type, or else the inferred one.
     */
    fun getterType(project: Project, phpIndex: PhpIndex, getter: Method): PhpType {
        val stated = PhpType()
        getter.returnType?.let { stated.add(it.type) }
        getter.docComment?.returnTag?.let { stated.add(it.type) }
        return (if (stated.isEmpty) getter.type else stated).lookupCompleteType(project, phpIndex, null)
    }

    private fun parseFieldSignature(expression: String): Pair<String, String>? {
        if (expression.length < 6 || expression[2] != '#') {
            return null
        }
        val separator = expression.lastIndexOf('.')
        if (separator < 5) {
            return null
        }
        val fieldName = expression.substring(separator + 1)
        if (!FIELD_NAME.matches(fieldName)) {
            return null
        }
        return expression.substring(2, separator) to fieldName
    }

    private fun classOfReceiver(receiver: String): String? {
        if (receiver[1] !in CLASS_RECEIVER_KEYS || receiver.getOrNull(2) != '\\') {
            return null
        }
        return receiver.substring(2)
    }

    private fun String.isNeverAnEntity(): Boolean =
        !contains("\\Entity\\", ignoreCase = true) && NON_ENTITY_SUFFIXES.any { endsWith(it, ignoreCase = true) }
}
