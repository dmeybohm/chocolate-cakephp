package com.daveme.intellij.chocolateCakePHP.util

fun String.startsWithUppercaseCharacter(): Boolean {
    return this.isNotEmpty() && Character.isUpperCase(this[0])
}

fun String.chopFromEnd(end: String): String =
    if (end == "" || !this.endsWith(end))
        this
    else
        this.substring(0, this.length - end.length)
