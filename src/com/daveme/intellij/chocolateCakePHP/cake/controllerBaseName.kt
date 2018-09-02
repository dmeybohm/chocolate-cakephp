package com.daveme.intellij.chocolateCakePHP.cake

fun controllerBaseName(controllerClass: String): String? =
    if (!controllerClass.endsWith("Controller"))
        null
    else
        controllerClass.substring(0, controllerClass.length - "Controller".length)