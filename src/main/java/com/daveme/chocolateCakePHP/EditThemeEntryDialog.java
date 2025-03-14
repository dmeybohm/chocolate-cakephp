package com.daveme.chocolateCakePHP;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Consumer;

public class EditThemeEntryDialog extends DialogWrapper {
    private JPanel contentPane;
    private JLabel assetsPathLabel;
    private JLabel themePathLabel;
    private JTextField themePathTextField;
    private JTextField assetsPathTextField;

    final private Project project;

    private Consumer<ThemeConfig> action;

    public EditThemeEntryDialog(
        @Nullable Project project,
        @NotNull String title,
        @NotNull ThemeConfig initialThemeConfig
    ) {
        super(project, true, true);

        this.project = project;

        themePathTextField.setText(initialThemeConfig.getThemePath());
        assetsPathTextField.setText(initialThemeConfig.getAssetPath());

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
            action.accept(getThemeConfig());
        }
        super.doOKAction();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return themePathTextField;
    }

    public JPanel getContentPane() {
        return contentPane;
    }

    private void createUIComponents() {
    }

    public void setAction(Consumer<ThemeConfig> consumer) {
        this.action = consumer;
    }

    public ThemeConfig getThemeConfig() {
        return new ThemeConfig(
            themePathTextField.getText(),
            assetsPathTextField.getText()
        );
    }
}
