package com.daveme.chocolateCakePHP;

import com.intellij.icons.AllIcons;
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
import static com.daveme.chocolateCakePHP.StringsKt.pathNormalize;

class ConfigForm implements SearchableConfigurable {
    private final Project project;
    private JPanel topPanel;
    private JCheckBox enableAutoDetectCheckBox;
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
    private JLabel cake3DetectedLabel;
    private JLabel cake2DetectedLabel;
    private JTextField composerJsonPathTextField;
    private JButton composerJsonPathDefaultButton;

    public ConfigForm(Project project) {
        this.project = project;
    }

    private void loadSettingsToUI(@NotNull Settings settings) {
        composerJsonPathTextField.setText(settings.getComposerJsonPath());

        forceEnableCakePHP3CheckBox.setSelected(settings.getCake3ForceEnabled());
        enableAutoDetectCheckBox.setSelected(settings.getAutoDetect());
        showDetected(cake3DetectedLabel, settings.getCake3Detected());
        updateCake3SettingsVisibility();
        appDirectoryTextField.setText(settings.getAppDirectory());
        appNamespaceTextField.setText(settings.getAppNamespace());
        templateExtensionTextField.setText(settings.getCakeTemplateExtension());

        showDetected(cake2DetectedLabel, settings.getCake2Detected());
        enableCake2SupportCheckBox.setSelected(settings.getCake2ForceEnabled());
        updateCake2SettingsVisibility();
        cake2TemplateExtensionTextField.setText(settings.getCake2TemplateExtension());
        cake2AppDirectoryTextField.setText(settings.getCake2AppDirectory());
    }

    private static void showDetected(JLabel label, boolean detected) {
        label.setText(detected ? "Detected: yes" : "Detected: no");
        label.setIcon(detected ? AllIcons.General.InspectionsOK : AllIcons.General.Error);
    }

    private void applyToSettings(@NotNull Settings settings) {
        SettingsState origState = settings.getState();
        SettingsState newState = copySettingsState(origState);

        newState.setComposerJsonPath(pathNormalize(composerJsonPathTextField.getText()));

        newState.setCake3Enabled(enableAutoDetectCheckBox.isSelected());
        newState.setCake3ForceEnabled(forceEnableCakePHP3CheckBox.isSelected());
        newState.setAppDirectory(pathNormalize(appDirectoryTextField.getText()));
        newState.setCakeTemplateExtension(templateExtensionTextField.getText());
        newState.setAppNamespace(appNamespaceTextField.getText());

        newState.setCake2Enabled(enableCake2SupportCheckBox.isSelected());
        newState.setCake2TemplateExtension(cake2TemplateExtensionTextField.getText());
        newState.setCake2AppDirectory(pathNormalize(cake2AppDirectoryTextField.getText()));

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

        composerJsonPathDefaultButton.addActionListener(e ->
                this.composerJsonPathTextField.setText(defaults.getComposerJsonPath())
        );
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

        enableAutoDetectCheckBox.addActionListener(e -> {
            this.updateCake3SettingsVisibility();
            this.updateCake2SettingsVisibility();
        });

        enableCake2SupportCheckBox.addActionListener(e ->
            this.updateCake2SettingsVisibility()
        );

        return topPanel;
    }

    private void updateCake3SettingsVisibility() {
        boolean visible = enableAutoDetectCheckBox.isSelected() ||
                forceEnableCakePHP3CheckBox.isSelected();
        cake3Panel.setVisible(visible);
        updateCake3SettingEditability();
    }

    private void updateCake2SettingsVisibility() {
        cake2Panel.setVisible(enableAutoDetectCheckBox.isSelected() ||
                enableCake2SupportCheckBox.isSelected());
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
        String oldComposerJsonPath = settings.getComposerJsonPath();

        // Ensure namespace starts with backslash:
        String namespace = appNamespaceTextField.getText();
        if (
                settings.getAutoDetect() &&
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
                !oldAppNamespace.equals(settings.getAppNamespace()) ||
                !oldComposerJsonPath.equals(settings.getComposerJsonPath())) {
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
