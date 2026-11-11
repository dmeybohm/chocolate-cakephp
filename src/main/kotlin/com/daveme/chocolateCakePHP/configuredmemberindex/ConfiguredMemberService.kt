package com.daveme.chocolateCakePHP.configuredmemberindex

import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.absoluteClassName
import com.daveme.chocolateCakePHP.cake.cakeTwoPluginName
import com.daveme.chocolateCakePHP.findPluginConfigByName
import com.daveme.chocolateCakePHP.PhpFilesModificationTracker
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import java.util.concurrent.ConcurrentHashMap

/**
 * The resolved type of one configured member, whether an `initialize()` load (or a property
 * array) in the receiver's chain determined it, and the kinds the member cannot be because
 * every receiver's configuration of them is known and does not load it.
 */
data class MemberTypeResult(
    val classFqns: Set<String>,
    val classes: List<PhpClass>,
    val hasInitializeLoad: Boolean,
    val excludedKinds: Set<MemberKind> = emptySet(),
) {
    val type: PhpType
        get() = PhpType().apply { classFqns.forEach { add(it) } }
}

/**
 * The configured members of a set of receiver classes, by alias, together with the kinds
 * that every receiver's chain loads in `initialize()` or a property array.
 */
data class LoadedMembers(
    val members: Map<String, MemberTypeResult>,
    val initializeKinds: Set<MemberKind>,
)

/**
 * The members one receiver's class chain configures, by alias, with whether the configuration
 * determined each one, and the kinds it determines completely.
 */
internal class ChainResolution(
    val members: Map<String, Pair<Set<String>, Boolean>>,
    val initializeKinds: Set<MemberKind>,
    val excludesUnloaded: Set<MemberKind> = emptySet(),
)

/**
 * The rule for which members a receiver's class chain configures.
 */
internal interface ChainResolver {
    fun resolve(kinds: Set<MemberKind>, alias: String?, resolveLoad: (MemberLoad) -> List<String>): ChainResolution
}

/**
 * An association configured on a table class, with the class in the table's chain that configures it.
 */
data class TableAssociationLoad(
    val declaringClassFqn: String,
    val load: MemberLoad,
)

/**
 * A behavior attached to a table class: the load in force for its alias, and the classes it
 * resolves to.
 */
data class AttachedBehavior(
    val alias: String,
    val load: MemberLoad,
    val classes: List<PhpClass>,
    val isInitializeLoad: Boolean,
)

object ConfiguredMemberService {

    private const val CAKE_ORM_BEHAVIOR_NAMESPACE = "\\Cake\\ORM\\Behavior"

    private val BEHAVIORS_CACHE_KEY = Key.create<CachedValue<ConcurrentHashMap<String, List<Pair<MemberLoad, Boolean>>>>>(
        "com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService.behaviors"
    )

    private const val CAKE_VIEW_CLASS = "\\Cake\\View\\View"

    private val frameworkBaseClasses = setOf(
        "\\Cake\\Controller\\Controller",
        CAKE_VIEW_CLASS,
        "\\Cake\\View\\Helper",
        "\\Cake\\ORM\\Table",
    ).map { it.lowercase() }.toSet()

    /**
     * The type of [alias] on receivers of type [classFqns], or null when no class in any
     * receiver's chain loads it. [ignoredProperties] gives the configuring properties that have
     * no effect on a receiver.
     */
    fun memberType(
        project: Project,
        classFqns: Collection<String>,
        kinds: Set<MemberKind>,
        alias: String,
        ignoredProperties: (String) -> Set<String> = { emptySet() }
    ): MemberTypeResult? {
        val resolutions = specificReceivers(classFqns).map {
            resolveChain(project, it, kinds, alias, ignoredProperties(it))
        }
        return combine(project, resolutions, kinds, alias)
    }

    /**
     * The type of [alias] on [classFqn], leaving out what the chain's `initialize()` methods load.
     */
    fun memberTypeOutsideInitialize(
        project: Project,
        classFqn: String,
        kinds: Set<MemberKind>,
        alias: String,
        ignoredProperties: Set<String> = emptySet()
    ): MemberTypeResult? {
        val resolution = resolveChain(project, classFqn, kinds, alias, ignoredProperties) {
            it.source == LoadSource.PROPERTY_ARRAY || !it.isInitializeLoad
        }
        return combine(project, listOf(resolution), kinds, alias)
    }

