package com.daveme.chocolateCakePHP;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class ConfigForm implements SearchableConfigurable {
    private final Project project;
    private JPanel topPanel;
    private JCheckBox enableCake3SupportCheckBox;
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
        toggleCake3State(settings.getCake3Enabled());
        appDirectoryTextField.setText(settings.getAppDirectory());
        appNamespaceTextField.setText(settings.getAppNamespace());
        templateExtensionTextField.setText(settings.getCakeTemplateExtension());

        toggleCake2State(settings.getCake2Enabled());
        cake2TemplateExtensionTextField.setText(settings.getCake2TemplateExtension());
        cake2AppDirectoryTextField.setText(settings.getCake2AppDirectory());
    }

    private void applyToSettings(@NotNull Settings settings) {
        SettingsState state = settings.getState();

        state.setCake3Enabled(enableCake3SupportCheckBox.isSelected());
        state.setAppDirectory(appDirectoryTextField.getText());
        state.setCakeTemplateExtension(templateExtensionTextField.getText());
        state.setAppNamespace(appNamespaceTextField.getText());

        state.setCake2Enabled(enableCake2SupportCheckBox.isSelected());
        state.setCake2TemplateExtension(cake2TemplateExtensionTextField.getText());
        state.setCake2AppDirectory(cake2AppDirectoryTextField.getText());

        settings.loadState(state);
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
