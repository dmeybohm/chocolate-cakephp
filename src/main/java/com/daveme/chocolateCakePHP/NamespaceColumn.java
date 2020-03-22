package com.daveme.chocolateCakePHP;

import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;

public class NamespaceColumn extends com.intellij.util.ui.ColumnInfo<PluginEntry, String> {

    public NamespaceColumn(String name) {
        super(name);
    }

    @Nullable
    @Override
    public String valueOf(PluginEntry pluginEntry) {
        return pluginEntry.getNamespace();
    }
}
