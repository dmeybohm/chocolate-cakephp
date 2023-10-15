package com.daveme.chocolateCakePHP.controller

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class CreateViewFile : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        System.out.println("actionPerformed in CreateViewFile")
    }

}