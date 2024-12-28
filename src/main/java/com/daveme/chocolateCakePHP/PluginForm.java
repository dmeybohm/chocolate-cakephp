package com.daveme.chocolateCakePHP;

import com.daveme.chocolateCakePHP.cake.PluginEntry;
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
import java.util.ArrayList;

import static com.daveme.chocolateCakePHP.SettingsKt.*;

public class PluginForm implements SearchableConfigurable {

    private TableView<PluginEntry> tableView;
    private final Project project;
    private PluginTableModel pluginTableModel;
    private JPanel topPanel;
    private JPanel tableViewPanel;
    private JButton pluginPathDefaultButton;
    private JTextField pluginPathTextField;
    private JPanel headlinePanelForPlugins;

    private static final String EDIT_ENTRY_TITLE = "Edit Plugin Namespace";

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
        return "Plugins";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        Settings settings = Settings.getInstance(project);
        pluginTableModel = PluginTableModel.fromSettings(settings);
        pluginPathTextField.setText(settings.getPluginPath());
        final Settings defaults = Settings.getDefaults();

        this.tableView = new TableView<>(pluginTableModel);
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(this.tableView, new ElementProducer<>() {
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
            assert selected != null;
            EditPluginEntryDialog dialog = EditPluginEntryDialog.createDialog(
                    EDIT_ENTRY_TITLE,
                    project,
                    selected.toPluginConfig()
            );
            dialog.setAction(pluginConfig -> {
                setPluginConfig(pluginConfig, selectedRow);
            });
            dialog.setVisible(true);
        });

        decorator.setAddAction(action -> {
            EditPluginEntryDialog dialog = EditPluginEntryDialog.createDialog(
                EDIT_ENTRY_TITLE,
                project,
                defaultPluginConfig()
            );
            dialog.setAction(pluginConfig -> {
                pluginTableModel.addRow(PluginEntry.fromPluginConfig(pluginConfig));
                setPluginConfig(pluginConfig, pluginTableModel.getRowCount() - 1);
            });
            dialog.setVisible(true);
        });

        decorator.setRemoveAction(action -> {
            pluginTableModel.removeRow(tableView.getSelectedRow());
        });

        decorator.disableUpDownActions();

        tableViewPanel.add(decorator.createPanel(), BorderLayout.NORTH);

        pluginPathDefaultButton.addActionListener(e ->
            this.pluginPathTextField.setText(defaults.getPluginPath())
        );

        return topPanel;
    }

    private void setPluginConfig(PluginConfig pluginConfig, int selectedRow) {
        String withBackslash = pluginConfig.getNamespace().startsWith("\\") ?
                pluginConfig.getNamespace() : "\\" + pluginConfig.getNamespace();
        pluginConfig.getPluginPath();
        String templatePath = pluginConfig.getPluginPath();
        pluginTableModel.setValueAt(withBackslash, selectedRow, 0);
        pluginTableModel.setValueAt(templatePath, selectedRow, 1);
    }

    @Override
    public boolean isModified() {
        Settings settings = Settings.getInstance(project);
        Settings newSettings = Settings.fromSettings(settings);
        applyToSettings(newSettings);
        return !newSettings.equals(settings);
    }

    private void applyToSettings(@NotNull Settings settings) {
        SettingsState origState = settings.getState();
        SettingsState newState = copySettingsState(origState);
        newState.setPluginConfigs(Settings.pluginConfigsFromEntryList(
                pluginTableModel.getPluginEntries()));
        newState.setPluginPath(pluginPathTextField.getText());
        // Clear legacy lists:
        newState.setPluginNamespaces(new ArrayList<>());
        settings.loadState(newState);
    }

    @Override
    public void apply() throws ConfigurationException {
        Settings settings = Settings.getInstance(project);
        applyToSettings(settings);
    }

}
