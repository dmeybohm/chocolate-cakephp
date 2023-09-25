package com.daveme.chocolateCakePHP.ui

import com.daveme.chocolateCakePHP.Settings
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class ConfigForm(val project: Project) : SearchableConfigurable {

    override fun createComponent(): JComponent {
        val settings = Settings.getInstance(project)
        return panel {
            group("CakePHP 3+") {
                lateinit var cake3Enabled: Cell<JBCheckBox>
                row() {
                    cake3Enabled = checkBox("Enable support for CakePHP 3+")
                        .bindSelected(settings.state::cake3Enabled)

                }
                row("App namespace") {
                    textField()
                        .comment("Classes under this namespace will be available for autocomplete")
                        .bindText(settings.state::appNamespace)
                    button("Reset") {
                        println("Reset")
                    }
                }.visibleIf(cake3Enabled.selected)
                row("App directory") {
                    textField()
                        .comment("This is the top-level directory where your source code is located.")
                        .bindText(settings.state::appDirectory)
                    button("Reset") {
                        println("Reset")
                    }
                }.visibleIf(cake3Enabled.selected)
                row("Template extension") {
                    textField()
                        .comment("The extension used for view files. <b>NOTE</b>: only used for CakePHP 3.")
                        .bindText(settings.state::cakeTemplateExtension)
                    button("Reset") {
                        println("Reset")
                    }
                }.visibleIf(cake3Enabled.selected)
            }
            group("CakePHP 2") {
                lateinit var cake2Enabled: Cell<JBCheckBox>
                row() {
                    cake2Enabled = checkBox("Enable support for CakePHP 2")
                        .bindSelected(settings.state::cake2Enabled)
                }
                row("App directory") {
                    textField()
                        .comment("This is the top-level directory where your source code is located.")
                        .bindText(settings.state::cake2AppDirectory)
                    button("Reset") {
                        println("Reset")
                    }
                }.visibleIf(cake2Enabled.selected)
                row("Template extension") {
                    textField()
                        .comment("The extension used for view files. By default this is <b>\"ctp\"</b>")
                        .bindText(settings.state::cake2AppDirectory)
                    button("Reset") {
                        println("Reset")
                    }
                }.visibleIf(cake2Enabled.selected)
            }
        }
    }

    override fun isModified(): Boolean {
        return false
    }

    override fun apply() {

    }

    override fun getDisplayName(): String {
        return "Config Form New"
    }

    override fun getId(): String {
        return "chocolateCakePHP.ui.ConfigForm"
    }
}
