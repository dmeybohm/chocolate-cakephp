package com.daveme.chocolateCakePHP;

import com.daveme.chocolateCakePHP.ui.DataViewTableModel;
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

public class DataViewsForm implements SearchableConfigurable {

    private TableView<String> tableView;
    private final Project project;
    private DataViewTableModel dataViewTableModel;
    private JPanel topPanel;
    private JPanel tableViewPanel;
    private JPanel headlinePanelForPlugins;
    private JLabel viewFilesLabel;

    private static final String EDIT_ENTRY_TITLE = "Data View Extension";
    private static final String EDIT_ENTRY_LABEL = "Data view extension";

    public DataViewsForm(Project project) {
        this.project = project;
    }

    @NotNull
    @Override
    public String getId() {
        return "com.daveme.chocolateCakePHP.DataViewForm";
    }

    @Override
    public String getDisplayName() {
        return "CakePHP Data Views";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        Settings settings = Settings.getInstance(project);
        dataViewTableModel = DataViewTableModel.fromSettings(settings);

        this.tableView = new TableView<>(dataViewTableModel);
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
            EditEntryDialog dialog = EditEntryDialog.createDialog(
                    EDIT_ENTRY_TITLE,
                    EDIT_ENTRY_LABEL,
                    project,
                    selected);
            dialog.addTextFieldListener(fieldText -> dataViewTableModel.setValueAt(fieldText, selectedRow, 0));
            dialog.setVisible(true);
        });

        decorator.setAddAction(action -> {
            EditEntryDialog dialog = EditEntryDialog.createDialog(
                    EDIT_ENTRY_TITLE,
                    EDIT_ENTRY_LABEL,
                    project,
                    ""
            );
            dialog.addTextFieldListener(fieldText -> dataViewTableModel.addRow(fieldText));
            dialog.setVisible(true);
        });

        decorator.setRemoveAction(action -> dataViewTableModel.removeRow(tableView.getSelectedRow()));

        decorator.disableUpAction();
        decorator.disableDownAction();

        tableViewPanel.add(decorator.createPanel(), BorderLayout.NORTH);

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
        newState.setDataViewExtensions(dataViewTableModel.getViewFiles());
        settings.loadState(newState);
    }

    @Override
    public void apply() {
        Settings settings = Settings.getInstance(project);
        applyToSettings(settings);
    }

}
