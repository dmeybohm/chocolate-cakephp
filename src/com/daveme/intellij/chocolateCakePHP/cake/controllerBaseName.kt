package com.daveme.intellij.chocolateCakePHP.cake

fun controllerBaseName(controllerClass: String): String? {
    return if (!controllerClass.endsWith("Controller")) {
        null
    } else controllerClass.substring(0, controllerClass.length - "Controller".length)
}