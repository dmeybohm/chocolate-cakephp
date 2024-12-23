package com.daveme.chocolateCakePHP.ui

import com.daveme.chocolateCakePHP.cake.PluginEntry
import com.intellij.util.ui.ColumnInfo

class TemplatePathColumn(name: String) :
    ColumnInfo<PluginEntry, String>(name) {

    override fun valueOf(pluginEntry: PluginEntry): String {
        return pluginEntry.templatePath ?: ""
    }
}