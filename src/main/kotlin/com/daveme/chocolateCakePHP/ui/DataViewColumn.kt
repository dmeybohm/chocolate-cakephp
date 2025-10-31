package com.daveme.chocolateCakePHP.ui

import com.daveme.chocolateCakePHP.ChocolateCakePHPBundle
import com.intellij.util.ui.ColumnInfo

class DataViewColumn(name: String) : ColumnInfo<String, String>(name) {
    override fun getName(): String =
        ChocolateCakePHPBundle.message("table.column.dataViewExtension")

    override fun valueOf(viewFile: String): String {
        return viewFile
    }

}