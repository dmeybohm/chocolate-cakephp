package com.daveme.chocolateCakePHP.behavior

import com.daveme.chocolateCakePHP.PhpFilesModificationTracker
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberLoad
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.util.concurrent.ConcurrentHashMap

/**
 * A method a behavior mixes into the tables it is attached to, under the alias it is called by.
 */
class BehaviorMember(
    val alias: String,
    val method: Method,
)

/**
 * The methods and finders one behavior class mixes into a table, each keyed by its lowercase alias.
 */
class BehaviorMemberMap(
    val methods: Map<String, BehaviorMember>,
    val finders: Map<String, BehaviorMember>,
)

/**
 * The alias map of one of a behavior's `implementedMethods` or `implementedFinders` keys before
 * any load options apply: from `$_defaultConfig` when [isConfigured], else from reflection.
 * [aliases] is null when the configured map cannot be read.
 */
private class DefaultAliases(
    val aliases: Map<String, String>?,
    val isConfigured: Boolean,
)

private class DefaultMaps(
    val methods: DefaultAliases,
    val finders: DefaultAliases,
)

object BehaviorMembers {

    private const val BEHAVIOR_BASE_CLASS = "\\Cake\\ORM\\Behavior"

    private const val DEFAULT_CONFIG_PROPERTY = "_defaultConfig"

    // Behavior::$_eventMap, the callbacks implementedEvents() lists by default:
    private val eventMethods = setOf(
        "beforeMarshal", "afterMarshal", "beforeFind", "beforeSave", "afterSave",
        "afterSaveCommit", "beforeDelete", "afterDelete", "afterDeleteCommit",
        "buildValidator", "buildRules", "beforeRules", "afterRules",
    )

    // The public methods of Cake\ORM\Behavior, for when the framework is not attached:
    private val fallbackBaseMethods = setOf(
        "__construct", "initialize", "getTable", "table", "verifyConfig", "implementedEvents",
        "implementedFinders", "implementedMethods", "setConfig", "getConfig", "getConfigOrFail",
        "configShallow",
    )

    private val CACHE_KEY = Key.create<CachedValue<ConcurrentHashMap<String, DefaultMaps>>>(
        "com.daveme.chocolateCakePHP.behavior.BehaviorMembers"
    )

    /**
     * The members [behaviorClass] mixes in when loaded with the options of [load].
     */
    fun of(project: Project, behaviorClass: PhpClass, load: MemberLoad?): BehaviorMemberMap {
        val defaults = defaultMapsOf(project, behaviorClass)
        return BehaviorMemberMap(
            methods = resolve(
                behaviorClass,
                applyOption(defaults.methods, load?.methodAliases, load?.hasUnreadMethodAliases == true)
            ),
            finders = resolve(
                behaviorClass,
                applyOption(defaults.finders, load?.finderAliases, load?.hasUnreadFinderAliases == true)
            ),
        )
    }

    private fun defaultMapsOf(project: Project, behaviorClass: PhpClass): DefaultMaps {
        val tracker = project.getService(PhpFilesModificationTracker::class.java)
        tracker.ensureArmed()
        val cache = CachedValuesManager.getManager(project).getCachedValue(
            project,
            CACHE_KEY,
            { CachedValueProvider.Result.create(ConcurrentHashMap<String, DefaultMaps>(), tracker) },
            false
        )
        return cache.getOrPut(behaviorClass.fqn) { buildDefaultMaps(project, behaviorClass) }
    }

    private fun buildDefaultMaps(project: Project, behaviorClass: PhpClass): DefaultMaps {
        val defaultConfig = defaultConfigOf(behaviorClass)
        val configuredMethods = configuredAliases(defaultConfig, "implementedMethods")
        val configuredFinders = configuredAliases(defaultConfig, "implementedFinders")
        if (configuredMethods != null && configuredFinders != null) {
            return DefaultMaps(configuredMethods, configuredFinders)
        }
        val (reflectedMethods, reflectedFinders) = reflect(project, behaviorClass)
        return DefaultMaps(
            methods = configuredMethods ?: DefaultAliases(reflectedMethods, isConfigured = false),
            finders = configuredFinders ?: DefaultAliases(reflectedFinders, isConfigured = false),
        )
    }

    // The default value of the nearest declaration of $_defaultConfig below the base class, which
    // is unknown when it is not an array literal.
    private fun defaultConfigOf(behaviorClass: PhpClass): DefaultConfig {
        for (each in behaviorChain(behaviorClass)) {
            val field = each.findOwnFieldByName(DEFAULT_CONFIG_PROPERTY, false) ?: continue
            return DefaultConfig(field.defaultValue as? ArrayCreationExpression, isKnown = field.defaultValue is ArrayCreationExpression)
        }
        return DefaultConfig(null, isKnown = true)
    }

    private class DefaultConfig(val array: ArrayCreationExpression?, val isKnown: Boolean)

