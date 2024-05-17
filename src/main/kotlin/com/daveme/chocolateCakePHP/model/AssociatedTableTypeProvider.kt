package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.getPossibleTableClasses
import com.daveme.chocolateCakePHP.startsWithUppercaseCharacter
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class AssociatedTableTypeProvider : PhpTypeProvider4 {

    companion object {
        const val TYPE_PROVIDER_CHAR = '\u8317'
    }

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
            return PhpType().add("#${key}.${fieldName}")
        }

        //
        // TODO handle:
        //      $articles = $this->fetchTable('Articles');
        //      $articles->Movies
        //
        return null
    }

    override fun complete(expression: String, project: Project): PhpType? {
        val possibleTableName = expression.substring(3)
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
