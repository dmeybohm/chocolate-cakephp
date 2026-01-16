package com.daveme.chocolateCakePHP;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.FileBasedIndex;
import com.daveme.chocolateCakePHP.view.viewfileindex.ViewFileIndexServiceKt;
import com.daveme.chocolateCakePHP.view.viewvariableindex.ViewVariableIndexServiceKt;
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
        enableAutoDetectCake3CheckBox.setSelected(settings.getCake3AutoDetect());
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
        final Settings defaults = Settings.getDefaults(project);

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
        updateCake3SettingEditability();
    }

    private void updateCake2SettingsVisibility() {
        cake2Panel.setVisible(enableCake2SupportCheckBox.isSelected());
    }

    private void updateCake3SettingEditability() {
        var cake3SettingsEnabled = forceEnableCakePHP3CheckBox.isSelected();
        appNamespaceTextField.setEnabled(cake3SettingsEnabled);
        appNamespaceDefaultButton.setEnabled(cake3SettingsEnabled);
        appDirectoryTextField.setEnabled(cake3SettingsEnabled);
        appDirectoryDefaultButton.setEnabled(cake3SettingsEnabled);
        templateExtensionTextField.setEnabled(cake3SettingsEnabled);
        templateExtensionDefaultButton.setEnabled(cake3SettingsEnabled);
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

        // Capture previous state for comparison - track INDIVIDUAL enable flags
        boolean wasCake2Enabled = settings.getCake2Enabled();
        boolean wasCake3Enabled = settings.getCake3Enabled();
        boolean wasCake3ForceEnabled = settings.getCake3ForceEnabled();
        String oldTemplateExt = settings.getCakeTemplateExtension();
        String oldCake2TemplateExt = settings.getCake2TemplateExtension();
        String oldAppDir = settings.getAppDirectory();
        String oldCake2AppDir = settings.getCake2AppDirectory();
        String oldAppNamespace = settings.getAppNamespace();

        // Ensure namespace starts with backslash:
        String namespace = appNamespaceTextField.getText();
        if (
                settings.getCake3AutoDetect() &&
                !namespace.isEmpty() &&
                !namespace.startsWith("\\")
        ) {
            String newNamespace = "\\" + namespace;
            appNamespaceTextField.setText(newNamespace);
        }

        applyToSettings(settings);

        // Check if any indexing-relevant settings changed
        boolean needsReindex = false;

        // Check individual enable flags changed
        if (wasCake2Enabled != settings.getCake2Enabled() ||
            wasCake3Enabled != settings.getCake3Enabled() ||
            wasCake3ForceEnabled != settings.getCake3ForceEnabled()) {
            needsReindex = true;
        }

        // Other settings changed (only check if enabled)
        if (settings.getEnabled()) {
            if (!oldTemplateExt.equals(settings.getCakeTemplateExtension()) ||
                !oldCake2TemplateExt.equals(settings.getCake2TemplateExtension()) ||
                !oldAppDir.equals(settings.getAppDirectory()) ||
                !oldCake2AppDir.equals(settings.getCake2AppDirectory()) ||
                !oldAppNamespace.equals(settings.getAppNamespace())) {
                needsReindex = true;
            }
        }

        if (needsReindex) {
            requestIndexRebuild();
        }
    }

    private void requestIndexRebuild() {
        FileBasedIndex fileIndex = FileBasedIndex.getInstance();
        fileIndex.requestRebuild(ViewFileIndexServiceKt.getVIEW_FILE_INDEX_KEY());
        fileIndex.requestRebuild(ViewVariableIndexServiceKt.getVIEW_VARIABLE_INDEX_KEY());
    }

}