    // The configured map of [key], or null when the key is not set and reflection applies.
    private fun configuredAliases(defaultConfig: DefaultConfig, key: String): DefaultAliases? {
        if (!defaultConfig.isKnown) {
            return DefaultAliases(null, isConfigured = true)
        }
        val element = defaultConfig.array?.hashElements
            ?.firstOrNull { (it.key as? StringLiteralExpression)?.contents == key }
            ?: return null
        val value = element.value as? ArrayCreationExpression
            ?: return DefaultAliases(null, isConfigured = true)
        val aliases = linkedMapOf<String, String>()
        if (value.children.any { it is PhpPsiElement && it !is ArrayHashElement }) {
            return DefaultAliases(null, isConfigured = true)
        }
        for (entry in value.hashElements) {
            val alias = (entry.key as? StringLiteralExpression)?.contents
            val method = (entry.value as? StringLiteralExpression)?.contents
            if (alias.isNullOrEmpty() || method.isNullOrEmpty()) {
                return DefaultAliases(null, isConfigured = true)
            }
            aliases[alias] = method
        }
        return DefaultAliases(aliases, isConfigured = true)
    }

    /**
     * `Behavior::_reflectionCache()`: the public methods of the class and its parents below the
     * base class, with their traits, that the base class does not have and that are not event
     * callbacks, split into finders and methods.
     */
    private fun reflect(project: Project, behaviorClass: PhpClass): Pair<Map<String, String>, Map<String, String>> {
        val baseMethods = PhpIndex.getInstance(project).getClassesByFQN(BEHAVIOR_BASE_CLASS)
            .flatMap { base -> base.methods.map { it.name } }
            .toSet()
            .ifEmpty { fallbackBaseMethods }
        val methods = linkedMapOf<String, String>()
        val finders = linkedMapOf<String, String>()
        val seen = mutableSetOf<String>()
        for (each in behaviorChain(behaviorClass)) {
            for (method in methodsOf(each)) {
                val name = method.name
                if (!method.access.isPublic || !seen.add(name.lowercase())) {
                    continue
                }
                if (name in baseMethods || name in eventMethods || name.startsWith("__")) {
                    continue
                }
                if (name.startsWith("find")) {
                    val finder = name.substring(4).replaceFirstChar { it.lowercaseChar() }
                    if (finder.isNotEmpty()) {
                        finders.putIfAbsent(finder, name)
                    }
                } else {
                    methods.putIfAbsent(name, name)
                }
            }
        }
        return methods to finders
    }

    /**
     * `Behavior::_resolveMethodAliases()` and the fallback in `implementedMethods()`: options
     * merge by method name onto a configured map, and replace a reflected one.
     */
    private fun applyOption(defaults: DefaultAliases, custom: Map<String, String>?, isUnread: Boolean): Map<String, String> {
        val base = defaults.aliases
        if (isUnread || base == null) {
            return emptyMap()
        }
        if (custom == null) {
            return base
        }
        if (!defaults.isConfigured || custom.isEmpty()) {
            return custom
        }
        // array_flip() keeps the first position of a repeated value and the last key for it:
        val aliasByMethod = linkedMapOf<String, String>()
        custom.forEach { (alias, method) -> aliasByMethod[method] = alias }
        val defaultAliasByMethod = linkedMapOf<String, String>()
        base.forEach { (alias, method) -> defaultAliasByMethod[method] = alias }
        defaultAliasByMethod.forEach { (method, alias) -> aliasByMethod.putIfAbsent(method, alias) }
        val result = linkedMapOf<String, String>()
        aliasByMethod.forEach { (method, alias) -> result[alias] = method }
        return result
    }

    private fun resolve(behaviorClass: PhpClass, aliases: Map<String, String>): Map<String, BehaviorMember> {
        val result = linkedMapOf<String, BehaviorMember>()
        for ((alias, methodName) in aliases) {
            val method = behaviorClass.findMethodByName(methodName) ?: continue
            result.putIfAbsent(alias.lowercase(), BehaviorMember(alias, method))
        }
        return result
    }

    private fun behaviorChain(behaviorClass: PhpClass): Sequence<PhpClass> {
        val visited = mutableSetOf<String>()
        return generateSequence(behaviorClass) { it.superClass }
            .takeWhile { !it.fqn.equals(BEHAVIOR_BASE_CLASS, ignoreCase = true) && visited.add(it.fqn.lowercase()) }
    }

    // A class's own method overrides the same method from a trait it uses:
    private fun methodsOf(phpClass: PhpClass, visited: MutableSet<String> = mutableSetOf()): List<Method> {
        if (!visited.add(phpClass.fqn.lowercase())) {
            return emptyList()
        }
        return phpClass.ownMethods.toList() + phpClass.traits.flatMap { methodsOf(it, visited) }
    }
}
