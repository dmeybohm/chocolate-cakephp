package com.daveme.chocolateCakePHP.configuredmemberindex

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.isAnyControllerClass
import com.daveme.chocolateCakePHP.startsWithUppercaseCharacter
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
        val (receiverClass, fieldName) = parseClassFieldSignature(expression) ?: return null
        val kinds = memberKindsForReceiver(receiverClass) ?: return null
        if (DumbService.getInstance(project).isDumb) {
            return null
        }
        if (!Settings.getInstance(project).enabled) {
            return null
        }
        val configured = ConfiguredMemberService.memberType(project, listOf(receiverClass), kinds, fieldName)
        if (configured == null || !configured.hasInitializeLoad) {
            return null
        }
        val declaresField = PhpIndex.getInstance(project).getClassesByFQN(receiverClass)
            .any { it.findFieldByName(fieldName, false) != null }
        if (declaresField) {
            return null
        }
        // A non-null result here stops PhpStorm from resolving the field natively:
        return configured.type
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

        /**
         * The receiver class and field name of a native field signature on a class receiver,
         * such as `#P#C\App\Controller\MoviesController.Movies`.
         */
        fun parseClassFieldSignature(expression: String): Pair<String, String>? {
            if (expression.length < 6 || expression[2] != '#' || expression[3] !in CLASS_RECEIVER_KEYS) {
                return null
            }
            val separator = expression.lastIndexOf('.')
            if (separator < 5) {
                return null
            }
            val receiverClass = expression.substring(4, separator)
            val fieldName = expression.substring(separator + 1)
            if (!receiverClass.startsWith("\\") || !fieldName.startsWithUppercaseCharacter()) {
                return null
            }
            return receiverClass to fieldName
        }

        private fun memberKindsForReceiver(receiverClass: String): Set<MemberKind>? =
            when {
                receiverClass.isAnyControllerClass() -> setOf(MemberKind.COMPONENT, MemberKind.TABLE)
                receiverClass.endsWith("View", ignoreCase = true) ||
                    receiverClass.endsWith("Helper", ignoreCase = true) -> setOf(MemberKind.HELPER)
                else -> null
            }
    }
}
