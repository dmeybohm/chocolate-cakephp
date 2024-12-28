package com.daveme.chocolateCakePHP;

import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.*;
import java.util.function.Consumer;

public class EditPluginEntryDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JLabel namespaceLabel;
    private JTextField namespaceTextField;
    private JTextField templatePathTextField;

    final private Project project;

    private Consumer<PluginConfig> action;

    public EditPluginEntryDialog(
        Project project,
        PluginConfig initialPluginConfig
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
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );

        namespaceTextField.requestFocus();
        namespaceTextField.setText(initialPluginConfig.getNamespace());
        templatePathTextField.setText(initialPluginConfig.getPluginPath());
        // todo srcPath
        // todo assetPath
    }

    private void onOK() {
        if (action != null) {
            action.accept(new PluginConfig(
                namespaceTextField.getText(),
                templatePathTextField.getText(),
                // todo update
                "",
                ""
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

    public void setAction(Consumer<PluginConfig> consumer) {
        this.action = consumer;
    }

    public static EditPluginEntryDialog createDialog(
            String title,
            Project project,
            PluginConfig initialPluginConfig
    ) {
        EditPluginEntryDialog dialog = new EditPluginEntryDialog(
            project,
            initialPluginConfig
        );
        dialog.setLocationRelativeTo(null);
        dialog.setTitle(title);
        dialog.pack();
        return dialog;
    }

}
