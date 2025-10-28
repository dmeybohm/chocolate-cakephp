package com.daveme.chocolateCakePHP.ui

import com.daveme.chocolateCakePHP.cake.PluginEntry
import com.daveme.chocolateCakePHP.ChocolateCakePHPBundle
import com.daveme.chocolateCakePHP.Settings
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.SortableColumnModel
import javax.swing.RowSorter
import javax.swing.table.TableModel

@Suppress("UNCHECKED_CAST")
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
        return when (i) {
            0 -> ChocolateCakePHPBundle.message("table.column.pluginNamespace")
            1 -> ChocolateCakePHPBundle.message("table.column.pluginPath")
            2 -> ChocolateCakePHPBundle.message("table.column.sourcePath")
            3 -> ChocolateCakePHPBundle.message("table.column.assetsPath")
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

    abstract class TranslatedColumn(name: String) :
        ColumnInfo<PluginEntry, String>(name)

    class NamespaceColumn(name: String) : TranslatedColumn(name) {
        override fun valueOf(pluginEntry: PluginEntry): String =
            pluginEntry.namespace
    }

    class PluginPathColumn(name: String) : TranslatedColumn(name) {
        override fun valueOf(pluginEntry: PluginEntry): String =
            pluginEntry.pluginPath
    }

    class SourcePathColumn(name: String) : TranslatedColumn(name) {
        override fun getName(): String =
            ChocolateCakePHPBundle.message("table.column.sourcePath")

        override fun valueOf(pluginEntry: PluginEntry): String =
            pluginEntry.srcPath
    }

    class AssetsPathColumn(name: String) : TranslatedColumn(name) {
        override fun getName(): String =
            ChocolateCakePHPBundle.message("table.column.assetsPath")

        override fun valueOf(pluginEntry: PluginEntry): String =
            pluginEntry.assetPath
    }

    companion object {
        private val myColumns by lazy {
            val namespace = ChocolateCakePHPBundle.message("table.column.pluginNamespace")
            val pluginPath = ChocolateCakePHPBundle.message("table.column.pluginPath")
            val sourcePath = ChocolateCakePHPBundle.message("table.column.sourcePath")
            val assetsPath = ChocolateCakePHPBundle.message("table.column.assetsPath")

            arrayOf(
                NamespaceColumn(namespace),
                PluginPathColumn(pluginPath),
                SourcePathColumn(sourcePath),
                AssetsPathColumn(assetsPath),
            )
        }

        @JvmStatic
        fun fromSettings(settings: Settings): PluginTableModel {
            val pluginEntries = settings.pluginConfigs.map {
                PluginEntry.fromPluginConfig(it)
            }.toMutableList()
            return PluginTableModel(
                pluginEntries,
                myColumns as Array<ColumnInfo<PluginEntry, String>>
            )
        }
    }

}