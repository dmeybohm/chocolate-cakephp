package com.daveme.chocolateCakePHP.configuredmemberindex

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.isCakeTwoModel
import com.daveme.chocolateCakePHP.controllerConventionType
import com.daveme.chocolateCakePHP.isAnyControllerClass
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.daveme.chocolateCakePHP.startsWithUppercaseCharacter
import com.daveme.chocolateCakePHP.view.ViewHelperReceivers
import com.daveme.chocolateCakePHP.viewHelperTypeFromFieldName
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeSignatureKey

/**
 * Completes PhpStorm's own field signatures for members a class is configured to load, in
 * place of the type of the class's magic `__get`.
 */
class ConfiguredMemberFieldTypeProvider : PhpTypeProvider4 {

    override fun getKey(): Char = PhpTypeSignatureKey.FIELD.key

    // Only completes the native field signatures PhpStorm already gives field references.
    override fun getType(psiElement: PsiElement): PhpType? = null

    override fun complete(expression: String, project: Project): PhpType? {
        val (receiver, fieldName) = parseFieldSignature(expression) ?: return null
        val receiverClass = classOfReceiver(receiver)
        val settings = Settings.getInstance(project)
        if (receiverClass == null && !(settings.cake2Enabled && isChainWithinBounds(expression))) {
            return null
        }
        if (DumbService.getInstance(project).isDumb || !settings.enabled) {
            return null
        }
        val phpIndex = PhpIndex.getInstance(project)
        val receiverClasses = if (receiverClass != null)
            listOf(receiverClass)
        else
            resolveReceiverClasses(project, phpIndex, receiver)
        if (receiverClasses.isEmpty()) {
            return null
        }
        // A non-null result here stops PhpStorm from resolving the field natively, for every
        // receiver class, so one class without an answer leaves them all to native resolution:
        val result = PhpType()
        for (each in receiverClasses) {
            result.add(fieldTypeOnClass(project, settings, phpIndex, each, fieldName) ?: return null)
        }
        return result
    }

    private fun resolveReceiverClasses(project: Project, phpIndex: PhpIndex, receiver: String): List<String> =
        PhpType().add(receiver).lookupCompleteType(project, phpIndex, null).types
            .filter { it.startsWith("\\") && !PhpType.isPrimitiveType(it) }

    // The type of the field on one class, or null if native resolution should provide it.
    private fun fieldTypeOnClass(
        project: Project,
        settings: Settings,
        phpIndex: PhpIndex,
        receiverClass: String,
        fieldName: String
    ): PhpType? {
        val isModel = settings.cake2Enabled && phpIndex.isCakeTwoModel(receiverClass)
        val kinds = if (isModel) setOf(MemberKind.ASSOCIATION) else memberKindsForReceiver(receiverClass) ?: return null
        val declaresField = phpIndex.getClassesByFQN(receiverClass)
            .any { it.findFieldByName(fieldName, false) != null }
        if (declaresField) {
            return null
        }
        val isView = receiverClass.isViewClassName()
        // The builder always passes a view its helpers, which replaces this property:
        val ignoredProperties = if (isView) setOf(HELPERS_PROPERTY) else emptySet()
        val configured = ConfiguredMemberService.memberType(
            project, listOf(receiverClass), kinds, fieldName
        ) { ignoredProperties }
        val determined = configured != null && configured.hasInitializeLoad
        if (determined && configured!!.classFqns.isNotEmpty()) {
            return configured.type
        }
        // In a template, ViewHelperInViewTypeProvider knows which classes render it, so for a helper
        // one of them may configure, neither __get() nor the convention should add to its answer:
        if (settings.cake3Enabled && receiverClass.equals("${settings.appNamespace}\\View\\AppView", ignoreCase = true) &&
            fieldName in ViewHelperReceivers.aliasesOfRenderingClasses(project)
        ) {
            return PhpType().apply {
                add(UNRESOLVABLE_SIGNATURE)
                configured?.let { add(it.type) }
            }
        }
        return when {
            // A model can bind an association from anywhere, so one found by name still resolves:
            isModel -> {
                val result = PhpType()
                configured?.let { result.add(it.type) }
                if (phpIndex.isCakeTwoModel("\\$fieldName")) {
                    result.add("\\$fieldName")
                }
                result.takeUnless { it.isEmpty }
            }
            // A view loads any helper by name, so its configuration rules nothing out. CakePHP 2
            // views get their helpers from the controllers rendering them, not from this class:
            isView && settings.cake2Enabled -> null
            determined && !isView -> PhpType().add(PhpType.NULL)
            else -> conventionFieldType(settings, phpIndex, receiverClass, fieldName, configured)
        }
    }

    private fun conventionFieldType(
        settings: Settings,
        phpIndex: PhpIndex,
        receiverClass: String,
        fieldName: String,
        configured: MemberTypeResult?
    ): PhpType? {
        val convention = if (receiverClass.isAnyControllerClass())
            controllerConventionType(settings, fieldName, configured?.excludedKinds.orEmpty())
        else
            viewHelperTypeFromFieldName(settings, fieldName)
        // Without a class to show for it, the convention says less than the __get() type does:
        val hasClass = configured?.classFqns.orEmpty().isNotEmpty() ||
            convention.types.any { phpIndex.getClassesByFQN(it).isNotEmpty() }
        if (settings.cake3Enabled && !hasClass) {
            return null
        }
        return PhpType().apply {
            configured?.let { add(it.type) }
            add(convention)
        }
    }

    override fun getBySignature(
        expression: String,
        set: Set<String>,
        i: Int,
        project: Project
    ): Collection<PhpNamedElement> = emptyList()

    companion object {

        private val CLASS_RECEIVER_KEYS = setOf(
            PhpTypeSignatureKey.CLASS.key,
            PhpTypeSignatureKey.POLYMORPHIC_CLASS.key,
        )

        private const val MAX_CHAIN_LENGTH = 10

        // An empty type would leave the field to native resolution, so this completes to nothing instead:
        private val UNRESOLVABLE_SIGNATURE = "#${PhpTypeSignatureKey.FIELD.key}"

        /**
         * The receiver signature and field name of a native field signature, such as
         * `#C\\App\\Controller\\MoviesController` and `Movies` for
         * `#P#C\\App\\Controller\\MoviesController.Movies`.
         */
        fun parseFieldSignature(expression: String): Pair<String, String>? {
            if (expression.length < 6 || expression[2] != '#') {
                return null
            }
            val separator = expression.lastIndexOf('.')
            if (separator < 5) {
                return null
            }
            val fieldName = expression.substring(separator + 1)
            if (!fieldName.startsWithUppercaseCharacter()) {
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

        private fun isChainWithinBounds(expression: String): Boolean {
            val prefix = "#${PhpTypeSignatureKey.FIELD.key}"
            var depth = 0
            while (expression.startsWith(prefix, depth * prefix.length)) {
                depth++
            }
            return depth <= MAX_CHAIN_LENGTH
        }

        private fun String.isViewClassName(): Boolean = endsWith("View", ignoreCase = true)

        private fun memberKindsForReceiver(receiverClass: String): Set<MemberKind>? =
            when {
                receiverClass.isAnyControllerClass() -> setOf(MemberKind.COMPONENT, MemberKind.TABLE)
                receiverClass.isViewClassName() ||
                    receiverClass.endsWith("Helper", ignoreCase = true) -> setOf(MemberKind.HELPER)
                else -> null
            }
    }
}
