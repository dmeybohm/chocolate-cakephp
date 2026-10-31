package com.daveme.chocolateCakePHP.entity

import com.daveme.chocolateCakePHP.PhpFilesModificationTracker
import com.daveme.chocolateCakePHP.camelCaseToUnderscore
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import java.util.concurrent.ConcurrentHashMap

private const val ORM_ENTITY = "\\Cake\\ORM\\Entity"
private const val ENTITY_INTERFACE = "\\Cake\\Datasource\\EntityInterface"
private const val ENTITY_TRAIT = "\\Cake\\Datasource\\EntityTrait"

/**
 * The accessor methods of an entity class, by method name, under each field spelling CakePHP
 * answers to, as `EntityTrait::_accessor()` registers them.
 */
class EntityAccessorTable(
    private val getters: Map<String, String>,
    private val setters: Map<String, String>,
    val fields: Set<String>,
) {
    fun getter(phpClass: PhpClass, field: String): Method? =
        getters[field]?.let { phpClass.findMethodByName(it) }

    fun setter(phpClass: PhpClass, field: String): Method? =
        setters[field]?.let { phpClass.findMethodByName(it) }
}

object EntityAccessors {

    private val CACHE_KEY = Key.create<CachedValue<ConcurrentHashMap<String, EntityAccessorTable>>>(
        "com.daveme.chocolateCakePHP.entity.EntityAccessors"
    )

    fun tableOf(project: Project, phpClass: PhpClass): EntityAccessorTable {
        val tracker = project.getService(PhpFilesModificationTracker::class.java)
        tracker.ensureArmed()
        val cache = CachedValuesManager.getManager(project).getCachedValue(
            project,
            CACHE_KEY,
            { CachedValueProvider.Result.create(ConcurrentHashMap<String, EntityAccessorTable>(), tracker) },
            false
        )
        return cache.getOrPut(phpClass.fqn) { buildTable(phpClass) }
    }

    fun isFrameworkEntityType(fqn: String): Boolean =
        fqn.equals(ORM_ENTITY, ignoreCase = true) || fqn.equals(ENTITY_INTERFACE, ignoreCase = true)

    fun isEntityClass(phpClass: PhpClass): Boolean =
        classChain(phpClass).any { each ->
            each.fqn.equals(ORM_ENTITY, ignoreCase = true) ||
                each.interfaceNames.any { it.equals(ENTITY_INTERFACE, ignoreCase = true) }
        }

    /**
     * The entity classes behind [classNames], leaving out the framework's own entity types when a
     * more specific class is among them. Null when any of the classes is not an entity.
     */
    fun entityClasses(phpIndex: PhpIndex, classNames: Collection<String>): List<PhpClass>? {
        val specific = classNames.filterNot { isFrameworkEntityType(it) }
        val result = specific.mapNotNull { entityClass(phpIndex, it) }
        return result.takeIf { it.size == specific.size }
    }

    /**
     * The entity classes among the types of a field reference's receiver, ignoring any other class.
     */
    fun entitiesOfReceiver(phpIndex: PhpIndex, fieldReference: FieldReference): List<PhpClass> {
        val project = fieldReference.project
        val receiverTypes = fieldReference.classReference?.type
            ?.lookupCompleteType(project, phpIndex, null)
            ?.types
            .orEmpty()
        return receiverTypes
            .filter { it.startsWith("\\") && !isFrameworkEntityType(it) }
            .mapNotNull { entityClass(phpIndex, it) }
    }

    private fun entityClass(phpIndex: PhpIndex, fqn: String): PhpClass? =
        phpIndex.getClassesByFQN(fqn).firstOrNull()?.takeIf { isEntityClass(it) }

    private fun classChain(phpClass: PhpClass): Sequence<PhpClass> {
        val visited = mutableSetOf<String>()
        return generateSequence(phpClass) { it.superClass }.takeWhile { visited.add(it.fqn) }
    }

    private fun buildTable(phpClass: PhpClass): EntityAccessorTable {
        val getters = mutableMapOf<String, String>()
        val setters = mutableMapOf<String, String>()
        val fields = linkedSetOf<String>()
        for (each in classChain(phpClass)) {
            if (each.fqn.equals(ORM_ENTITY, ignoreCase = true)) {
                break
            }
            // get_class_methods() runs in the scope of the class that uses the trait, which is
            // the only class whose private methods it can see:
            val seesPrivate = each.traitNames.any { it.equals(ENTITY_TRAIT, ignoreCase = true) }
            for (method in methodsOf(each)) {
                if (method.access.isPrivate && !seesPrivate) {
                    continue
                }
                val name = method.name
                val accessors = when {
                    name.length <= 4 -> continue
                    name.startsWith("_get") -> getters
                    name.startsWith("_set") -> setters
                    else -> continue
                }
                val field = name.substring(4).replaceFirstChar { it.lowercaseChar() }
                val underscored = field.camelCaseToUnderscore()
                fields.add(underscored)
                for (spelling in listOf(underscored, field, field.replaceFirstChar { it.uppercaseChar() })) {
                    accessors.putIfAbsent(spelling, name)
                }
            }
        }
        return EntityAccessorTable(getters, setters, fields)
    }

    // A class's own method overrides the same method from a trait it uses:
    private fun methodsOf(phpClass: PhpClass, visited: MutableSet<String> = mutableSetOf()): List<Method> {
        if (!visited.add(phpClass.fqn)) {
            return emptyList()
        }
        return phpClass.ownMethods.toList() + phpClass.traits.flatMap { methodsOf(it, visited) }
    }
}
