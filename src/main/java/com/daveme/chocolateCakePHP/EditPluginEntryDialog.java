package com.daveme.chocolateCakePHP;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.daveme.chocolateCakePHP.cake.PluginEntry;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

import static com.daveme.chocolateCakePHP.SettingsKt.effectivePluginName;

public class EditPluginEntryDialog extends DialogWrapper {
    private JPanel contentPane;
    private JLabel pluginNameLabel;
    private JTextField pluginNameTextField;
    private JLabel namespaceLabel;
    private JTextField namespaceTextField;
    private JTextField pluginPathTextField;
    private JLabel sourcePathLabel;
    private JTextField sourcePathTextField;
    private JLabel assetsPathLabel;
    private JTextField assetsPathTextField;

    final private Project project;
    private final List<PluginEntry> existingEntries;
    private final int editingIndex;
    private final boolean initialCakeTwo;

    private Consumer<PluginConfig> action;

    public EditPluginEntryDialog(
        @Nullable Project project,
        @NotNull String title,
        @NotNull PluginConfig initialPluginConfig,
        @NotNull List<PluginEntry> existingEntries,
        int editingIndex
    ) {
        super(project, true, true);

        this.project = project;
        this.existingEntries = existingEntries;
        this.editingIndex = editingIndex;

        PluginEntry initialEntry = PluginEntry.fromPluginConfig(initialPluginConfig);
        this.initialCakeTwo = initialEntry.getCakeTwo();
        pluginNameTextField.setText(initialEntry.getPluginName());
        namespaceTextField.setText(initialEntry.getNamespace());
        pluginPathTextField.setText(initialEntry.getPluginPath());
        sourcePathTextField.setText(initialEntry.getSrcPath());
        assetsPathTextField.setText(initialEntry.getAssetPath());

        setTitle(title);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return contentPane;
    }

    @Override
    protected void doOKAction() {
        PluginConfig newConfig = getPluginConfig();
        String newName = effectivePluginName(newConfig);

        if (!newName.isEmpty()) {
            for (int i = 0; i < existingEntries.size(); i++) {
                if (i == editingIndex) continue;
                PluginEntry entry = existingEntries.get(i);
                String existingName = effectivePluginName(entry.toPluginConfig());
                if (newName.equals(existingName)) {
                    setErrorText("A plugin with the name '" + newName + "' already exists.", pluginNameTextField);
                    return;
                }
            }
        }

        if (action != null) {
            action.accept(newConfig);
        }
        super.doOKAction();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return pluginNameTextField;
    }

    public JPanel getContentPane() {
        return contentPane;
    }

    private void createUIComponents() {
    }

    public void setAction(Consumer<PluginConfig> consumer) {
        this.action = consumer;
    }

    public PluginConfig getPluginConfig() {
        if (editingIndex < 0 && project != null) {
            PluginConfig classified = Settings.getInstance(project).classifyLegacyPluginConfig(
                new LegacyPluginConfig(
                    pluginNameTextField.getText(),
                    namespaceTextField.getText(),
                    sourcePathTextField.getText(),
                    pluginPathTextField.getText(),
                    assetsPathTextField.getText()
                )
            );
            if (classified != null) {
                return classified;
            }
        }
        return new PluginEntry(
            pluginNameTextField.getText(),
            namespaceTextField.getText(),
            pluginPathTextField.getText(),
            assetsPathTextField.getText(),
            sourcePathTextField.getText(),
            editingIndex >= 0 && initialCakeTwo
        ).toPluginConfig();
    }
}
