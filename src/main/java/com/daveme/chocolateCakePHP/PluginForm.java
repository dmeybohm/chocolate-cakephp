package com.daveme.chocolateCakePHP;

import com.daveme.chocolateCakePHP.ui.PluginTableModel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ElementProducer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.swing.*;
import java.awt.*;

public class PluginForm implements SearchableConfigurable {

    private TableView<PluginEntry> tableView;
    private final Project project;
    private PluginTableModel pluginTableModel;
    private JPanel topPanel;
    private JPanel tableViewPanel;
    private JButton pluginPathDefaultButton;
    private JTextField pluginPathTextField;
    private JPanel headlinePanelForPlugins;

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
        pluginTableModel = PluginTableModel.createFromSettings(settings);
        pluginPathTextField.setText(settings.getPluginPath());

        this.tableView = new TableView<>(pluginTableModel);
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
            EditPluginEntryDialog dialog = EditPluginEntryDialog.createDialog(project, selected);
            dialog.addTextFieldListener(newEntry -> {
                String namespace = newEntry.getNamespace();
                String withBackslash = namespace.startsWith("\\") ? namespace : "\\" + namespace;
                PluginEntry newPluginEntry = new PluginEntry(withBackslash, newEntry.getPath());
                pluginTableModel.setValueAt(newPluginEntry, selectedRow, 0);
            });
            dialog.setVisible(true);
        });

        decorator.setAddAction(action -> {
            EditPluginEntryDialog dialog = EditPluginEntryDialog.createDialog(
                    project,
                    new PluginEntry("", "")
            );
            dialog.addTextFieldListener(newEntry -> {
                String namespace = newEntry.getNamespace();
                String withBackslash = namespace.startsWith("\\") ? namespace : "\\" + namespace;
                PluginEntry newPluginEntry = new PluginEntry(withBackslash, newEntry.getPath());
                pluginTableModel.addRow(newPluginEntry);
            });
            dialog.setVisible(true);
        });

        decorator.setRemoveAction(action -> {
            pluginTableModel.removeRow(tableView.getSelectedRow());
        });

        decorator.disableUpAction();
        decorator.disableDownAction();

        tableViewPanel.add(decorator.createPanel(), BorderLayout.NORTH);

        pluginPathDefaultButton.addActionListener(e ->
                this.pluginPathTextField.setText(Settings.DefaultPluginPath)
        );

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
