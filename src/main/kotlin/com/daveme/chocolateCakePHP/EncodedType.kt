package com.daveme.chocolateCakePHP


object EncodedType {

    // This is used for augmenting some types with metainformation
    // by attaching unused types and encoding the meta information in
    // the names.
    //
    // I have the \Best php namespace for myself registered on
    // packagist.org, so no one should use that one for this.
    //
    private const val ENCODED_TYPE_NAMESPACE = "\\Best\\ChocolateCakePHP"

    //
    // The app-specific namespaces are wrapped descending from this namespace,
    // so we can use the type code already in PhpStorm to keep track of which
    // class invoked the query builder.
    //
    private const val QUERY_BUILDER_PREFIX = "${ENCODED_TYPE_NAMESPACE}\\QueryBuilder"

    //
    // The app-specific namespace for dynamic tables - for tables without a definition
    // in the class. This allows attaching metadata to tables that don't have a definition.
    //
//    private const val DYNAMIC_TABLE_PREFIX = "${ENCODED_TYPE_NAMESPACE}\\Model\\Table\\"

    //
    // The app-specific namespace for dynamic tables - for tables without a definition
    // in the class. This allows attaching metadata to entities that don't have a definition.
    //
//    private const val DYNAMIC_ENTITY_PREFIX = "${ENCODED_TYPE_NAMESPACE}\\Model\\Entity\\"

    fun encodeForQueryBuilder(source: String): String =
        QUERY_BUILDER_PREFIX + source

    fun decodeQueryBuilder(encoded: String): String =
        if (encoded.startsWith(QUERY_BUILDER_PREFIX))
            encoded.substring(QUERY_BUILDER_PREFIX.length)
        else
            encoded

    fun isEncodedForQueryBuilder(wrapped: String): Boolean =
        wrapped.startsWith(QUERY_BUILDER_PREFIX)

//    fun encodeForDynamicTable(source: String): String =
//        DYNAMIC_TABLE_PREFIX + source
//
//    fun decodeDynamicTable(encoded: String): String =
//        if (encoded.startsWith(DYNAMIC_TABLE_PREFIX))
//            encoded.substring(DYNAMIC_TABLE_PREFIX.length)
//        else
//            encoded

}

