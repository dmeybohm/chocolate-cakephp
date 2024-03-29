package com.daveme.chocolateCakePHP

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

fun String.isTopLevelTableClass(): Boolean =
    this.equals("\\Cake\\ORM\\Table", ignoreCase = true)

fun String.isQueryObject(): Boolean =
    this.equals("\\Cake\\ORM\\SelectQuery", ignoreCase = true) ||
            this.equals("\\Cake\\ORM\\Query", ignoreCase = true)

fun String.hasGetTableLocatorMethodCall(): Boolean =
    this.contains(".getTableLocator")

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

