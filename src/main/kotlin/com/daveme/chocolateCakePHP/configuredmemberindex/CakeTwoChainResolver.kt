package com.daveme.chocolateCakePHP.configuredmemberindex

import com.daveme.chocolateCakePHP.Inflector
import com.jetbrains.php.PhpIndex

/**
 * The CakePHP 2 framework class a class chain ends in.
 */
internal enum class CakeTwoBase(val fqn: String) {
    CONTROLLER("\\Controller"),
    MODEL("\\Model"),
    HELPER("\\Helper"),
    VIEW("\\View");

    companion object {
        fun forClass(fqn: String): CakeTwoBase? =
            values().firstOrNull { it.fqn.equals(fqn, ignoreCase = true) }

        /**
         * The base of [chain], most-derived class first, when it is a CakePHP 2 chain.
         */
        fun ofChain(phpIndex: PhpIndex, chain: List<String>): CakeTwoBase? {
            for (fqn in chain) {
                forClass(fqn)?.let { return it }
                for (phpClass in phpIndex.getClassesByFQN(fqn)) {
                    phpClass.superFQN?.let { forClass(it) }?.let { return it }
                }
            }
            return null
        }
    }
}

/**
 * Resolves the members of a CakePHP 2 class chain, where a configuring property applies as a
 * whole: its nearest declaration wins, and controllers merge theirs with `AppController`'s.
 */
