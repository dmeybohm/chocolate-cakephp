package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.getPossibleTableClasses
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class AssociatedTableTypeProvider : PhpTypeProvider4 {

    private val TYPE_PROVIDER_CHAR = '\u8317'
    private val TYPE_PROVIDER_END_CHAR = '\u8312'

    override fun getKey(): Char {
        return TYPE_PROVIDER_CHAR
    }

    override fun getType(psiElement: PsiElement): PhpType? {
        val fieldReference = psiElement as? FieldReference ?: return null
        val fieldName = fieldReference.name ?: return null

        //
        // Handles:
        //       $this->Articles->Movies
        //
        if (fieldName.startsWithUppercaseCharacter() &&
            fieldReference.parent is FieldReference
        ) {
            val settings = Settings.getInstance(fieldReference.project)
            if (!settings.cake3Enabled) {
                return null
            }
            val parent = fieldReference.parent as? FieldReference ?: return null
            val parentFieldName = parent.name ?: return null
            if (!parentFieldName.startsWithUppercaseCharacter()) {
                return null
            }
            return PhpType().add("#${key}.v1.${fieldName}")
        }

        if (
            fieldName.startsWithUppercaseCharacter() &&
            fieldReference.firstChild is Variable
        ) {
            val variable = fieldReference.firstChild as Variable
            val variableSignature = variable.signature
            val hasFetchTable = variableSignature.contains("fetchTable")
            val endChar = TYPE_PROVIDER_END_CHAR
            if (hasFetchTable)  {
                return PhpType().add("#${key}.v2.${fieldName}.${variableSignature}${endChar}")
            }
        }

        return null
    }

    override fun complete(expression: String, project: Project): PhpType? {
        val version = expression.substring(3, 5)
        val possibleTableName = expression.substring(6)
        if (version != "v1") {
            return null
        }
        val settings = Settings.getInstance(project)
        if (!settings.cake3Enabled) {
            return null
        }
        val phpIndex = PhpIndex.getInstance(project)
        return getAllPossibleAssociationTableClassesFromName(
            phpIndex,
            settings,
            possibleTableName
        )
    }

    override fun getBySignature(
        expression: String,
        set: Set<String>,
        depth: Int,
        project: Project
    ): Collection<PhpNamedElement?> {
        val settings = Settings.getInstance(project)
        if (!settings.cake3Enabled) {
            return emptyList()
        }
        val version = expression.substring(1, 3)
        if (version != "v2") {
            return emptyList()
        }
        val tableEnd = expression.substring(4).indexOf('.')
        if (tableEnd <= 0) {
            return emptyList()
        }
        val possibleTableName = expression.substring(4, tableEnd + 4)
        val endChar = TYPE_PROVIDER_END_CHAR
        val signatureEnd = expression.substring(tableEnd + 5).indexOf(endChar)
        if (signatureEnd <= 0) {
            return emptyList()
        }
        val signature = expression.substring(tableEnd + 5, signatureEnd + tableEnd + 5)
        val phpIndex = PhpIndex.getInstance(project)

        //
        // TODO I think we can't use getBySignature here because getBySignature() isn't
        //      implemented on the TableLocatorTypeProvider, but that may work bette, but that may work better
        //
        val varType = PhpType()
        signature.split("|").forEach { sigPart -> varType.add(sigPart) }
        val completeType = varType.lookupCompleteType(project, phpIndex, set)
        if (completeType.types.isEmpty()) {
            return emptyList()
        }

        if (completeType.isDefinitelyTableClass()) {
            val resultClasses = phpIndex.getPossibleTableClasses(settings, possibleTableName)
            if (resultClasses.isNotEmpty()) {
                // todo add association classes?
                return resultClasses
            }

        }
        return emptyList()
    }

    private fun getAllPossibleAssociationTableClassesFromName(
        phpIndex: PhpIndex,
        settings: Settings,
        possibleTableName: String
    ): PhpType? {
        val resultClasses = phpIndex.getPossibleTableClasses(settings, possibleTableName)
        if (resultClasses.isNotEmpty()) {
            val result = PhpType()
                    .add("\\Cake\\ORM\\Association\\BelongsTo")
                    .add("\\Cake\\ORM\\Association\\BelongsToMany")
                    .add("\\Cake\\ORM\\Association\\HasOne")
                    .add("\\Cake\\ORM\\Association\\HasMany")

            resultClasses.forEach { result.add(it.fqn) }
            return result
        }

        return null
    }
}
