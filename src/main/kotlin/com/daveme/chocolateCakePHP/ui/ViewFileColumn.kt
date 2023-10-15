package com.daveme.chocolateCakePHP.ui

import com.intellij.util.ui.ColumnInfo

class ViewFileColumn(name: String) : ColumnInfo<String, String>(name) {
    override fun valueOf(viewFile: String): String {
        return viewFile
    }

}