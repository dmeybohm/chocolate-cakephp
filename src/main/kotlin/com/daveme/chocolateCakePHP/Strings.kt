package com.daveme.chocolateCakePHP

fun String.startsWithUppercaseCharacter(): Boolean =
    this.isNotEmpty() && Character.isUpperCase(this[0])

fun String.chopFromEnd(end: String): String =
    if (end == "" || !this.endsWith(end))
        this
    else
        this.substring(0, this.length - end.length)

fun String.isControllerClass(): Boolean =
    this.contains("Controller") ||
        this.contains("\\Cake\\Controller\\Controller")

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

