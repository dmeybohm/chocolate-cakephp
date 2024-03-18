package com.daveme.chocolateCakePHP

// This is used for augmenting some types with metainformation
// by attaching unused types and encoding the meta information in
// the names.
//
// I have the \Best php namespace for myself registered on
// packagist.org, so no one should use that one for this.
//
private const val PLUGIN_SPECIFIC_NAMESPACE = "\\Best\\ChocolateCakePHP"

//
// The app-specific namespaces are wrapped descending from this namespace,
// so we can use the type code already in PhpStorm to keep track of which
// class invoked the query builder.
//
private const val QUERY_BUILDER_PREFIX = "${PLUGIN_SPECIFIC_NAMESPACE}\\QueryBuilder"


fun String.wrapInPluginSpecificTypeForQueryBuilder(): String =
    QUERY_BUILDER_PREFIX + this

fun String.unwrapFromPluginSpecificTypeForQueryBuilder(): String =
    if (this.startsWith(QUERY_BUILDER_PREFIX))
        this.substring(QUERY_BUILDER_PREFIX.length)
    else
        this