package com.daveme.chocolateCakePHP.ui

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.makeRelativeToProjectDir
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import javax.swing.JComponent

class PluginForm(val project: Project) : SearchableConfigurable {
    override fun createComponent(): JComponent {
        val settings = Settings.getInstance(project)
        return panel {
            group("CakePHP 3+ Plugins") {
                row("Plugin path") {
                    textFieldWithBrowseButton(
                        project = project,
                        fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                        fileChosen = { file -> makeRelativeToProjectDir(project, file) }
                    )
                        .comment("Plugins will be check underneath this directory for autocomplete.")
                        .bindText(settings.state::pluginPath)
                        .gap(RightGap.SMALL)
                        .resizableColumn()
                        .horizontalAlign(HorizontalAlign.FILL)
                    button("Default") {
                        println("Reset")
                    }
                }

            }
        }
    }

    override fun isModified(): Boolean {
        return false
    }

    override fun apply() {

    }

    override fun getDisplayName(): String {
        return "Cake 3+ Plugins"
    }

    override fun getId(): String {
        return "chocolatecakephp.ui.PluginForm"
    }
}
