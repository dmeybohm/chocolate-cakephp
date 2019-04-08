package com.daveme.intellij.chocolateCakePHP;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class ConfigForm implements SearchableConfigurable {
    private boolean modified;
    private Project project;
    private JPanel topPanel;
    private JLabel appDirectoryLabel;
    private JButton appDirectoryDefaultButton;
    private TextFieldWithBrowseButton appDirectoryTextField;
    private JButton appNamespaceDefaultButton;
    private JLabel appNamespaceLabel;
    private JTextField appNamespaceTextField;
    private JTextField cakeTemplateExtensionTextField;
    private JButton cakeTemplateExtensionDefaultButton;
    private JLabel cakeTemplateExtensionLabel;
    private Settings originalSettings;

    public ConfigForm(Project project) {
        this.project = project;
    }

    private void loadSettingsToUI(Settings settings) {
        appDirectoryTextField.setText(settings.getAppDirectory());
        appNamespaceTextField.setText(settings.getAppNamespace());
        cakeTemplateExtensionTextField.setText(settings.getCakeTemplateExtension());
    }

    private void copySettingsFromUI(Settings settings) {
        settings.setAppDirectory(appDirectoryTextField.getText());
        settings.setAppNamespace(appNamespaceTextField.getText());
        settings.setCakeTemplateExtension(cakeTemplateExtensionTextField.getText());
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
        Settings settings = Settings.Companion.getInstance(project);
        loadSettingsToUI(settings);
        originalSettings = new Settings(settings);
        return topPanel;
    }

    @Override
    public boolean isModified() {
        Settings newSettings = new Settings();
        copySettingsFromUI(newSettings);
        return !newSettings.equals(originalSettings);
    }

    public void apply() {
        Settings settings = Settings.Companion.getInstance(project);
        copySettingsFromUI(settings);
        modified = false;
        this.originalSettings = new Settings(settings);
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }
}