internal class CakeTwoChainResolver(
    private val base: CakeTwoBase,
    private val chain: List<String>,
    private val configurations: List<ClassConfiguration>,
    private val pluginName: String?,
) {

    private class Declared(
        val classIndex: Int,
        val declaration: PropertyDeclaration,
        val loads: List<MemberLoad>,
    ) {
        val isArray get() = declaration.value == DeclaredValue.ARRAY
    }

    private class KindLoads(val loads: List<MemberLoad>, val isKnown: Boolean)

    private val appControllerIndex = indexOfClass { it.equals("\\AppController", ignoreCase = true) }

    // Only a controller in a plugin merges that plugin's AppController:
    private val pluginAppControllerIndex = if (pluginName == null) -1 else indexOfClass {
        it.equals("\\${pluginName}AppController", ignoreCase = true)
    }

    fun resolve(kinds: Set<MemberKind>, alias: String?, resolveLoad: (MemberLoad) -> List<String>): ChainResolution {
        val members = mutableMapOf<String, Pair<Set<String>, Boolean>>()
        val knownKinds = mutableSetOf<MemberKind>()
        for (kind in kinds) {
            val kindLoads = kindLoads(kind) ?: continue
            if (kindLoads.isKnown) {
                knownKinds += kind
            }
            for (load in kindLoads.loads) {
                if (alias != null && load.alias != alias) {
                    continue
                }
                val isAuthoritative = when {
                    load.hasUnreadClassName -> false
                    // An association a property lists exists whatever the other properties hold:
                    kind == MemberKind.ASSOCIATION -> load.source == LoadSource.PROPERTY_ARRAY
                    else -> kindLoads.isKnown
                }
                val (fqns, existing) = members[load.alias] ?: (emptySet<String>() to false)
                members[load.alias] = (fqns + resolveLoad(load)) to (existing || isAuthoritative)
            }
        }
        return ChainResolution(
            members = members,
            initializeKinds = knownKinds,
            // Unlisted associations keep a type, since bindModel() calls on other receivers are not indexed:
            excludesUnloaded = knownKinds - MemberKind.ASSOCIATION,
        )
    }

    private fun kindLoads(kind: MemberKind): KindLoads? =
        when (base) {
            CakeTwoBase.CONTROLLER -> when (kind) {
                MemberKind.COMPONENT -> mergedWithAppController("components")
                MemberKind.HELPER -> mergedWithAppController("helpers")
                MemberKind.TABLE -> models()
                MemberKind.ASSOCIATION -> null
            }
            CakeTwoBase.HELPER -> if (kind == MemberKind.HELPER) ownValue("helpers") else null
            CakeTwoBase.MODEL -> if (kind == MemberKind.ASSOCIATION) associations() else null
            // The rendering controller's helpers replace the view's own:
            CakeTwoBase.VIEW -> null
        }

    private fun ownValue(property: String): KindLoads {
        val declared = nearest(property, 0)
            ?: return KindLoads(emptyList(), false)
        return KindLoads(declared.loads, declared.declaration.isComplete && declared.isArray)
    }

    /**
     * `Controller::_mergeControllerVars()`: the controller's value merged onto the plugin
     * `AppController`'s and then `AppController`'s, the child's entry winning for an alias.
     */
    private fun mergedWithAppController(property: String): KindLoads {
        val own = nearest(property, 0)
            ?: return KindLoads(emptyList(), false)
        val parents = listOfNotNull(
            appControllerIndex.takeIf { it > 0 }?.let { nearest(property, it) },
            pluginAppControllerIndex.takeIf { it > 0 }?.let { nearest(property, it) },
        ).filter { it.isArray }
        val merged = linkedMapOf<String, MemberLoad>()
        for (declared in parents + own) {
            for (load in declared.loads) {
                val parentLoad = merged[load.alias]
                // Options merge recursively, so a child entry without a className keeps the parent's:
                merged[load.alias] = if (parentLoad != null && load.className == null && !load.hasUnreadClassName) {
                    load.copy(className = parentLoad.className, hasUnreadClassName = parentLoad.hasUnreadClassName)
                } else {
                    load
                }
            }
        }
        val isKnown = own.isArray && (parents + own).all { it.declaration.isComplete }
        return KindLoads(merged.values.toList(), isKnown)
    }

    /**
     * The models a controller lazy-loads: its effective `$uses` after the merge rules of
     * `Controller::_mergeControllerVars()`, or its default model, plus its `loadModel()` calls.
     */
    private fun models(): KindLoads {
        val loadModelCalls = allLoads().filter {
            it.kind == MemberKind.TABLE && it.source == LoadSource.METHOD_CALL
        }
        val own = nearest("uses", 0)
            ?: return KindLoads(loadModelCalls, false)
        val app = appControllerIndex.takeIf { it > 0 }?.let { nearest("uses", it) }
        val plugin = pluginAppControllerIndex.takeIf { it > 0 }?.let { nearest("uses", it) }

        val uses = mutableListOf<MemberLoad>()
        var isKnown = own.declaration.isComplete
        when (own.declaration.value) {
            DeclaredValue.NULL, DeclaredValue.FALSE -> {}
            DeclaredValue.TRUE -> uses += defaultModelLoad()
            DeclaredValue.ARRAY -> {
                if (app != null && app.classIndex == own.classIndex) {
                    uses += defaultModelLoad()
                }
                uses += own.loads
            }
            DeclaredValue.STRING, DeclaredValue.OTHER -> {
                uses += own.loads
                isKnown = false
            }
        }
        val loadsModels = own.declaration.value != DeclaredValue.NULL &&
                own.declaration.value != DeclaredValue.FALSE
        if (loadsModels) {
            for (parent in listOfNotNull(plugin, app).filter { it.isArray }) {
                uses += parent.loads.filter { load -> uses.none { it.alias == load.alias } }
                isKnown = isKnown && parent.declaration.isComplete
            }
            if (uses.isEmpty()) {
                uses += defaultModelLoad()
            }
        }
        return KindLoads(uses + loadModelCalls, isKnown)
    }

    /**
     * Each association property's nearest declaration, unmerged, plus the chain's own
     * `bindModel()` calls.
     */
    private fun associations(): KindLoads {
        var isKnown = true
        val loads = mutableListOf<MemberLoad>()
        for (property in cakeTwoAssociationTypeKeys) {
            val declared = nearest(property, 0)
            if (declared == null) {
                isKnown = false
                continue
            }
            loads += declared.loads
            isKnown = isKnown && declared.declaration.isComplete &&
                    declared.declaration.value != DeclaredValue.OTHER
        }
        loads += allLoads().filter { it.source == LoadSource.BIND_MODEL }
        return KindLoads(loads, isKnown)
    }

    private fun defaultModelLoad(): MemberLoad {
        val controllerName = chain.first().substringAfterLast('\\').removeSuffix("Controller")
        val modelClass = Inflector.singularize(controllerName)
        return MemberLoad(
            kind = MemberKind.TABLE,
            source = LoadSource.PROPERTY_ARRAY,
            alias = modelClass,
            loadedName = modelClass,
            className = null,
            methodName = null,
            offset = -1,
            property = "uses",
        )
    }

    private fun nearest(property: String, fromIndex: Int): Declared? {
        for (index in fromIndex until configurations.size) {
            val configuration = configurations[index]
            val declaration = configuration.declarations.firstOrNull { it.property == property } ?: continue
            val loads = configuration.loads.filter {
                it.source == LoadSource.PROPERTY_ARRAY && it.property == property
            }
            return Declared(index, declaration, loads)
        }
        return null
    }

    private fun allLoads(): List<MemberLoad> = configurations.flatMap { it.loads }

    private fun indexOfClass(predicate: (String) -> Boolean): Int = chain.indexOfFirst(predicate)
}
