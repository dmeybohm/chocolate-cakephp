package com.daveme.chocolateCakePHP;

import com.daveme.chocolateCakePHP.ui.PluginConfigListener;
import com.daveme.chocolateCakePHP.ui.TextFieldListener;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.*;

public class EditPluginEntryDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JLabel namespaceLabel;
    private JTextField namespaceTextField;
    private JTextField templatePathTextField;

    final private Project project;

    private PluginConfigListener listener;

    public EditPluginEntryDialog(
            Project project,
            String initialNamespace,
            String initialTemplatePath
    ) {
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

        namespaceTextField.requestFocus();
        namespaceTextField.setText(initialNamespace);
        templatePathTextField.setText(initialTemplatePath);
    }

    private void onOK() {
        if (listener != null) {
            String templatePath = templatePathTextField.getText().isEmpty() ? null
                    : templatePathTextField.getText();
            listener.actionPerformed(new PluginConfig(
                    namespaceTextField.getText(),
                    templatePath
            ));
        }
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    private void createUIComponents() {
    }

    public void addPluginConfigListener(PluginConfigListener listener) {
        this.listener = listener;
    }

    public static EditPluginEntryDialog createDialog(
            String title,
            Project project,
            String initialNamespace,
            String initialTemplatePath
    ) {
        EditPluginEntryDialog dialog = new EditPluginEntryDialog(
                project,
                initialNamespace,
                initialTemplatePath
        );
        dialog.setLocationRelativeTo(null);
        dialog.setTitle(title);
        dialog.pack();
        return dialog;
    }

}
