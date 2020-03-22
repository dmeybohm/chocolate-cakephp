package com.daveme.chocolateCakePHP;

import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

public class PluginTableModel implements SortableColumnModel, TableModel {

    private Settings settings;
    private ColumnInfo[] columnInfos = new ColumnInfo[] { new NamespaceColumn("Namespace") };

    @Override
    public ColumnInfo[] getColumnInfos() {
        return columnInfos;
    }

    @Override
    public void setSortable(boolean aBoolean) {

    }

    @Override
    public boolean isSortable() {
        return false;
    }

    @Override
    public Object getRowValue(int row) {
        return this.settings.getPluginEntries().get(row);
    }

    @Nullable
    @Override
    public RowSorter.SortKey getDefaultSortKey() {
        return null;
    }

    private static Object[] columns = new Object[] { "Namespace" };

    private PluginTableModel(Settings settings) {
        this.settings = settings;
    }

    public static PluginTableModel fromSettings(Settings settings) {
        return new PluginTableModel(settings);
    }

    @Override
    public int getRowCount() {
        return this.settings.getPluginEntries().size();
    }

    @Override
    public int getColumnCount() {
        return 1;
    }

    @Override
    public String getColumnName(int i) {
        return columnInfos[i].getName();
    }

    @Override
    public Class<?> getColumnClass(int i) {
        return columnInfos[i].getColumnClass();
    }

    @Override
    public boolean isCellEditable(int i, int i1) {
        return false;
    }

    @Override
    public Object getValueAt(int i, int i1) {
        return this.settings.getPluginEntries().get(i).getNamespace();
    }

    @Override
    public void setValueAt(Object o, int i, int i1) {
        this.settings.getPluginEntries().set(i, (PluginEntry)o);
    }

    @Override
    public void addTableModelListener(TableModelListener tableModelListener) {

    }

    @Override
    public void removeTableModelListener(TableModelListener tableModelListener) {

    }
}
