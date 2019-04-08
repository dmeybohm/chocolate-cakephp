package com.daveme.intellij.chocolateCakePHP.cake

import com.daveme.intellij.chocolateCakePHP.Settings

fun isCakeTemplate(settings: Settings, filename: String): Boolean {
    val last = filename.lastIndexOf('.')
    if (last > 0) {
        val ext = filename.substring(last + 1)
        return ext == settings.cakeTemplateExtension
    }
    return false
}

fun viewRelativeTemplatePath(settings: Settings, controllerName: String, controllerAction: String): String {
    return "View/$controllerName/$controllerAction.${settings.cakeTemplateExtension}"
}

fun viewElementRelativePath(settings: Settings, elementPath: String): String {
    return "View/Elements/$elementPath.${settings.cakeTemplateExtension}"
}
