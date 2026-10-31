package com.daveme.chocolateCakePHP.configuredmemberindex

/**
 * Resolves the members of a CakePHP 3 to 5 class chain, most-derived class first, where an
 * `initialize()` load or a property array replaces an ancestor's load of the same alias.
 */
internal class InitializeChainResolver(private val configurations: List<ClassConfiguration>) : ChainResolver {

    override fun resolve(kinds: Set<MemberKind>, alias: String?, resolveLoad: (MemberLoad) -> List<String>): ChainResolution {
        val loadsByClass = configurations.map { configuration ->
            configuration.loads.filter { it.kind in kinds && (alias == null || it.alias == alias) }
        }
        val allLoads = loadsByClass.flatten()

        val members = allLoads.map { it.alias }.distinct().associateWith { eachAlias ->
            val nearest = configurations.zip(loadsByClass).firstNotNullOfOrNull { (configuration, loads) ->
                loads.filter { it.alias == eachAlias && it.isInitializeLoad }
                    .ifEmpty { null }
                    ?.let { configuration to it }
            }
            val initializeLoads = nearest?.second.orEmpty()
            val otherLoads = allLoads.filter { it.alias == eachAlias && !it.isInitializeLoad }
            val fqns = (initializeLoads + otherLoads).flatMap(resolveLoad).toSet()
            fqns to (nearest != null && initializeLoads.all { determinesMember(it, nearest.first) })
        }
        val incompleteKinds = configurations.flatMap { it.declarations }.filter { !it.isComplete }.map { it.kind }
        val initializeKinds = allLoads.filter { it.isInitializeLoad }.map { it.kind }.toSet() - incompleteKinds.toSet()
        return ChainResolution(members, initializeKinds)
    }

    private fun determinesMember(load: MemberLoad, configuration: ClassConfiguration): Boolean =
        !load.hasUnreadClassName && (
            load.source != LoadSource.PROPERTY_ARRAY ||
                configuration.declarations.none { it.property == load.property && !it.isComplete }
        )
}
