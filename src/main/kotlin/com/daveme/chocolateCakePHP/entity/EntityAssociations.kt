package com.daveme.chocolateCakePHP.entity

import com.daveme.chocolateCakePHP.camelCaseToUnderscore
import com.daveme.chocolateCakePHP.configuredmemberindex.AssociationType
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberLoad
import com.daveme.chocolateCakePHP.configuredmemberindex.TableAssociationLoad
import com.daveme.chocolateCakePHP.entityToTableClass
import com.daveme.chocolateCakePHP.singularize
import com.daveme.chocolateCakePHP.tableToEntityClass
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.resolve.types.PhpType

private const val ORM_ENTITY = "\\Cake\\ORM\\Entity"

/**
 * A property an entity gets from an association configured on its table.
 */
class EntityAssociationProperty(
    val type: PhpType,
    private val loads: List<TableAssociationLoad>,
    private val targetEntities: List<String>,
) {
    /**
     * The association calls that configure the property, and the entity classes it holds.
     */
    fun targets(project: Project, phpIndex: PhpIndex): List<PsiElement> {
        val calls = loads.mapNotNull { (declaringClass, load) ->
            phpIndex.getClassesByFQN(declaringClass).firstOrNull()
                ?.containingFile?.findElementAt(load.offset)?.parent
        }
        return calls + targetEntities.flatMap { phpIndex.getClassesByFQN(it) }
    }
}

object EntityAssociations {

    /**
     * The property name CakePHP gives an association, or null if the source doesn't determine it.
     */
    fun propertyName(load: MemberLoad): String? {
        load.propertyName?.let { return it }
        if (load.hasUnreadPropertyName) {
            return null
        }
        val name = load.alias.substringAfterLast('.')
        return if (load.associationType.isToOne())
            name.singularize().camelCaseToUnderscore()
        else
            name.camelCaseToUnderscore()
    }

    fun propertiesOf(project: Project, phpIndex: PhpIndex, entity: PhpClass): Map<String, EntityAssociationProperty> {
        val tableFqn = entity.fqn.entityToTableClass() ?: return emptyMap()
        if (phpIndex.getClassesByFQN(tableFqn).isEmpty()) {
            return emptyMap()
        }
        return ConfiguredMemberService.associationLoads(project, listOf(tableFqn))
            .filter { !it.load.hasUnreadClassName }
            .groupBy { propertyName(it.load) }
            .mapNotNull { (name, loads) -> name?.let { it to propertyOf(project, phpIndex, loads) } }
            .toMap()
    }

    private fun propertyOf(
        project: Project,
        phpIndex: PhpIndex,
        loads: List<TableAssociationLoad>
    ): EntityAssociationProperty {
        val type = PhpType()
        val targets = mutableListOf<String>()
        for ((_, load) in loads) {
            val entities = ConfiguredMemberService.loadClasses(project, load)
                .map { it.fqn.tableToEntityClass() }
                .filter { phpIndex.getClassesByFQN(it).isNotEmpty() }
            targets += entities
            val isToOne = load.associationType.isToOne()
            for (each in entities.ifEmpty { listOf(ORM_ENTITY) }) {
                type.add(if (isToOne) each else "$each[]")
            }
            if (isToOne) {
                type.add(PhpType.NULL)
            }
        }
        return EntityAssociationProperty(type, loads, targets.distinct())
    }

    private fun AssociationType?.isToOne(): Boolean =
        this == AssociationType.BELONGS_TO || this == AssociationType.HAS_ONE
}
