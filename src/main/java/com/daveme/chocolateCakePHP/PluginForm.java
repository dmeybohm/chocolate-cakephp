package com.daveme.chocolateCakePHP;

import com.daveme.chocolateCakePHP.ui.PluginTableModel;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ElementProducer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.swing.*;
import java.util.Collection;

public class PluginForm implements SearchableConfigurable {

    private TableView<PluginEntry> tableView;
    private final Project project;
    private PluginTableModel pluginTableModel;
    private JPanel topPanel;
    private JPanel tableViewPanel;
    private JButton pluginPathDefaultButton;
    private JTextField pluginPathTextField;
    private JLabel pluginPathLabel;

    public PluginForm(Project project) {
        this.project = project;
    }

    @NotNull
    @Override
    public String getId() {
        return "com.daveme.chocolateCakePHP.PluginForm";
    }

    @Override
    public String getDisplayName() {
        return "CakePHP 3 Plugins";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        Settings settings = Settings.getInstance(project);
        pluginTableModel = PluginTableModel.fromSettings(settings);
        pluginPathTextField.setText(settings.getPluginPath());

        this.tableView = new TableView<>();
        tableView.setModel(pluginTableModel);
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(this.tableView, new ElementProducer<PluginEntry>() {
            @Override
            public PluginEntry createElement() {
                return null;
            }

            @Override
            public boolean canCreateElement() {
                return true;
            }
        });

        decorator.setEditAction(action -> {
            PluginEntry selected = tableView.getSelectedObject();
            final int selectedRow = tableView.getSelectedRow();
            EditPluginEntryDialog dialog = EditPluginEntryDialog.createDialog(project, selected.getNamespace());
            dialog.addTextFieldListener(fieldText -> {
                String withBackslash = fieldText.startsWith("\\") ? fieldText : "\\" + fieldText;
                PluginEntry newPluginEntry = new PluginEntry(withBackslash);
                pluginTableModel.setValueAt(newPluginEntry, selectedRow, 0);
            });
            dialog.setVisible(true);
        });

        decorator.setAddAction(action -> {
            EditPluginEntryDialog dialog = EditPluginEntryDialog.createDialog(project, "");
            dialog.addTextFieldListener(fieldText -> {
                String withBackslash = fieldText.startsWith("\\") ? fieldText : "\\" + fieldText;
                PluginEntry newPluginEntry = new PluginEntry(withBackslash);
                pluginTableModel.addRow(newPluginEntry);
            });
            dialog.setVisible(true);
        });

        decorator.setRemoveAction(action -> {
            pluginTableModel.removeRow(tableView.getSelectedRow());
        });

        decorator.disableUpAction();
        decorator.disableDownAction();

        tableViewPanel.add(decorator.createPanel());

        return topPanel;
    }

    @Override
    public boolean isModified() {
        Settings settings = Settings.getInstance(project);
        return !settings.getPluginEntries().equals(pluginTableModel.getPluginEntries()) ||
                !settings.getPluginPath().equals(pluginPathTextField.getText());
    }

    @Override
    public void apply() throws ConfigurationException {
        Settings settings = Settings.getInstance(project);
        settings.setPluginEntries(pluginTableModel.getPluginEntries());
        settings.setPluginPath(pluginPathTextField.getText());
    }

}
