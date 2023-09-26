package com.daveme.chocolateCakePHP;

import com.daveme.chocolateCakePHP.ui.FullyQualifiedNameInsertHandler;
import com.daveme.chocolateCakePHP.ui.FullyQualifiedNameTextFieldCompletionProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.jetbrains.php.completion.PhpCompletionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

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
    private TextFieldWithCompletion appNamespaceTextField;
    private JTextField appDirectoryTextField;
    private JButton appDirectoryDefaultButton;
    private JPanel cake3Panel;
    private JPanel cake2Panel;
     
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

    private void copySettingsFromUI(@NotNull Settings settings) {
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
    public void disposeUIResources() {
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
        copySettingsFromUI(newSettings);
        return !newSettings.equals(originalSettings);
    }

    public void apply() {
        Settings settings = Settings.getInstance(project);
        copySettingsFromUI(settings);
    }

    private void createUIComponents() {
        System.out.println("project = " + (project == null));
        FullyQualifiedNameInsertHandler insertHandler = new FullyQualifiedNameInsertHandler();
        if (!SwingUtilities.isEventDispatchThread()) {
            System.out.println("delayed application manager create");
            ApplicationManager.getApplication().invokeAndWait(() -> setupHandler(insertHandler), ModalityState.any());
        } else {
            setupHandler(insertHandler);
        }
    }

    private void setupHandler(FullyQualifiedNameInsertHandler insertHandler) {
        PhpCompletionUtil.PhpFullyQualifiedNameTextFieldCompletionProvider completionProvider =
                new FullyQualifiedNameTextFieldCompletionProvider(project, insertHandler);
        System.out.println("project is null in setuphandler: " + (project == null));
        appNamespaceTextField = new TextFieldWithCompletion(
                project,
                completionProvider,
                "",
                true,
                true,
                true,
                true
        );
    }
}
