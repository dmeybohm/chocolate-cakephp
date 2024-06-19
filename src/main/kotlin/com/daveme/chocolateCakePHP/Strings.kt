package com.daveme.chocolateCakePHP

import java.util.*

fun String.startsWithUppercaseCharacter(): Boolean =
    this.isNotEmpty() && Character.isUpperCase(this[0])

fun String.removeFromEnd(end: String, ignoreCase: Boolean = false): String =
    if (end == "" || !this.endsWith(end, ignoreCase))
        this
    else
        this.substring(0, this.length - end.length)

fun String.removeFromStart(start: String, ignoreCase: Boolean = false): String =
    if (start == "" || !this.startsWith(start, ignoreCase = ignoreCase))
        this
    else
        this.substring(start.length)

fun String.isAnyControllerClass(): Boolean =
    this.endsWith("Controller", ignoreCase = true)

fun String.isAnyTableClass(): Boolean =
    this.endsWith("Table", ignoreCase = true)

fun String.tableToEntityClass(): String {
    val parts = this.removeFromEnd("Table", ignoreCase = true)
        .split('\\')
    if (parts.size < 2) {
        return this
    }
    val last = parts.last().singularize()
    val newParts = parts.dropLast(2) + "Entity" + last
    return newParts.joinToString("\\" )
}

fun String.isTableLocatorInterface(): Boolean =
    this.equals("\\Cake\\ORM\\Locator\\LocatorInterface", ignoreCase = true)

fun String.isTopLevelTableClass(): Boolean =
    this.equals("\\Cake\\ORM\\Table", ignoreCase = true)

fun String.isQueryObject(): Boolean =
    this.equals("\\Cake\\ORM\\SelectQuery", ignoreCase = true) ||
            this.equals("\\Cake\\ORM\\Query", ignoreCase = true)

fun String.hasGetTableLocatorMethodCall(): Boolean =
    this.contains(".getTableLocator", ignoreCase = true)

fun String.absoluteClassName(): String =
    if (this.startsWith("\\"))
        this
    else
        "\\${this}"

fun String.controllerBaseName(): String? =
    if (!endsWith("Controller"))
        null
    else
        substring(0, length - "Controller".length)

val matchUpperCase = Regex("([A-Z])")
fun String.camelCaseToUnderscore(): String {
    val removeFirst = this.isNotEmpty() && this[0].isUpperCase()
    val result = matchUpperCase.replace(this, "_$1")
    return if (removeFirst)
        result.substring(1).lowercase()
    else
        result.lowercase()
}

fun String.conditionalCamelCaseToUnderscore(convert: Boolean): String =
    if (convert)
        this.camelCaseToUnderscore()
    else
        this

fun String.latinCapitalize(): String {
    return replaceFirstChar {
        if (it.isLowerCase())
            it.titlecase(Locale.US)
        else
            it.toString()
    }
}

fun String.removeQuotes(): String =
    if (this.length < 2)
        this
    else
        this.substring(1, this.length - 1)

fun String.underscoreToCamelCase(): String {
    return this.split('_')
        .mapIndexed { index, s ->
            if (index == 0) s
            else s.latinCapitalize()
        }
        .joinToString("")
}

fun String.mneumonicEscape(): String =
    this.replace("_", "__")

fun String.singularize(): String {
    return Inflector.singularize(this)
}