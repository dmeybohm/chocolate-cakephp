package com.daveme.chocolateCakePHP.ui

import com.daveme.chocolateCakePHP.PluginEntry
import com.intellij.util.ui.ColumnInfo

class ViewFileColumn(name: String) : ColumnInfo<String, String>(name) {
    override fun valueOf(viewFile: String): String {
        return viewFile
    }

}