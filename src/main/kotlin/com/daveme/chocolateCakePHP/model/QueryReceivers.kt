package com.daveme.chocolateCakePHP.model

import com.daveme.chocolateCakePHP.isAnyTableClass
import com.daveme.chocolateCakePHP.lookupCompleteType
import com.daveme.chocolateCakePHP.unwrapFromPluginSpecificTypeForQueryBuilder
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.MethodReference

/**
 * The table classes the receiver of a method call is, or is a query built from.
 */
fun queryReceiverTables(methodReference: MethodReference): List<String> {
    val project = methodReference.project
    val classReference = methodReference.classReference ?: return emptyList()
    return classReference.type.lookupCompleteType(project, PhpIndex.getInstance(project), null).types
        .filter { it.startsWith("\\") && it.isAnyTableClass() }
        .map { it.unwrapFromPluginSpecificTypeForQueryBuilder() }
        .distinct()
}
