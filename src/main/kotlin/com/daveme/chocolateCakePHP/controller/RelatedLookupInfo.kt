package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.Settings
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

data class RelatedLookupInfo(
    val project: Project,
    val settings: Settings,
    val controllerName: String,
    val file: PsiFile,
)