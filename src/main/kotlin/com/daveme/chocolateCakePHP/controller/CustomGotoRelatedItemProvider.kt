package com.daveme.chocolateCakePHP.controller

import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.NotNullFunction

class CustomGotoRelatedItemProvider(project: Project, defaultViewFile: String) :
    NotNullFunction<PsiElement, MutableCollection<out GotoRelatedItem>> {

    override fun `fun`(dom: PsiElement): MutableCollection<out GotoRelatedItem> {
        TODO("Not yet implemented")
    }
}
