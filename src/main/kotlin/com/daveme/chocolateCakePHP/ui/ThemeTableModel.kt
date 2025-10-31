package com.daveme.chocolateCakePHP.ui

import com.daveme.chocolateCakePHP.ChocolateCakePHPBundle
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.ThemeEntry
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.SortableColumnModel
import javax.swing.RowSorter
import javax.swing.table.TableModel

class ThemeTableModel(
    val themeEntries: MutableList<ThemeEntry>,
    columns: Array<ColumnInfo<ThemeEntry, String>>
) :
    ListTableModel<ThemeEntry>(*columns),
    SortableColumnModel,
    TableModel
{
    override fun setSortable(aBoolean: Boolean) {}

    override fun isSortable(): Boolean {
        return false
    }

    override fun getRowValue(row: Int): ThemeEntry {
        return themeEntries[row]
    }

    override fun getDefaultSortKey(): RowSorter.SortKey? {
        return null
    }

    override fun getRowCount(): Int {
        return themeEntries.size
    }

    override fun getColumnCount(): Int {
        return myColumns.size
    }

    override fun getColumnName(i: Int): String {
        return when (i) {
            0 -> ChocolateCakePHPBundle.message("table.column.themePath")
            1 -> ChocolateCakePHPBundle.message("table.column.assetsPath")
            else -> throw RuntimeException("Invalid column")
        }
    }

    override fun getColumnClass(i: Int): Class<*> {
        return columnInfos[i].columnClass
    }

    override fun isCellEditable(i: Int, i1: Int): Boolean {
        return false
    }

    override fun getValueAt(i: Int, i1: Int): Any {
        return when (i1) {
            0 ->
                themeEntries[i].pluginPath
            1 ->
                themeEntries[i].assetPath
            else ->
                throw RuntimeException("Invalid column")
        }
    }

    override fun setValueAt(o: Any, i: Int, i1: Int) {
        val existingEntry = themeEntries[i]
        when (i1) {
            0 ->
                existingEntry.pluginPath = o.toString()
            1 ->
                existingEntry.assetPath = o.toString()
            else ->
                throw RuntimeException("Invalid column")
        }
        themeEntries[i] = existingEntry
        fireTableCellUpdated(i, i1)
    }

    override fun addRow(item: ThemeEntry?) {
        themeEntries.add(item!!)
        val size = themeEntries.size
        fireTableRowsInserted(size - 1, size - 1)
    }

    override fun removeRow(idx: Int) {
        themeEntries.removeAt(idx)
        fireTableRowsDeleted(idx, idx)
    }

    class ThemePathColumn : ColumnInfo<ThemeEntry, String>("Theme Path") {
        override fun getName(): String =
            ChocolateCakePHPBundle.message("table.column.themePath")

        override fun valueOf(themeEntry: ThemeEntry): String =
            themeEntry.pluginPath
    }

    class AssetsPathColumn : ColumnInfo<ThemeEntry, String>("Assets Path") {
        override fun getName(): String =
            ChocolateCakePHPBundle.message("table.column.assetsPath")

        override fun valueOf(themeEntry: ThemeEntry): String =
            themeEntry.assetPath
    }

    companion object {
        private val myColumns =
            arrayOf(
                ThemePathColumn(),
                AssetsPathColumn(),
            )

        @JvmStatic
        fun fromSettings(settings: Settings): ThemeTableModel {
            val themeEntries = settings.themeConfigs.map {
                ThemeEntry.fromThemeConfig(it)
            }.toMutableList()
            return ThemeTableModel(
                themeEntries,
                myColumns
            )
        }
    }
}