    fun loadedMembers(
        project: Project,
        classFqns: Collection<String>,
        kinds: Set<MemberKind>,
        ignoredProperties: (String) -> Set<String> = { emptySet() }
    ): LoadedMembers {
        val resolutions = specificReceivers(classFqns).map {
            resolveChain(project, it, kinds, null, ignoredProperties(it))
        }
        val aliases = resolutions.flatMap { it.members.keys }.toSet()
        val members = aliases.mapNotNull { alias ->
            combine(project, resolutions, kinds, alias)?.let { alias to it }
        }.toMap()
        // A receiver without initialize loads of a kind still has that kind's members by convention:
        val initializeKinds = resolutions.map { it.initializeKinds }
            .reduceOrNull { kinds, other -> kinds intersect other }
            .orEmpty()
        return LoadedMembers(members, initializeKinds)
    }

    /**
     * The classes a single load resolves to.
     */
    fun loadClasses(project: Project, load: MemberLoad): List<PhpClass> {
        val phpIndex = PhpIndex.getInstance(project)
        return resolveLoad(phpIndex, Settings.getInstance(project), load)
            .flatMap { phpIndex.getClassesByFQN(it) }
    }

    /**
     * The associations configured by the chains of [tableFqns]. As in [InitializeChainResolver],
     * the nearest class's `initialize()` loads of an alias replace its ancestors', and loads
     * outside `initialize()` are kept from every class.
     */
    fun associationLoads(project: Project, tableFqns: Collection<String>): List<TableAssociationLoad> {
        val phpIndex = PhpIndex.getInstance(project)
        val fileBasedIndex = FileBasedIndex.getInstance()
        val scope = GlobalSearchScope.allScope(project)
        return tableFqns.flatMap { tableFqn ->
            val nearestInitialize = mutableMapOf<String, String>()
            classChain(phpIndex, tableFqn).flatMap { fqn ->
                val loads = fileBasedIndex.getValues(CONFIGURED_MEMBER_INDEX_KEY, fqn, scope)
                    .flatMap { it.loads }
                    .filter { it.kind == MemberKind.ASSOCIATION && it.associationType != null }
                loads.filter { it.isInitializeLoad }.forEach { nearestInitialize.putIfAbsent(it.alias, fqn) }
                loads.filter { !it.isInitializeLoad || nearestInitialize[it.alias] == fqn }
                    .map { TableAssociationLoad(fqn, it) }
            }
        }
    }

    /**
     * The behaviors the chain of [tableFqn] attaches, one per alias for the `initialize()` loads
     * and one per load for the others.
     */
    fun behaviors(project: Project, tableFqn: String): List<AttachedBehavior> {
        val tracker = project.getService(PhpFilesModificationTracker::class.java)
        tracker.ensureArmed()
        val cache = CachedValuesManager.getManager(project).getCachedValue(
            project,
            BEHAVIORS_CACHE_KEY,
            {
                CachedValueProvider.Result.create(
                    ConcurrentHashMap<String, List<Pair<MemberLoad, Boolean>>>(),
                    tracker,
                    Settings.getInstance(project).modificationTracker,
                )
            },
            false
        )
        val loads = cache.getOrPut(tableFqn) { behaviorLoadsInForce(project, tableFqn) }
        val phpIndex = PhpIndex.getInstance(project)
        val settings = Settings.getInstance(project)
        return loads.map { (load, isInitializeLoad) ->
            AttachedBehavior(
                alias = load.alias,
                load = load,
                classes = resolveLoad(phpIndex, settings, load).flatMap { phpIndex.getClassesByFQN(it) },
                isInitializeLoad = isInitializeLoad,
            )
        }
    }

    // A repeated load of an alias does not replace the first one to run, which is the farthest
    // ancestor's by the convention of calling parent::initialize() first; a plain repeat changes
    // nothing, so a load with options wins over an ancestor's plain one.
    private fun behaviorLoadsInForce(project: Project, tableFqn: String): List<Pair<MemberLoad, Boolean>> {
        val phpIndex = PhpIndex.getInstance(project)
        val chain = classChain(phpIndex, tableFqn)
        if (Settings.getInstance(project).cake2Enabled && CakeTwoBase.ofChain(phpIndex, chain) != null) {
            return emptyList()
        }
        val farthestFirst = chainConfigurations(project, chain).reversed()
            .flatMap { configuration -> configuration.loads.filter { it.kind == MemberKind.BEHAVIOR } }
        return farthestFirst.map { it.alias }.distinct().flatMap { alias ->
            val loads = farthestFirst.filter { it.alias == alias }
            val initializeLoads = loads.filter { it.isInitializeLoad }
            if (initializeLoads.isEmpty()) {
                loads.map { it to false }
            } else {
                listOf((initializeLoads.firstOrNull { it.carriesOptions() } ?: initializeLoads.first()) to true)
            }
        }
    }

