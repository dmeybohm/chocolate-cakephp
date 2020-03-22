package com.daveme.chocolateCakePHP;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.table.TableView;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.completion.PhpCompletionUtil;
import com.jetbrains.php.completion.insert.PhpInsertHandlerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

class ConfigForm implements SearchableConfigurable {
    private Project project;
    private JPanel topPanel;
    private TableView<PluginEntry> pluginTableView;
    private JCheckBox enableCake3SupportCheckBox;
    private JButton appNamespaceDefaultButton;
    private JTextField templateExtensionTextField;
    private JButton templateExtensionDefaultButton;
    private JCheckBox enableCake2SupportCheckBox;
    private TextFieldWithBrowseButton cake2AppDirectoryTextField;
    private JButton cake2AppDirectoryDefaultButton;
    private JTextField cake2TemplateExtensionTextField;
    private JButton cake2TemplateExtensionDefaultButton;
    private TextFieldWithBrowseButton pluginPathTextField;
    private JButton pluginPathDefaultButton;
    private TextFieldWithCompletion appNamespaceTextField;
    private JTextField appDirectoryTextField;
    private JButton appDirectoryDefaultButton;
    private JPanel cake3Panel;
    private JPanel cake2Panel;
    private JPanel pluginsPanel;
    private JTable pluginsEnabledTable;
    private PluginTableModel pluginTableModel;
    private Settings originalSettings;
     
    public ConfigForm(Project project) {
        this.project = project;
    }

    private void loadSettingsToUI(Settings settings) {
        toggleCake3State(settings.getCake3Enabled());
        appDirectoryTextField.setText(settings.getAppDirectory());
        appNamespaceTextField.setText(settings.getAppNamespace());
        templateExtensionTextField.setText(settings.getCakeTemplateExtension());
        pluginPathTextField.setText(settings.getPluginPath());

        toggleCake2State(settings.getCake2Enabled());
        cake2TemplateExtensionTextField.setText(settings.getCakeTemplateExtension());
        cake2AppDirectoryTextField.setText(settings.getCake2AppDirectory());
        pluginsEnabledTable.setModel(pluginTableModel);
    }

    private ListModel<PluginEntry> listModelFromPluginEntries(List<PluginEntry> pluginEntries) {
        DefaultListModel<PluginEntry> listModel = new DefaultListModel<>();
        listModel.clear();
        listModel.addAll(pluginEntries);
        return listModel;
    }

    private void copySettingsFromUI(Settings settings) {
        settings.setCake3Enabled(enableCake3SupportCheckBox.isSelected());
        settings.setAppDirectory(appDirectoryTextField.getText());
        settings.setCakeTemplateExtension(templateExtensionTextField.getText());
        settings.setPluginPath(pluginPathTextField.getText());
        settings.setAppNamespace(appNamespaceTextField.getText());

        settings.setCake2Enabled(enableCake2SupportCheckBox.isSelected());
        settings.setCake2TemplateExtension(cake2TemplateExtensionTextField.getText());
        settings.setCake2AppDirectory(cake2AppDirectoryTextField.getText());
    }

    private List<PluginEntry> pluginEntryListFromListModel(ListModel<PluginEntry> listModel) {
        List<PluginEntry> pluginEntries = new ArrayList<>();
        for (int i = 0; i < listModel.getSize(); i++) {
            pluginEntries.add(listModel.getElementAt(i));
        }
        return pluginEntries;
    }

    @Override
    @NotNull
    @Nls
    public String getId() {
        return "ChocolateCakePHP.ConfigForm";
    }

    @Nls
    @Nullable
    public String getDisplayName() {
        return null;
    }

    @Override
    @Nullable
    public JComponent createComponent() {
        Settings settings = Settings.getInstance(project);
        pluginTableModel = PluginTableModel.fromSettings(settings);
        loadSettingsToUI(settings);
        originalSettings = new Settings(settings);

        appNamespaceDefaultButton.addActionListener(e ->
                this.appNamespaceTextField.setText(Settings.DefaultAppNamespace)
        );
        appDirectoryDefaultButton.addActionListener(e ->
                this.appDirectoryTextField.setText(Settings.DefaultAppDirectory)
        );
        templateExtensionDefaultButton.addActionListener(e ->
                this.templateExtensionTextField.setText(Settings.DefaultCakeTemplateExtension)
        );
        pluginPathDefaultButton.addActionListener(e ->
                this.pluginPathTextField.setText(Settings.DefaultPluginPath)
        );
        cake2AppDirectoryDefaultButton.addActionListener(e ->
                this.cake2AppDirectoryTextField.setText(Settings.DefaultCake2AppDirectory)
        );
        cake2TemplateExtensionDefaultButton.addActionListener(e ->
                this.cake2TemplateExtensionTextField.setText(Settings.DefaultCake2TemplateExtension)
        );

        // Toggle enabled/disabled for panels based on checkboxes:
        enableCake3SupportCheckBox.addActionListener(e ->
            this.toggleCake3State(enableCake3SupportCheckBox.isSelected())
        );

        enableCake2SupportCheckBox.addActionListener(e ->
            this.toggleCake2State(enableCake2SupportCheckBox.isSelected())
        );

        return topPanel;
    }

    private void toggleCake3State(boolean enabled) {
        cake3Panel.setVisible(enabled);
        enableCake3SupportCheckBox.setSelected(enabled);
    }

    private void toggleCake2State(boolean enabled) {
        cake2Panel.setVisible(enabled);
        enableCake2SupportCheckBox.setSelected(enabled);
    }

    @Override
    public boolean isModified() {
        Settings newSettings = new Settings();
        copySettingsFromUI(newSettings);
        return !newSettings.equals(originalSettings);
    }

    public void apply() {
        Settings settings = Settings.getInstance(project);
        copySettingsFromUI(settings);
        this.originalSettings = new Settings(settings);
    }

    private void createUIComponents() {
        ConfigFormInsertHandler insertHandler = new ConfigFormInsertHandler();
        try {
            SwingUtilities.invokeAndWait(() -> {
                PhpCompletionUtil.PhpFullyQualifiedNameTextFieldCompletionProvider completionProvider =
                        new NamespaceCompletionProvider(project, insertHandler);
                appNamespaceTextField = new TextFieldWithCompletion(
                        project,
                        completionProvider,
                        "",
                        true,
                        true,
                        true,
                        true
                );
            });
        } catch (InterruptedException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    //
    // Completion provider for the class text fields.
    //
    static class NamespaceCompletionProvider extends PhpCompletionUtil.PhpFullyQualifiedNameTextFieldCompletionProvider {
        private Project project;
        private ConfigFormInsertHandler handler;

        NamespaceCompletionProvider(Project project, ConfigFormInsertHandler handler) {
            this.project = project;
            this.handler = handler;
        }

        @Override
        protected void addCompletionVariants(@NotNull String namespaceName, @NotNull String prefix, @NotNull CompletionResultSet completionResultSet) {
            PhpIndex phpIndex = PhpIndex.getInstance(project);
            PhpCompletionUtil.addSubNamespaces(namespaceName + "\\", completionResultSet, phpIndex, handler);
            completionResultSet.stopHere();
        }
    }

    //
    // Insertion handler for the class text fields.
    //
    static class ConfigFormInsertHandler implements InsertHandler<LookupElement> {
        @Override
        public void handleInsert(@NotNull InsertionContext insertionContext, @NotNull LookupElement lookupElement) {
            Object object = lookupElement.getObject();
            if (object instanceof String) {
                PhpInsertHandlerUtil.insertQualifier(insertionContext, (String) object);
            }
        }
    }
}
