package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.ControllerPath
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

data class RelatedLookupInfo(
    val project: Project,
    val settings: Settings,
    val controllerPath: ControllerPath,
    val file: PsiFile,
)