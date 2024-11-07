package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.getPossibleTableClasses
import com.daveme.chocolateCakePHP.startsWithUppercaseCharacter
import com.daveme.chocolateCakePHP.substringOrNull
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class AssociatedTableTypeProvider : PhpTypeProvider4 {

    companion object {
        const val TYPE_PROVIDER_CHAR = '\u8317'
        const val TYPE_PROVIDER_END_CHAR = '\u8301'
    }

    override fun getKey(): Char {
        return TYPE_PROVIDER_CHAR
    }

    override fun getType(psiElement: PsiElement): PhpType? {
        val fieldReference = psiElement as? FieldReference ?: return null
        val fieldName = fieldReference.name ?: return null
        if (fieldName.isEmpty()) return null

        val settings = Settings.getInstance(fieldReference.project)
        if (!settings.cake3Enabled) {
            return null
        }

        //
        // Handles:
        //       $this->Articles->Movies
        //
        if (fieldName.startsWithUppercaseCharacter() &&
            fieldReference.parent is FieldReference
        ) {
            // $this->Movies->Articles
            val parent = fieldReference.parent as? FieldReference ?: return null
            val parentFieldName = parent.name ?: return null
            if (!parentFieldName.startsWithUppercaseCharacter()) {
                return null
            }
            return PhpType().add("#${key}.v1.${fieldName}")
        }

        if (!fieldName.startsWithUppercaseCharacter()) {
            return null
        }
        when (fieldReference.firstChild) {
            is Variable -> {
                // $movies = $this->fetchTable("Movies");
                // $movies->Articles
                //
                // $movies = TableRegistry::get("Movies");
                // $movies->Articles
                //
                // $movies = $this->getTableLocator()->get("Movies");
                // $movies->Articles
                val variable = fieldReference.firstChild as Variable
                val variableSignature = variable.signature
                val hasFetchTable = variableSignature.hasFetchTableMethodCall() ||
                        variableSignature.hasGetTableLocatorMethodCall() ||
                        variableSignature.hasTableRegistryGetCall()
                val endChar = TYPE_PROVIDER_END_CHAR
                if (hasFetchTable)  {
                    return PhpType().add("#${key}.v2.${fieldName}.${variableSignature}${endChar}")
                }
            }
            is MethodReference -> {
                // $this->fetchTable("Movies")->Articles
                // $this->getTableLocator()->get("Movies")->Articles
                // TableRegistry::get("Movies")->Articles
                val methodReference = fieldReference.firstChild as MethodReference
                if (
                    methodReference.name.equals("fetchTable", ignoreCase = true) ||
                    isTableLocatorCall(methodReference) ||
                    isTableRegistryGetCall(methodReference)
                ) {
                    val methodSignature = methodReference.signature
                    val endChar = TYPE_PROVIDER_END_CHAR
                    return PhpType().add("#${key}.v2.${fieldName}.${methodSignature}${endChar}")
                }

            }
        }

        return null
    }

    override fun complete(expression: String, project: Project): PhpType? {
        val version = expression.substringOrNull(3, 5) ?: return null
        val possibleTableName = expression.substringOrNull(6) ?: return null
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
        val version = expression.substringOrNull(1, 3)
            ?: return emptyList()
        if (version != "v2") {
            return emptyList()
        }
        val tableEnd = expression.substringOrNull(4)?.indexOf('.')
        if (tableEnd == null || tableEnd <= 0) {
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
        //      implemented on the TableLocatorTypeProvider, but that may work better
        //
        val varType = PhpType()
        signature.split("|")
            .filter { !it.contains(TYPE_PROVIDER_CHAR) } // avoid recursion
            .forEach { varType.add(it) }

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

    private fun isTableLocatorCall(methodReference: MethodReference): Boolean {
        val child = methodReference.firstChild as? MethodReference ?: return false
        return child.name.equals("getTableLocator", ignoreCase = true)
    }

    private fun isTableRegistryGetCall(methodReference: MethodReference): Boolean {
        val classReference = methodReference.classReference ?: return false
        return methodReference.name.equals("get", ignoreCase = true) &&
                classReference.type.isProbablyTableRegistryClass()
    }
}
