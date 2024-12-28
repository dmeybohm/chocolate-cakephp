package com.daveme.chocolateCakePHP;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Consumer;

public class EditEntryDialog extends DialogWrapper {
    private JPanel contentPane;
    private JLabel fieldLabel;
    private JTextField fieldValueTextField;

    final private Project project;

    private Consumer<String> action;

    public EditEntryDialog(
        @NotNull String fieldLabelText,
        @NotNull String title,
        @Nullable Project project,
        @NotNull String initialValue
    ) {
        super(project, true, true);
        this.project = project;

        setTitle(title);
        fieldLabel.setText(fieldLabelText);
        fieldValueTextField.setText(initialValue);

        init();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return fieldValueTextField;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return contentPane;
    }

    @Override
    protected void doOKAction() {
        if (action != null) {
            action.accept(fieldValueTextField.getText());
        }
        super.doOKAction();
    }

    private void createUIComponents() {
    }

    public void setAction(Consumer<String> consumer) {
        this.action = consumer;
    }
}
