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

import static com.daveme.chocolateCakePHP.SettingsKt.copySettingsState;

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
    private static final String EDIT_ENTRY_LABEL = "Plugin namespace";

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
        final Settings defaults = Settings.getDefaults();

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
            assert selected != null;
            EditEntryDialog dialog = EditEntryDialog.createDialog(
                    EDIT_ENTRY_TITLE,
                    EDIT_ENTRY_LABEL,
                    project,
                    selected.getNamespace()
            );
            dialog.addTextFieldListener(fieldText -> {
                String withBackslash = fieldText.startsWith("\\") ? fieldText : "\\" + fieldText;
                PluginEntry newPluginEntry = new PluginEntry(withBackslash);
                pluginTableModel.setValueAt(newPluginEntry, selectedRow, 0);
            });
            dialog.setVisible(true);
        });

        decorator.setAddAction(action -> {
            EditEntryDialog dialog = EditEntryDialog.createDialog(
                    EDIT_ENTRY_TITLE,
                    EDIT_ENTRY_LABEL,
                    project,
                    ""
            );
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

        tableViewPanel.add(decorator.createPanel(), BorderLayout.NORTH);

        pluginPathDefaultButton.addActionListener(e ->
                this.pluginPathTextField.setText(defaults.getPluginPath())
        );

        return topPanel;
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
        newState.setPluginNamespaces(Settings.pluginNamespaceListFromEntryList(pluginTableModel.getPluginEntries()));
        newState.setPluginPath(pluginPathTextField.getText());
        settings.loadState(newState);
    }

    @Override
    public void apply() throws ConfigurationException {
        Settings settings = Settings.getInstance(project);
        applyToSettings(settings);
    }

}