    private fun MemberLoad.carriesOptions(): Boolean =
        className != null || hasUnreadClassName ||
            methodAliases != null || hasUnreadMethodAliases ||
            finderAliases != null || hasUnreadFinderAliases

    // The configuration of each class in [chain] as the index has it, in the chain's order.
    private fun chainConfigurations(project: Project, chain: List<String>): List<ClassConfiguration> {
        val fileBasedIndex = FileBasedIndex.getInstance()
        val scope = GlobalSearchScope.allScope(project)
        return chain.map { fqn ->
            val values = fileBasedIndex.getValues(CONFIGURED_MEMBER_INDEX_KEY, fqn, scope)
            ClassConfiguration(values.flatMap { it.loads }, values.flatMap { it.declarations })
        }
    }

    // The framework base classes say nothing about which members a receiver has.
    private fun specificReceivers(classFqns: Collection<String>): List<String> =
        classFqns.filter { it.lowercase() !in frameworkBaseClasses }

    // A receiver whose chain does not load the alias falls back to convention, unless its
    // configuration rules the alias out, so the result only counts as an initialize load when
    // every receiver had one or ruled it out.
    private fun combine(
        project: Project,
        resolutions: List<ChainResolution>,
        kinds: Set<MemberKind>,
        alias: String
    ): MemberTypeResult? {
        val perReceiver = resolutions.map { it.members[alias] }
        val excluded = resolutions.map { it.excludesUnloaded.containsAll(kinds) }
        val excludedKinds = if (perReceiver.all { it == null }) {
            resolutions.map { it.excludesUnloaded intersect kinds }
                .reduceOrNull { all, other -> all intersect other }
                .orEmpty()
        } else {
            emptySet()
        }
        if (perReceiver.all { it == null } && excludedKinds.isEmpty()) {
            return null
        }
        val fqns = perReceiver.filterNotNull().flatMap { it.first }.toSet()
        val phpIndex = PhpIndex.getInstance(project)
        return MemberTypeResult(
            classFqns = fqns,
            classes = fqns.flatMap { phpIndex.getClassesByFQN(it) },
            hasInitializeLoad = perReceiver.indices.all { index ->
                perReceiver[index]?.second ?: excluded[index]
            },
            excludedKinds = excludedKinds,
        )
    }

    private fun resolveChain(
        project: Project,
        classFqn: String,
        kinds: Set<MemberKind>,
        alias: String?,
        ignoredProperties: Set<String>,
        keepLoad: (MemberLoad) -> Boolean = { true }
    ): ChainResolution {
        val phpIndex = PhpIndex.getInstance(project)
        val settings = Settings.getInstance(project)
        val fileBasedIndex = FileBasedIndex.getInstance()
        val scope = GlobalSearchScope.allScope(project)

        // Most-derived class first:
        val chain = classChain(phpIndex, classFqn)
        val ignored = if (isViewChain(phpIndex, chain)) ignoredProperties + HELPERS_PROPERTY else ignoredProperties
        val configurations = chain.map { fqn ->
            val values = fileBasedIndex.getValues(CONFIGURED_MEMBER_INDEX_KEY, fqn, scope)
            ClassConfiguration(
                values.flatMap { it.loads }.filter { (it.source != LoadSource.PROPERTY_ARRAY || it.property !in ignored) && keepLoad(it) },
                values.flatMap { it.declarations }.filter { it.property !in ignored }
            )
        }
        val cakeTwoBase = if (settings.cake2Enabled) CakeTwoBase.ofChain(phpIndex, chain) else null
        if (cakeTwoBase != null) {
            val pluginName = if (cakeTwoBase == CakeTwoBase.CONTROLLER)
                cakeTwoPluginOfClass(project, phpIndex, settings, classFqn)
            else
                null
            return CakeTwoChainResolver(cakeTwoBase, chain, configurations, pluginName)
                .resolve(kinds, alias) { resolveCakeTwoLoad(it) }
        }
        return InitializeChainResolver(configurations).resolve(kinds, alias) { resolveLoad(phpIndex, settings, it) }
    }

