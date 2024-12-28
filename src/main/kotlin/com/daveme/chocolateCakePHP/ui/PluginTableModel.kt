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
    TableModel
{
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
        return myColumns.size
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
                pluginEntries[i].pluginPath
            2 ->
                pluginEntries[i].srcPath
            3 ->
                pluginEntries[i].assetPath
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
            2 ->
                existingEntry.srcPath = o.toString()
            3 ->
                existingEntry.assetPath = o.toString()
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

    class NamespaceColumn : ColumnInfo<PluginEntry, String>("Plugin Namespace") {
        override fun valueOf(pluginEntry: PluginEntry): String =
            pluginEntry.namespace
    }

    class PluginPathColumn : ColumnInfo<PluginEntry, String>("Plugin Path") {
        override fun valueOf(pluginEntry: PluginEntry): String =
            pluginEntry.pluginPath
    }

    class SourcePathColumn : ColumnInfo<PluginEntry, String>("Source Path") {
        override fun valueOf(pluginEntry: PluginEntry): String =
            pluginEntry.srcPath
    }

    class AssetsPathColumn : ColumnInfo<PluginEntry, String>("Assets Path") {
        override fun valueOf(pluginEntry: PluginEntry): String =
            pluginEntry.assetPath
    }

    companion object {
        private val myColumns =
            arrayOf(
                NamespaceColumn(),
                PluginPathColumn(),
                SourcePathColumn(),
                AssetsPathColumn(),
            )

        @JvmStatic
        fun fromSettings(settings: Settings): PluginTableModel {
            return PluginTableModel(
                settings.pluginEntries.toMutableList(),
                myColumns
            )
        }
    }

}