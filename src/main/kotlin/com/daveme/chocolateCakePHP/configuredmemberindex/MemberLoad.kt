package com.daveme.chocolateCakePHP.configuredmemberindex

/**
 * The kind of class a configured member resolves to.
 */
enum class MemberKind {
    COMPONENT,
    TABLE,
    HELPER,
    ASSOCIATION,
}

/**
 * The syntax a configured member was loaded with.
 */
enum class LoadSource {
    METHOD_CALL,
    PROPERTY_ARRAY,
}

/**
 * One load of a dynamic property, such as `$this->loadComponent('Alias', [...])`, as it is
 * written in the source file.
 */
data class MemberLoad(
    val kind: MemberKind,
    val source: LoadSource,
    val alias: String,
    val loadedName: String,
    val className: String?,
    val methodName: String?,
    val offset: Int,
) {
    val isInitializeLoad: Boolean
        get() = source == LoadSource.PROPERTY_ARRAY ||
                methodName.equals("initialize", ignoreCase = true)
}

internal val methodCallSources: Map<String, MemberKind> = mapOf(
    "loadcomponent" to MemberKind.COMPONENT,
    "loadmodel" to MemberKind.TABLE,
    "loadhelper" to MemberKind.HELPER,
    "addhelper" to MemberKind.HELPER,
    "belongsto" to MemberKind.ASSOCIATION,
    "hasone" to MemberKind.ASSOCIATION,
    "hasmany" to MemberKind.ASSOCIATION,
    "belongstomany" to MemberKind.ASSOCIATION,
)

// Source methods whose second argument is not an options array, so cannot alias:
// loadModel($modelClass, $modelType).
internal val sourcesWithoutOptions: Set<String> = setOf(
    "loadmodel",
)

internal const val ADD_ASSOCIATIONS_METHOD = "addassociations"

internal val associationTypeKeys: Set<String> = setOf(
    "belongsTo",
    "hasOne",
    "hasMany",
    "belongsToMany",
)

internal val propertyArraySources: Map<String, MemberKind> = mapOf(
    "helpers" to MemberKind.HELPER,
)
