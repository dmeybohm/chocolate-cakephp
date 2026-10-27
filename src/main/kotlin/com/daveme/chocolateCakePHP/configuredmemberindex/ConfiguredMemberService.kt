package com.daveme.chocolateCakePHP.configuredmemberindex

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.absoluteClassName
import com.daveme.chocolateCakePHP.findPluginConfigByName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.resolve.types.PhpType

/**
 * The resolved type of one configured member, and whether an `initialize()` load (or a
 * property array) in the receiver's chain determined it.
 */
data class MemberTypeResult(
    val classFqns: Set<String>,
    val classes: List<PhpClass>,
    val hasInitializeLoad: Boolean,
) {
    val type: PhpType
        get() = PhpType().apply { classFqns.forEach { add(it) } }
}

/**
 * The configured members of a set of receiver classes, by alias, together with the kinds
 * that some class in a receiver's chain loads in `initialize()` or a property array.
 */
data class LoadedMembers(
    val members: Map<String, MemberTypeResult>,
    val initializeKinds: Set<MemberKind>,
)

object ConfiguredMemberService {

    private val frameworkBaseClasses = setOf(
        "\\Cake\\Controller\\Controller",
        "\\Cake\\View\\View",
        "\\Cake\\View\\Helper",
        "\\Cake\\ORM\\Table",
    ).map { it.lowercase() }.toSet()

    private class ChainResolution(
        val members: Map<String, Pair<Set<String>, Boolean>>,
        val initializeKinds: Set<MemberKind>,
    )

    /**
     * The type of [alias] on receivers of type [classFqns], or null when no class in any
     * receiver's chain loads it.
     */
    fun memberType(
        project: Project,
        classFqns: Collection<String>,
        kinds: Set<MemberKind>,
        alias: String
    ): MemberTypeResult? {
        val resolutions = specificReceivers(classFqns).map { resolveChain(project, it, kinds, alias) }
        return combine(project, resolutions, alias)
    }

    fun loadedMembers(
        project: Project,
        classFqns: Collection<String>,
        kinds: Set<MemberKind>
    ): LoadedMembers {
        val resolutions = specificReceivers(classFqns).map { resolveChain(project, it, kinds, null) }
        val aliases = resolutions.flatMap { it.members.keys }.toSet()
        val members = aliases.mapNotNull { alias ->
            combine(project, resolutions, alias)?.let { alias to it }
        }.toMap()
        return LoadedMembers(members, resolutions.flatMap { it.initializeKinds }.toSet())
    }

    /**
     * The classes a single load resolves to.
     */
    fun loadClasses(project: Project, load: MemberLoad): List<PhpClass> {
        val phpIndex = PhpIndex.getInstance(project)
        return resolveLoad(phpIndex, Settings.getInstance(project), load)
            .flatMap { phpIndex.getClassesByFQN(it) }
    }

    // The framework base classes say nothing about which members a receiver has.
    private fun specificReceivers(classFqns: Collection<String>): List<String> =
        classFqns.filter { it.lowercase() !in frameworkBaseClasses }

    // A receiver whose chain does not load the alias falls back to convention, so the result
    // only counts as an initialize load when every receiver had one.
    private fun combine(
        project: Project,
        resolutions: List<ChainResolution>,
        alias: String
    ): MemberTypeResult? {
        val perReceiver = resolutions.map { it.members[alias] }
        if (perReceiver.all { it == null }) {
            return null
        }
        val fqns = perReceiver.filterNotNull().flatMap { it.first }.toSet()
        val phpIndex = PhpIndex.getInstance(project)
        return MemberTypeResult(
            classFqns = fqns,
            classes = fqns.flatMap { phpIndex.getClassesByFQN(it) },
            hasInitializeLoad = perReceiver.all { it != null && it.second },
        )
    }

