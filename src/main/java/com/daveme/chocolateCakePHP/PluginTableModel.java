package com.daveme.chocolateCakePHP;

import javax.swing.table.DefaultTableModel;
import java.util.List;

public class PluginTableModel extends DefaultTableModel {

    private static Object[] columns = new Object[] { "Namespace" };

    private PluginTableModel(Object[][] model, Object[] columns) {
        super(model, columns);
    }

    public static PluginTableModel fromSettings(Settings settings) {
        return new PluginTableModel(convertSettingsToTableModel(settings), columns);
    }

    private static Object[][] convertSettingsToTableModel(Settings settings) {
        List<PluginEntry> pluginEntries = settings.getPluginEntries();
        Object[][] result = new Object[pluginEntries.size()][2];

        for (int i = 0; i < pluginEntries.size(); i++) {
            PluginEntry pluginEntry = pluginEntries.get(i);
            result[i] = new Object[] { pluginEntry.getNamespace() };
        }
        return result;
    }

}
