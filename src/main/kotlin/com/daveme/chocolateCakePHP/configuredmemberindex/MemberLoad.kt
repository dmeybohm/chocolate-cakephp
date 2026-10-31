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
    BIND_MODEL,
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
    val property: String? = null,
    val hasUnreadClassName: Boolean = false,
) {
    val isInitializeLoad: Boolean
        get() = source == LoadSource.PROPERTY_ARRAY ||
                (source == LoadSource.METHOD_CALL && methodName.equals("initialize", ignoreCase = true))
}

/**
 * The form of the value a configuring property is declared with.
 */
enum class DeclaredValue {
    ARRAY,
    STRING,
    TRUE,
    FALSE,
    NULL,
    OTHER,
}

/**
 * A declaration of a property that configures members, such as `public $uses = array('Movie')`,
 * whether or not it loads anything.
 */
data class PropertyDeclaration(
    val property: String,
    val kind: MemberKind,
    val value: DeclaredValue,
    val isComplete: Boolean,
    val offset: Int,
)

/**
 * The member loads and configuring property declarations of one class.
 */
data class ClassConfiguration(
    val loads: List<MemberLoad>,
    val declarations: List<PropertyDeclaration>,
)

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

internal const val VIEW_BUILDER_METHOD = "viewbuilder"

internal const val ADD_HELPER_METHOD = "addhelper"

// ViewBuilder methods that take an array of helpers:
internal val viewBuilderHelperArrayMethods: Set<String> = setOf(
    "addhelpers",
    "sethelpers",
    "helpers",
)

internal const val HELPERS_PROPERTY = "helpers"

internal const val ADD_ASSOCIATIONS_METHOD = "addassociations"

internal const val BIND_MODEL_METHOD = "bindmodel"

internal const val HABTM_PROPERTY = "hasAndBelongsToMany"

internal val associationTypeKeys: Set<String> = setOf(
    "belongsTo",
    "hasOne",
    "hasMany",
    "belongsToMany",
)

internal val cakeTwoAssociationTypeKeys: Set<String> = setOf(
    "belongsTo",
    "hasOne",
    "hasMany",
    HABTM_PROPERTY,
)

internal val propertyArraySources: Map<String, MemberKind> = mapOf(
    HELPERS_PROPERTY to MemberKind.HELPER,
    "components" to MemberKind.COMPONENT,
    "uses" to MemberKind.TABLE,
) + cakeTwoAssociationTypeKeys.associateWith { MemberKind.ASSOCIATION }
