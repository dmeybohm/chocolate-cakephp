package com.daveme.intellij.chocolateCakePHP.cake

import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import java.util.ArrayList

fun controllerFieldClasses(fieldName: String, project: Project): Collection<PhpClass> {
    val result = ArrayList<PhpClass>()
    val phpIndex = PhpIndex.getInstance(project)
    val modelClasses = phpIndex.getClassesByFQN(fieldName)
    val componentClasses = phpIndex.getClassesByFQN(fieldName + "Component")
    result.addAll(modelClasses)
    result.addAll(componentClasses)
    return result
}