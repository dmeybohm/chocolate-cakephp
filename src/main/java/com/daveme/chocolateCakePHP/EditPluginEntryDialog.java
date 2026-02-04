package com.daveme.chocolateCakePHP;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Consumer;

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

    private Consumer<PluginConfig> action;

    public EditPluginEntryDialog(
        @Nullable Project project,
        @NotNull String title,
        @NotNull PluginConfig initialPluginConfig
    ) {
        super(project, true, true);

        this.project = project;

        pluginNameTextField.setText(initialPluginConfig.getPluginName());
        namespaceTextField.setText(initialPluginConfig.getNamespace());
        pluginPathTextField.setText(initialPluginConfig.getPluginPath());
        sourcePathTextField.setText(initialPluginConfig.getSrcPath());
        assetsPathTextField.setText(initialPluginConfig.getAssetPath());

        setTitle(title);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return contentPane;
    }

    @Override
    protected void doOKAction() {
        if (action != null) {
            action.accept(getPluginConfig());
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
        return new PluginConfig(
            pluginNameTextField.getText(),
            namespaceTextField.getText(),
            sourcePathTextField.getText(),
            pluginPathTextField.getText(),
            assetsPathTextField.getText()
        );
    }
}
