package com.daveme.chocolateCakePHP;

import com.daveme.chocolateCakePHP.ui.FullyQualifiedNameInsertHandler;
import com.daveme.chocolateCakePHP.ui.FullyQualifiedNameTextFieldCompletionProvider;
import com.daveme.chocolateCakePHP.ui.TextFieldListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.jetbrains.php.completion.PhpCompletionUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class EditPluginEntryDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private TextFieldWithCompletion appNamespaceTextField;

    final private Project project;

    private TextFieldListener listener;

    public EditPluginEntryDialog(Project project, String initialValue) {
        this.project = project;

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(e -> onOK());

        buttonCancel.addActionListener(e -> onCancel());

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(
                e -> onCancel(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );

        appNamespaceTextField.requestFocus();
        appNamespaceTextField.setText(initialValue);
    }

    private void onOK() {
        if (listener != null) {
            listener.actionPerformed(appNamespaceTextField.getText());
        }
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    private void createUIComponents() {
        FullyQualifiedNameInsertHandler insertHandler = new FullyQualifiedNameInsertHandler();
        PhpCompletionUtil.PhpFullyQualifiedNameTextFieldCompletionProvider completionProvider =
                new FullyQualifiedNameTextFieldCompletionProvider(project, insertHandler);
        appNamespaceTextField = new TextFieldWithCompletion(
                project,
                completionProvider,
                "",
                true,
                true,
                false,
                false
        );
    }

    public void addTextFieldListener(TextFieldListener listener) {
        this.listener = listener;
    }

    public static EditPluginEntryDialog createDialog(Project project, String initialValue) {
        EditPluginEntryDialog dialog = new EditPluginEntryDialog(project, initialValue);
        dialog.setLocationRelativeTo(null);
        dialog.setTitle("Edit Plugin Namespace");
        dialog.pack();
        return dialog;
    }

}
