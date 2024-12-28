package com.daveme.chocolateCakePHP;

import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.*;
import java.util.function.Consumer;

public class EditEntryDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JLabel fieldLabel;
    private JTextField fieldValueTextField;

    final private Project project;

    private Consumer<String> action;

    public EditEntryDialog(String fieldLabelText, Project project, String initialValue) {
        this.project = project;

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        fieldLabel.setText(fieldLabelText);

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

        fieldValueTextField.requestFocus();
        fieldValueTextField.setText(initialValue);
    }

    private void onOK() {
        if (action != null) {
            action.accept(fieldValueTextField.getText());
        }
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    private void createUIComponents() {
    }

    public void setAction(Consumer<String> consumer) {
        this.action = consumer;
    }

    public static EditEntryDialog createDialog(String title, String fieldLabel, Project project, String initialValue) {
        EditEntryDialog dialog = new EditEntryDialog(fieldLabel, project, initialValue);
        dialog.setLocationRelativeTo(null);
        dialog.setTitle(title);
        dialog.pack();
        return dialog;
    }

}
