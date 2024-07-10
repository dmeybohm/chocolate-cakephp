package com.daveme.chocolateCakePHP;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.daveme.chocolateCakePHP.SettingsKt.copySettingsState;

class ConfigForm implements SearchableConfigurable {
    private final Project project;
    private JPanel topPanel;
    private JCheckBox enableAutoDetectCake3CheckBox;
    private JCheckBox forceEnableCakePHP3CheckBox;
    private JButton appNamespaceDefaultButton;
    private JTextField templateExtensionTextField;
    private JButton templateExtensionDefaultButton;
    private JCheckBox enableCake2SupportCheckBox;
    private JTextField cake2AppDirectoryTextField;
    private JButton cake2AppDirectoryDefaultButton;
    private JTextField cake2TemplateExtensionTextField;
    private JButton cake2TemplateExtensionDefaultButton;
    private JTextField appNamespaceTextField;
    private JTextField appDirectoryTextField;
    private JButton appDirectoryDefaultButton;
    private JPanel cake3Panel;
    private JPanel cake2Panel;
    private JLabel cakeThreePlusTitle;
    private JLabel cakeTwoTitle;

    public ConfigForm(Project project) {
        this.project = project;
    }

    private void loadSettingsToUI(@NotNull Settings settings) {
        forceEnableCakePHP3CheckBox.setSelected(settings.getCake3ForceEnabled());
        enableAutoDetectCake3CheckBox.setSelected(settings.getCake3Enabled());
        updateCake3SettingsVisibility();
        appDirectoryTextField.setText(settings.getAppDirectory());
        appNamespaceTextField.setText(settings.getAppNamespace());
        templateExtensionTextField.setText(settings.getCakeTemplateExtension());

        enableCake2SupportCheckBox.setSelected(settings.getCake2Enabled());
        updateCake2SettingsVisibility();
        cake2TemplateExtensionTextField.setText(settings.getCake2TemplateExtension());
        cake2AppDirectoryTextField.setText(settings.getCake2AppDirectory());
    }

    private void applyToSettings(@NotNull Settings settings) {
        SettingsState origState = settings.getState();
        SettingsState newState = copySettingsState(origState);

        newState.setCake3Enabled(enableAutoDetectCake3CheckBox.isSelected());
        newState.setCake3ForceEnabled(forceEnableCakePHP3CheckBox.isSelected());
        newState.setAppDirectory(appDirectoryTextField.getText());
        newState.setCakeTemplateExtension(templateExtensionTextField.getText());
        newState.setAppNamespace(appNamespaceTextField.getText());

        newState.setCake2Enabled(enableCake2SupportCheckBox.isSelected());
        newState.setCake2TemplateExtension(cake2TemplateExtensionTextField.getText());
        newState.setCake2AppDirectory(cake2AppDirectoryTextField.getText());

        settings.loadState(newState);
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
        return "Chocolate CakePHP";
    }

    @Override
    @Nullable
    public JComponent createComponent() {
        Settings settings = Settings.getInstance(project);

        loadSettingsToUI(settings);
        final Settings defaults = Settings.getDefaults();

        appNamespaceDefaultButton.addActionListener(e ->
                this.appNamespaceTextField.setText(defaults.getAppNamespace())
        );
        appDirectoryDefaultButton.addActionListener(e ->
                this.appDirectoryTextField.setText(defaults.getAppDirectory())
        );
        templateExtensionDefaultButton.addActionListener(e ->
                this.templateExtensionTextField.setText(defaults.getCakeTemplateExtension())
        );
        cake2AppDirectoryDefaultButton.addActionListener(e ->
                this.cake2AppDirectoryTextField.setText(defaults.getCake2AppDirectory())
        );
        cake2TemplateExtensionDefaultButton.addActionListener(e ->
                this.cake2TemplateExtensionTextField.setText(defaults.getCake2TemplateExtension())
        );

        forceEnableCakePHP3CheckBox.addActionListener(e ->
            this.updateCake3SettingsVisibility()
        );

        enableAutoDetectCake3CheckBox.addActionListener(e ->
            this.updateCake3SettingsVisibility()
        );

        enableCake2SupportCheckBox.addActionListener(e ->
            this.updateCake2SettingsVisibility()
        );

        return topPanel;
    }

    private void updateCake3SettingsVisibility() {
        boolean visible = enableAutoDetectCake3CheckBox.isSelected() ||
                forceEnableCakePHP3CheckBox.isSelected();
        cake3Panel.setVisible(visible);
        enableAutoDetectCake3CheckBox.setEnabled(!forceEnableCakePHP3CheckBox.isSelected());
        updateAppNamespaceEditability();
    }

    private void updateCake2SettingsVisibility() {
        cake2Panel.setVisible(enableCake2SupportCheckBox.isSelected());
    }

    private void updateAppNamespaceEditability() {
        appNamespaceTextField.setEnabled(forceEnableCakePHP3CheckBox.isSelected());
        appNamespaceDefaultButton.setEnabled(forceEnableCakePHP3CheckBox.isSelected());
    }

    @Override
    public boolean isModified() {
        Settings originalSettings = Settings.getInstance(project);
        Settings newSettings = Settings.fromSettings(originalSettings);

        applyToSettings(newSettings);
        return !newSettings.equals(originalSettings);
    }

    public void apply() {
        Settings settings = Settings.getInstance(project);

        // Ensure namespace starts with backslash:
        String namespace = appNamespaceTextField.getText();
        if (settings.getCake3Enabled() &&
                !namespace.isEmpty() &&
                !namespace.startsWith("\\")) {
            String newNamespace = "\\" + namespace;
            appNamespaceTextField.setText(newNamespace);
        }

        applyToSettings(settings);
    }

}
