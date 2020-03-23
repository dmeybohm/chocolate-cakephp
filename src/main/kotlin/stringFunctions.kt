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

fun String.isCakeTemplate(settings: Settings): Boolean {
    val last = lastIndexOf('.')
    if (last > 0) {
        val ext = substring(last + 1)
        var result = false
        if (settings.cake3Enabled) {
            result = ext == settings.cakeTemplateExtension
        }
        if (settings.cake2Enabled) {
            result = result || ext == settings.cake2TemplateExtension
        }
        return result
    }
    return false
}

fun String.controllerBaseName(): String? =
    if (!endsWith("Controller"))
        null
    else
        substring(0, length - "Controller".length)

fun String.withStartingBackslash(): String =
    if (startsWith("\\")) this else "\\" + this
