package com.daveme.chocolateCakePHP.cake.CakeModel

import com.jetbrains.php.lang.psi.elements.PhpClass

fun isCakeTwoModelClass(classes: Collection<PhpClass>): Boolean {
    return classes.any { phpClass -> phpClass.superFQN == "\\AppModel" }
}