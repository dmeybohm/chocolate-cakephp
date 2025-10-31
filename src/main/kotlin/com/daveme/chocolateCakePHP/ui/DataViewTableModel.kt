package com.daveme.chocolateCakePHP.ui

import com.daveme.chocolateCakePHP.ChocolateCakePHPBundle
import com.daveme.chocolateCakePHP.Settings
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.SortableColumnModel
import javax.swing.RowSorter
import javax.swing.table.TableModel

class DataViewTableModel private constructor(
    val viewFiles: MutableList<String>,
    columns: Array<ColumnInfo<String, String>>
) :
    ListTableModel<String>(*columns),
    SortableColumnModel,
    TableModel {

    override fun setSortable(aBoolean: Boolean) {}

    override fun isSortable(): Boolean {
        return false
    }

    override fun getRowValue(row: Int): String {
        return viewFiles[row]
    }

    override fun getDefaultSortKey(): RowSorter.SortKey? {
        return null
    }

    override fun getRowCount(): Int {
        return viewFiles.size
    }

    override fun getColumnCount(): Int {
        return 1
    }

    override fun getColumnName(i: Int): String {
        return when (i) {
            0 -> ChocolateCakePHPBundle.message("table.column.dataViewExtension")
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
        return viewFiles[i]
    }

    override fun setValueAt(o: Any, i: Int, i1: Int) {
        viewFiles[i] = o as String
        fireTableCellUpdated(i, i1)
    }

    override fun addRow(item: String?) {
        viewFiles.add(item!!)
        val size = viewFiles.size
        fireTableRowsInserted(size - 1, size - 1)
    }

    override fun removeRow(idx: Int) {
        viewFiles.removeAt(idx)
        fireTableRowsDeleted(idx, idx)
    }

    companion object {
        @JvmStatic
        fun fromSettings(settings: Settings): DataViewTableModel {
            val columnText = ChocolateCakePHPBundle.message("table.column.dataViewExtension")
            val columns = arrayOf<ColumnInfo<String, String>>(
                DataViewColumn(columnText)
            )
            return DataViewTableModel(settings.dataViewExtensions.toMutableList(), columns)
        }
    }

}