    /**
     * Whether [chain] extends the framework's view class. A view class resolves as the framework
     * renders it: the view builder always passes helpers, which replaces its `$helpers` property.
     */
    private fun isViewChain(phpIndex: PhpIndex, chain: List<String>): Boolean =
        chain.any { fqn ->
            phpIndex.getClassesByFQN(fqn).any { it.superFQN.equals(CAKE_VIEW_CLASS, ignoreCase = true) }
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
        if (load.kind == MemberKind.BEHAVIOR) {
            return resolveBehaviorLoad(phpIndex, settings, load)
        }
        val className = load.className
        if (className != null && className.contains('\\')) {
            return listOf(className.absoluteClassName())
        }
        val name = className ?: load.loadedName
        val dotIndex = name.indexOf('.')
        if (dotIndex > 0) {
            val shortName = name.substring(dotIndex + 1)
            val pluginConfig = settings.findPluginConfigByName(name.substring(0, dotIndex))
            if (pluginConfig is CakeThreePluginConfig) {
                return listOf(classFqn(pluginConfig.namespace, load.kind, shortName))
            }
            return conventionCandidates(phpIndex, settings, load.kind, shortName)
        }
        return conventionCandidates(phpIndex, settings, load.kind, name)
    }

    /**
     * `App::className()`: a plugin-dotted name is only under that plugin, and a bare name is the
     * first of the app's class and the core's that exists.
     */
    private fun resolveBehaviorLoad(phpIndex: PhpIndex, settings: Settings, load: MemberLoad): List<String> {
        val className = load.className
        if (className != null && className.contains('\\')) {
            return listOf(className.absoluteClassName())
        }
        val name = className ?: load.loadedName
        val dotIndex = name.indexOf('.')
        if (dotIndex > 0) {
            val plugin = name.substring(0, dotIndex)
            val namespace = (settings.findPluginConfigByName(plugin) as? CakeThreePluginConfig)?.namespace
                ?: ("\\" + plugin.replace('/', '\\'))
            return listOf(classFqn(namespace, MemberKind.BEHAVIOR, name.substring(dotIndex + 1)))
        }
        return listOf(
            classFqn(settings.appNamespace, MemberKind.BEHAVIOR, name),
            "$CAKE_ORM_BEHAVIOR_NAMESPACE\\${name}Behavior",
        ).firstOrNull { phpIndex.getClassesByFQN(it).isNotEmpty() }
            ?.let { listOf(it) }
            .orEmpty()
    }

    private fun cakeTwoPluginOfClass(project: Project, phpIndex: PhpIndex, settings: Settings, classFqn: String): String? {
        val projectDir = project.guessProjectDir()
        return phpIndex.getClassesByFQN(classFqn).firstNotNullOfOrNull { phpClass ->
            phpClass.containingFile?.virtualFile?.let { cakeTwoPluginName(it, projectDir, settings) }
        }
    }

    private fun resolveCakeTwoLoad(load: MemberLoad): List<String> =
        listOf(cakeTwoClassFqn(load.kind, (load.className ?: load.loadedName).substringAfterLast('.')))

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
        settings.cakeThreePluginConfigs.mapTo(namespaces) { it.namespace }

        val candidates = namespaces.map { classFqn(it, kind, name) }.toMutableList()
        if (settings.cake2Enabled) {
            candidates += cakeTwoClassFqn(kind, name)
        }
        val distinct = candidates.distinct()
        val existing = distinct.filter { phpIndex.getClassesByFQN(it).isNotEmpty() }
        return existing.ifEmpty { distinct }
    }

    // CakePHP 2 classes are global, whichever plugin they come from.
    private fun cakeTwoClassFqn(kind: MemberKind, name: String): String =
        when (kind) {
            MemberKind.COMPONENT -> "\\${name}Component"
            MemberKind.HELPER -> "\\${name}Helper"
            MemberKind.TABLE, MemberKind.ASSOCIATION -> "\\$name"
            MemberKind.BEHAVIOR -> "\\${name}Behavior"
        }

    private fun classFqn(namespace: String, kind: MemberKind, name: String): String {
        val path = when (kind) {
            MemberKind.COMPONENT -> "Controller\\Component\\${name}Component"
            MemberKind.HELPER -> "View\\Helper\\${name}Helper"
            MemberKind.TABLE, MemberKind.ASSOCIATION -> "Model\\Table\\${name}Table"
            MemberKind.BEHAVIOR -> "Model\\Behavior\\${name}Behavior"
        }
        return "${namespace.absoluteClassName()}\\$path"
    }
}
