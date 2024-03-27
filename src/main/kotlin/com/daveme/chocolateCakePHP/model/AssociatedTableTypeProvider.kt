package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.PluginEntry
import com.daveme.chocolateCakePHP.isAnyTableClass
import com.daveme.chocolateCakePHP.startsWithUppercaseCharacter
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class AssociatedTableTypeProvider : PhpTypeProvider4 {
    companion object {
        const val typeProviderChar = '\u8317'
    }

    override fun getKey(): Char {
        return typeProviderChar
    }

    override fun getType(psiElement: PsiElement): PhpType? {
        val fieldReference = psiElement as? FieldReference ?: return null
        val fieldName = fieldReference.name ?: return null
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
            return PhpType().add("#${key}.${fieldName}");
        }
        return null
    }

    override fun complete(expression: String, project: Project): PhpType? {
        val possibleTableName = expression.substring(3)
        val settings = Settings.getInstance(project)
        if (!settings.cake3Enabled) {
            return null
        }
        val resultClasses = mutableListOf<PhpClass>()
        val possibleTableClass = "${settings.appNamespace}\\Model\\Table\\${possibleTableName}Table"
        val phpIndex = PhpIndex.getInstance(project)
        resultClasses += phpIndex.getClassesByFQN(possibleTableClass)
        settings.pluginEntries.forEach { pluginEntry ->
            resultClasses += phpIndex.getClassesByFQN("${pluginEntry.namespace}\\Model\\Table\\${possibleTableName}Table")
        }
        if (resultClasses.size > 0) {
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

    override fun getBySignature(s: String, set: Set<String>, i: Int, project: Project): Collection<PhpNamedElement?> {
        return emptyList()
    }
}
