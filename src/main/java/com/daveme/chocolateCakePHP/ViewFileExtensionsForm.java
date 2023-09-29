package com.daveme.chocolateCakePHP;

import com.daveme.chocolateCakePHP.ui.ViewFileTableModel;
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

public class ViewFileExtensionsForm implements SearchableConfigurable {

    private TableView<String> tableView;
    private final Project project;
    private ViewFileTableModel viewFileTableModel;
    private JPanel topPanel;
    private JPanel tableViewPanel;
    private JPanel headlinePanelForPlugins;

    public ViewFileExtensionsForm(Project project) {
        this.project = project;
    }

    @NotNull
    @Override
    public String getId() {
        return "com.daveme.chocolateCakePHP.Form";
    }

    @Override
    public String getDisplayName() {
        return "CakePHP View File Extensions";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        Settings settings = Settings.getInstance(project);
        viewFileTableModel = ViewFileTableModel.fromSettings(settings);
        final Settings defaults = Settings.getDefaults();

        this.tableView = new TableView<>(viewFileTableModel);
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(this.tableView, new ElementProducer<>() {
            @Override
            public String createElement() {
                return null;
            }

            @Override
            public boolean canCreateElement() {
                return true;
            }
        });

        decorator.setEditAction(action -> {
            String selected = tableView.getSelectedObject();
            final int selectedRow = tableView.getSelectedRow();
            EditPluginEntryDialog dialog = EditPluginEntryDialog.createDialog(project, selected);
            dialog.addTextFieldListener(fieldText -> {
                viewFileTableModel.setValueAt(fieldText, selectedRow, 0);
            });
            dialog.setVisible(true);
        });

        decorator.setAddAction(action -> {
            EditPluginEntryDialog dialog = EditPluginEntryDialog.createDialog(project, "");
            dialog.addTextFieldListener(fieldText -> {
                viewFileTableModel.addRow(fieldText);
            });
            dialog.setVisible(true);
        });

        decorator.setRemoveAction(action -> {
            viewFileTableModel.removeRow(tableView.getSelectedRow());
        });

        decorator.disableUpAction();
        decorator.disableDownAction();

        tableViewPanel.add(decorator.createPanel(), BorderLayout.NORTH);

        return topPanel;
    }

    @Override
    public boolean isModified() {
        Settings settings = Settings.getInstance(project);
        Settings newSettings = Settings.fromSettings(settings);
        copySettingsFromUI(newSettings);
        return !newSettings.equals(settings);
    }

    private void copySettingsFromUI(@NotNull Settings settings) {
        SettingsState state = settings.getState();
        state.setViewFileExtensions(viewFileTableModel.getViewFiles());
        settings.loadState(state);
    }

    @Override
    public void apply() throws ConfigurationException {
        Settings settings = Settings.getInstance(project);
        copySettingsFromUI(settings);
    }

}