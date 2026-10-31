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
    private static final String CAKE_THREE_VERSION = "CakePHP 3+";
    private static final String CAKE_TWO_VERSION = "CakePHP 2";

    private JPanel contentPane;
    private JComboBox<String> versionComboBox;
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
        boolean cakeTwo = editingIndex >= 0
                ? initialEntry.getCakeTwo()
                : project != null && Settings.getInstance(project).pluginPathIsCakeTwo(initialEntry.getPluginPath());
        versionComboBox.addItem(CAKE_THREE_VERSION);
        versionComboBox.addItem(CAKE_TWO_VERSION);
        versionComboBox.setSelectedItem(cakeTwo ? CAKE_TWO_VERSION : CAKE_THREE_VERSION);
        versionComboBox.addActionListener(e -> updateFieldVisibility());
        updateFieldVisibility();

        pluginNameTextField.setText(initialEntry.getPluginName());
        namespaceTextField.setText(initialEntry.getNamespace());
        pluginPathTextField.setText(initialEntry.getPluginPath());
        sourcePathTextField.setText(initialEntry.getSrcPath().isEmpty() ? "src" : initialEntry.getSrcPath());
        assetsPathTextField.setText(initialEntry.getAssetPath());

        setTitle(title);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return contentPane;
    }

    private boolean isCakeTwoSelected() {
        return CAKE_TWO_VERSION.equals(versionComboBox.getSelectedItem());
    }

    private void updateFieldVisibility() {
        boolean cakeThree = !isCakeTwoSelected();
        namespaceLabel.setVisible(cakeThree);
        namespaceTextField.setVisible(cakeThree);
        sourcePathLabel.setVisible(cakeThree);
        sourcePathTextField.setVisible(cakeThree);
    }

    @Override
    protected void doOKAction() {
        if (isCakeTwoSelected() && pluginNameTextField.getText().isBlank()) {
            String fromPath = pluginPathTextField.getText().replaceAll("/+$", "");
            pluginNameTextField.setText(fromPath.substring(fromPath.lastIndexOf('/') + 1));
            if (pluginNameTextField.getText().isBlank()) {
                setErrorText("A CakePHP 2 plugin needs a name.", pluginNameTextField);
                return;
            }
        }

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
        return new PluginEntry(
            pluginNameTextField.getText(),
            namespaceTextField.getText(),
            pluginPathTextField.getText(),
            assetsPathTextField.getText(),
            sourcePathTextField.getText(),
            isCakeTwoSelected()
        ).toPluginConfig();
    }
}