    private fun resolveChain(
        project: Project,
        classFqn: String,
        kinds: Set<MemberKind>,
        alias: String?
    ): ChainResolution {
        val phpIndex = PhpIndex.getInstance(project)
        val settings = Settings.getInstance(project)
        val fileBasedIndex = FileBasedIndex.getInstance()
        val scope = GlobalSearchScope.allScope(project)

        // Most-derived class first:
        val loadsByClass = classChain(phpIndex, classFqn).map { fqn ->
            fileBasedIndex.getValues(CONFIGURED_MEMBER_INDEX_KEY, fqn, scope)
                .flatten()
                .filter { it.kind in kinds && (alias == null || it.alias == alias) }
        }
        val allLoads = loadsByClass.flatten()

        val members = allLoads.map { it.alias }.distinct().associateWith { eachAlias ->
            // A later initialize() load replaces the instance an ancestor loaded:
            val initializeLoads = loadsByClass.firstNotNullOfOrNull { loads ->
                loads.filter { it.alias == eachAlias && it.isInitializeLoad }.ifEmpty { null }
            } ?: emptyList()
            val otherLoads = allLoads.filter { it.alias == eachAlias && !it.isInitializeLoad }
            val fqns = (initializeLoads + otherLoads)
                .flatMap { resolveLoad(phpIndex, settings, it) }
                .toSet()
            fqns to initializeLoads.isNotEmpty()
        }
        val initializeKinds = allLoads.filter { it.isInitializeLoad }.map { it.kind }.toSet()
        return ChainResolution(members, initializeKinds)
    }

    private fun classChain(phpIndex: PhpIndex, classFqn: String): List<String> {
        val result = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var current = listOf(classFqn)
        while (current.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val next = mutableListOf<String>()
            for (fqn in current) {
                val lowerFqn = fqn.lowercase()
                if (lowerFqn in frameworkBaseClasses || !visited.add(lowerFqn)) {
                    continue
                }
                val classes = phpIndex.getClassesByFQN(fqn)
                classes.mapTo(result) { it.fqn }
                classes.mapNotNullTo(next) { it.superFQN }
            }
            current = next
        }
        return result.distinct()
    }

    private fun resolveLoad(phpIndex: PhpIndex, settings: Settings, load: MemberLoad): List<String> {
        val className = load.className
        if (className != null && className.contains('\\')) {
            return listOf(className.absoluteClassName())
        }
        val name = className ?: load.loadedName
        val dotIndex = name.indexOf('.')
        if (dotIndex > 0) {
            val shortName = name.substring(dotIndex + 1)
            val pluginConfig = settings.findPluginConfigByName(name.substring(0, dotIndex))
            if (pluginConfig != null) {
                return listOf(classFqn(pluginConfig.namespace, load.kind, shortName))
            }
            return conventionCandidates(phpIndex, settings, load.kind, shortName)
        }
        return conventionCandidates(phpIndex, settings, load.kind, name)
    }

    private fun conventionCandidates(
        phpIndex: PhpIndex,
        settings: Settings,
        kind: MemberKind,
        name: String
    ): List<String> {
        val namespaces = mutableListOf<String>()
        if (kind == MemberKind.COMPONENT || kind == MemberKind.HELPER) {
            namespaces += "\\Cake"
        }
        namespaces += settings.appNamespace
        settings.pluginConfigs.mapTo(namespaces) { it.namespace }

        val candidates = namespaces.map { classFqn(it, kind, name) }.distinct()
        val existing = candidates.filter { phpIndex.getClassesByFQN(it).isNotEmpty() }
        return existing.ifEmpty { candidates }
    }

    private fun classFqn(namespace: String, kind: MemberKind, name: String): String {
        val path = when (kind) {
            MemberKind.COMPONENT -> "Controller\\Component\\${name}Component"
            MemberKind.HELPER -> "View\\Helper\\${name}Helper"
            MemberKind.TABLE, MemberKind.ASSOCIATION -> "Model\\Table\\${name}Table"
        }
        return "${namespace.absoluteClassName()}\\$path"
    }
}
