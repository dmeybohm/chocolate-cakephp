package com.daveme.chocolateCakePHP.ui

import com.daveme.chocolateCakePHP.cake.PluginEntry
import com.daveme.chocolateCakePHP.Settings
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.SortableColumnModel
import javax.swing.RowSorter
import javax.swing.table.TableModel

class PluginTableModel private constructor(
    val pluginEntries: MutableList<PluginEntry>,
    columns: Array<ColumnInfo<PluginEntry, String>>
) :
    ListTableModel<PluginEntry>(*columns),
    SortableColumnModel,
    TableModel {

    override fun setSortable(aBoolean: Boolean) {}

    override fun isSortable(): Boolean {
        return false
    }

    override fun getRowValue(row: Int): PluginEntry {
        return pluginEntries[row]
    }

    override fun getDefaultSortKey(): RowSorter.SortKey? {
        return null
    }

    override fun getRowCount(): Int {
        return pluginEntries.size
    }

    override fun getColumnCount(): Int {
        return 2
    }

    override fun getColumnName(i: Int): String {
        return columnInfos[i].name
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
                pluginEntries[i].namespace
            1 ->
                pluginEntries[i].pluginPath ?: ""
            else ->
                throw RuntimeException("Invalid column")
        }
    }

    override fun setValueAt(o: Any, i: Int, i1: Int) {
        val existingEntry = pluginEntries[i]
        when (i1) {
            0 ->
                existingEntry.namespace = o.toString()
            1 ->
                existingEntry.pluginPath = o.toString()
            else ->
                throw RuntimeException("Invalid column")
        }
        pluginEntries[i] = existingEntry
        fireTableCellUpdated(i, i1)
    }

    override fun addRow(item: PluginEntry?) {
        pluginEntries.add(item!!)
        val size = pluginEntries.size
        fireTableRowsInserted(size - 1, size - 1)
    }

    override fun removeRow(idx: Int) {
        pluginEntries.removeAt(idx)
        fireTableRowsDeleted(idx, idx)
    }

    companion object {
        private val myColumns =
            arrayOf(
                NamespaceColumn("Namespace"),
                PluginPathColumn("Plugin Path")
            )

        @JvmStatic
        fun fromSettings(settings: Settings): PluginTableModel {
            return PluginTableModel(settings.pluginEntries.toMutableList(), myColumns)
        }
    }

}