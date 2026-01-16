package com.daveme.chocolateCakePHP;

import com.daveme.chocolateCakePHP.cake.PluginEntry;
import com.daveme.chocolateCakePHP.cake.ThemeEntry;
import com.daveme.chocolateCakePHP.ui.PluginTableModel;
import com.daveme.chocolateCakePHP.ui.ThemeTableModel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.ui.ElementProducer;
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexServiceKt;
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndexServiceKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.daveme.chocolateCakePHP.SettingsKt.*;

public class PluginForm implements SearchableConfigurable {

    private TableView<PluginEntry> pluginTableView;
    private TableView<ThemeEntry> themeTableView;
    private final Project project;
    private PluginTableModel pluginTableModel;
    private ThemeTableModel themeTableModel;
    private JPanel topPanel;
    private JPanel pluginTableViewPanel;
    private JPanel headlinePanelForPlugins;
    private JPanel themesPanel;
    private JPanel themeConfigPanel;

    private static final String EDIT_PLUGIN_CONFIG_TITLE = "Edit Plugin Config";
    private static final String EDIT_THEME_CONFIG_TITLE = "Edit Theme Config";

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
        themeTableModel = ThemeTableModel.fromSettings(settings);
        final Settings defaults = Settings.getDefaults(project);

        this.pluginTableView = new TableView<>(pluginTableModel);
        ToolbarDecorator pluginDecorator = ToolbarDecorator.createDecorator(
            this.pluginTableView,
            new ElementProducer<>() {
                @Override
                public PluginEntry createElement() {
                    return null;
                }

                @Override
                public boolean canCreateElement() {
                    return true;
                }
            }
        );

        setupPluginTableToolbar(pluginDecorator);

        this.themeTableView = new TableView<>(themeTableModel);
        ToolbarDecorator themeTableDecorator = ToolbarDecorator.createDecorator(
            this.themeTableView,
            new ElementProducer<>() {
                @Override
                public ThemeEntry createElement() {
                    return null;
                }

                @Override
                public boolean canCreateElement() {
                    return true;
                }
            }
        );
        setupThemeTableToolbar(themeTableDecorator);

        pluginTableViewPanel.add(pluginDecorator.createPanel(), BorderLayout.NORTH);
        themeConfigPanel.add(themeTableDecorator.createPanel(), BorderLayout.NORTH);

        return topPanel;
    }

    private void setupPluginTableToolbar(ToolbarDecorator decorator) {
        decorator.setEditAction(action -> {
            PluginEntry selected = pluginTableView.getSelectedObject();
            if (selected == null) {
                return;
            }
            final int selectedRow = pluginTableView.getSelectedRow();
            EditPluginEntryDialog dialog= new EditPluginEntryDialog(
                    project,
                    EDIT_PLUGIN_CONFIG_TITLE,
                    selected.toPluginConfig()
            );
            dialog.setAction(pluginConfig -> {
                setPluginConfig(pluginConfig, selectedRow);
            });
            dialog.show();
        });

        decorator.setAddAction(action -> {
            EditPluginEntryDialog dialog = new EditPluginEntryDialog(
                project,
                    EDIT_PLUGIN_CONFIG_TITLE,
                defaultPluginConfig()
            );
            dialog.setAction(pluginConfig -> {
                pluginTableModel.addRow(PluginEntry.fromPluginConfig(pluginConfig));
                setPluginConfig(pluginConfig, pluginTableModel.getRowCount() - 1);
            });
            dialog.show();
        });

        decorator.setRemoveAction(action -> {
            pluginTableModel.removeRow(pluginTableView.getSelectedRow());
        });

        decorator.disableUpDownActions();
    }

    private void setupThemeTableToolbar(ToolbarDecorator themeTableDecorator) {
        themeTableDecorator.setEditAction(action -> {
            ThemeEntry selected = themeTableView.getSelectedObject();
            if (selected == null) {
                return;
            }
            final int selectedRow = themeTableView.getSelectedRow();
            EditThemeEntryDialog dialog = new EditThemeEntryDialog(
                    project,
                    EDIT_PLUGIN_CONFIG_TITLE,
                    selected.toThemeConfig()
            );
            dialog.setAction(themeConfig -> {
                setThemeConfig(themeConfig, selectedRow);
            });
            dialog.show();
        });

        themeTableDecorator.setAddAction(action -> {
            EditThemeEntryDialog dialog = new EditThemeEntryDialog(
                    project,
                    EDIT_THEME_CONFIG_TITLE,
                    defaultThemeConfig()
            );
            dialog.setAction(themeConfig -> {
                themeTableModel.addRow(ThemeEntry.fromThemeConfig(themeConfig));
                setThemeConfig(themeConfig, themeTableView.getRowCount() - 1);
            });
            dialog.show();
        });

        themeTableDecorator.setRemoveAction(action -> {
            themeTableModel.removeRow(themeTableView.getSelectedRow());
        });

        themeTableDecorator.disableUpDownActions();

    }

    private void setPluginConfig(PluginConfig pluginConfig, int selectedRow) {
        String withBackslash = pluginConfig.getNamespace().startsWith("\\")
                ? pluginConfig.getNamespace()
                : "\\" + pluginConfig.getNamespace();
        String templatePath = pluginConfig.getPluginPath();
        String sourcePath = pluginConfig.getSrcPath();
        String assetPath = pluginConfig.getAssetPath();

        pluginTableModel.setValueAt(withBackslash, selectedRow, 0);
        pluginTableModel.setValueAt(templatePath, selectedRow, 1);
        pluginTableModel.setValueAt(sourcePath, selectedRow, 2);
        pluginTableModel.setValueAt(assetPath, selectedRow, 3);
    }

    private void setThemeConfig(ThemeConfig themeConfig, int selectedRow) {
        String themePath = themeConfig.getPluginPath();
        String assetPath = themeConfig.getAssetPath();

        themeTableModel.setValueAt(themePath, selectedRow, 0);
        themeTableModel.setValueAt(assetPath, selectedRow, 1);
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
        newState.setThemeConfigs(Settings.themeConfigsFromEntryList(
                themeTableModel.getThemeEntries()));
        // Clear legacy lists:
        newState.setPluginNamespaces(new ArrayList<>());
        settings.loadState(newState);
    }

    @Override
    public void apply() throws ConfigurationException {
        Settings settings = Settings.getInstance(project);

        // Capture previous plugin configs
        List<PluginConfig> oldPluginConfigs = settings.getPluginConfigs();
        List<ThemeConfig> oldThemeConfigs = settings.getThemeConfigs();

        applyToSettings(settings);

        // Check if plugin/theme configs changed
        if (settings.getEnabled()) {
            List<PluginConfig> newPluginConfigs = settings.getPluginConfigs();
            List<ThemeConfig> newThemeConfigs = settings.getThemeConfigs();

            if (!oldPluginConfigs.equals(newPluginConfigs) ||
                !oldThemeConfigs.equals(newThemeConfigs)) {
                requestIndexRebuild();
            }
        }
    }

    private void requestIndexRebuild() {
        FileBasedIndex fileIndex = FileBasedIndex.getInstance();
        fileIndex.requestRebuild(ViewFileIndexServiceKt.getVIEW_FILE_INDEX_KEY());
        fileIndex.requestRebuild(ViewVariableIndexServiceKt.getVIEW_VARIABLE_INDEX_KEY());
    }

}
