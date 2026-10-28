package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.getPossibleTableClasses
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.controller.ControllerFieldTypeProvider
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.daveme.chocolateCakePHP.startsWithUppercaseCharacter
import com.daveme.chocolateCakePHP.substringOrNull
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
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
        //       $this->Authors (inside a table class)
        //
        val receiverTable = thisTableClass(fieldReference)
        if (fieldName.startsWithUppercaseCharacter() && receiverTable != null) {
            return PhpType().add("#${key}.v3.${fieldName}.${receiverTable}")
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
            val receiverSignature = receiverSignature(fieldReference.firstChild)
            val endChar = TYPE_PROVIDER_END_CHAR
            return PhpType().add("#${key}.v1.${fieldName}.${receiverSignature}${endChar}")
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
            is FieldReference -> {
                // $this->Movies->Articles
                val innerSignature = receiverSignature(fieldReference.firstChild)
                val endChar = TYPE_PROVIDER_END_CHAR
                return PhpType().add("#${key}.v2.${fieldName}.${innerSignature}${endChar}")
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
        if (DumbService.getInstance(project).isDumb) {
            return null
        }

        val version = expression.substringOrNull(3, 5) ?: return null
        val possibleTableName = expression.substringOrNull(6) ?: return null
        val settings = Settings.getInstance(project)
        if (!settings.cake3Enabled) {
            return null
        }
        val phpIndex = PhpIndex.getInstance(project)
        val associationName = possibleTableName.substringBefore('.')
        return when (version) {
            "v1" -> {
                val receiverSignature = possibleTableName.substringAfter('.', "")
                    .substringBefore(TYPE_PROVIDER_END_CHAR)
                getConfiguredAssociationType(
                    project,
                    phpIndex,
                    settings,
                    associationName,
                    receiverTableClasses(project, phpIndex, receiverSignature)
                )
            }
            "v3" -> getConfiguredAssociationType(
                project,
                phpIndex,
                settings,
                associationName,
                listOf(possibleTableName.substringAfter('.'))
            )
            else -> null
        }
    }

    override fun getBySignature(
        expression: String,
        set: Set<String>,
        depth: Int,
        project: Project
    ): Collection<PhpNamedElement?> {
        if (DumbService.getInstance(project).isDumb) {
            return emptyList()
        }

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
        val possibleTableName = expression.substringOrNull(4, tableEnd + 4)
            ?: return emptyList()
        val endChar = TYPE_PROVIDER_END_CHAR
        val signatureEnd = expression.substringOrNull(tableEnd + 5)?.indexOf(endChar)
            ?: return emptyList()
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
            val tableTypes = completeType.types.filter { it.startsWith("\\") && it.isAnyTableClass() }
            val configured = ConfiguredMemberService.memberType(
                project,
                tableTypes,
                setOf(MemberKind.ASSOCIATION),
                possibleTableName
            )
            val resultClasses = configured?.classes.orEmpty().toMutableList()
            if (configured == null || !configured.hasInitializeLoad) {
                resultClasses += phpIndex.getPossibleTableClasses(settings, possibleTableName)
            }
            if (resultClasses.isNotEmpty()) {
                // todo add association classes?
                return resultClasses.distinct()
            }

        }
        return emptyList()
    }

    private fun getConfiguredAssociationType(
        project: Project,
        phpIndex: PhpIndex,
        settings: Settings,
        associationName: String,
        receiverTables: List<String>
    ): PhpType? {
        val configured = ConfiguredMemberService.memberType(
            project,
            receiverTables,
            setOf(MemberKind.ASSOCIATION),
            associationName
        )
        val result = PhpType()
        if (configured != null && configured.classFqns.isNotEmpty()) {
            result.add(associationClassesType())
            result.add(configured.type)
        }
        if (configured == null || !configured.hasInitializeLoad) {
            getAllPossibleAssociationTableClassesFromName(phpIndex, settings, associationName)
                ?.let { result.add(it) }
        }
        return if (result.isEmpty) null else result
    }

    private fun receiverSignature(receiver: PsiElement): String {
        val types = (receiver as? PhpTypedElement)?.type?.types.orEmpty()
            .filter { !it.contains(TYPE_PROVIDER_CHAR) } // avoid recursion
            .toMutableList()
        // The receiver's own type does not include the deferred controller field signature here:
        (receiver as? FieldReference)
            ?.let { types += ControllerFieldTypeProvider.configuredFieldSignatures(it) }
        return types.distinct().joinToString("|")
    }

    private fun receiverTableClasses(project: Project, phpIndex: PhpIndex, signature: String): List<String> {
        val receiverType = PhpType()
        signature.split("|")
            .filter { it.isNotEmpty() && !it.contains(TYPE_PROVIDER_CHAR) } // avoid recursion
            .forEach { receiverType.add(it) }
        if (receiverType.isEmpty) {
            return emptyList()
        }
        return receiverType.lookupCompleteType(project, phpIndex, null).types
            .filter { it.startsWith("\\") && it.isAnyTableClass() }
    }

    private fun getAllPossibleAssociationTableClassesFromName(
        phpIndex: PhpIndex,
        settings: Settings,
        possibleTableName: String
    ): PhpType? {
        val resultClasses = phpIndex.getPossibleTableClasses(settings, possibleTableName)
        if (resultClasses.isNotEmpty()) {
            val result = associationClassesType()
            resultClasses.forEach { result.add(it.fqn) }
            return result
        }

        return null
    }

    private fun associationClassesType(): PhpType =
        PhpType()
            .add("\\Cake\\ORM\\Association\\BelongsTo")
            .add("\\Cake\\ORM\\Association\\BelongsToMany")
            .add("\\Cake\\ORM\\Association\\HasOne")
            .add("\\Cake\\ORM\\Association\\HasMany")

    // The table class of `$this` when the field is accessed directly on `$this` in a table.
    private fun thisTableClass(fieldReference: FieldReference): String? {
        val variable = fieldReference.firstChild as? Variable ?: return null
        if (variable.name != "this") {
            return null
        }
        return variable.type.types.firstOrNull {
            it.startsWith("\\") && it.isAnyTableClass() && !it.contains('.')
        }
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
