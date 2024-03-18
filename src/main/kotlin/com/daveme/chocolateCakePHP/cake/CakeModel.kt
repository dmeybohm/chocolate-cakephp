package com.daveme.chocolateCakePHP.cake

import com.jetbrains.php.lang.psi.elements.PhpClass

val cake3NonQueryModelMethodNames = listOf(
    "toArray",
    "first",
    "last",
    "count",
    "all",
)

fun isCakeTwoModelClass(classes: Collection<PhpClass>): Boolean {
    return classes.any { phpClass -> phpClass.superFQN == "\\AppModel" }
}