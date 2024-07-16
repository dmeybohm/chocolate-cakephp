package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.getPossibleTableClassesWithDefault
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
        val version = expression.substring(3, 5)
        val settings = Settings.getInstance(project)
        if (!settings.cake3Enabled) {
            return null
        }
        when (version) {
            "v1" -> {
                val phpIndex = PhpIndex.getInstance(project)
                val possibleTableName = expression.substring(6)
                return getAllPossibleAssociationTableClassesFromName(
                    phpIndex,
                    settings,
                    possibleTableName
                )
            }
            "v2" -> {
                val start = expression.substring(2)
                val tableEnd = start.substring(4).indexOf('.')
                if (tableEnd <= 0) {
                    return null
                }
                val possibleTableName = start.substring(4, tableEnd + 4)
                val endChar = TYPE_PROVIDER_END_CHAR
                val signatureEnd = start.substring(tableEnd + 5).indexOf(endChar)
                if (signatureEnd <= 0) {
                    return null
                }
                val signature = start.substring(tableEnd + 5, signatureEnd + tableEnd + 5)
                val phpIndex = PhpIndex.getInstance(project)

                val varType = PhpType()
                signature.split("|")
                    .filter { !it.contains(TYPE_PROVIDER_CHAR) } // avoid recursion
                    .forEach { varType.add(it) }

                val completeType = varType.lookupCompleteType(project, phpIndex, null)
                if (completeType.types.isEmpty()) {
                    return null
                }

                if (!completeType.isDefinitelyTableClass()) {
                    return null
                }

                return getAllPossibleAssociationTableClassesFromName(phpIndex, settings, possibleTableName)
            }
            else -> {
                return null
            }
        }
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
        val resultClasses = phpIndex.getPossibleTableClassesWithDefault(settings, possibleTableName)
        if (resultClasses.isNotEmpty()) {
            val result = PhpType()
            if (resultClasses.size == 1) {
                result.add(EncodedType.encodeForDynamicTable(possibleTableName))
            }
            result.add("\\Cake\\ORM\\Association\\BelongsTo")
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
