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