package com.daveme.intellij.chocolateCakePHP.cake

// TODO make this configurable
private const val TEMPLATE_EXT = "ctp"

fun isCakeTemplate(filename: String): Boolean {
    val last = filename.lastIndexOf('.')
    if (last > 0) {
        val ext = filename.substring(last + 1)
        return ext == TEMPLATE_EXT
    }
    return false
}