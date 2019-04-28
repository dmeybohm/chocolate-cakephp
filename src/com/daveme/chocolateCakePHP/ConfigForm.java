package com.daveme.chocolateCakePHP;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.completion.PhpCompletionUtil;
import com.jetbrains.php.completion.insert.PhpInsertHandlerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class ConfigForm implements SearchableConfigurable {
    private boolean modified;
    private Project project;
    private JPanel topPanel;
    private JLabel appDirectoryLabel;
    private JButton appDirectoryDefaultButton;
    private TextFieldWithBrowseButton appDirectoryTextField;
    private JLabel appNamespaceLabel;
    private TextFieldWithCompletion appNamespaceTextField;
    private JButton appNamespaceDefault;
    private JTextField cakeTemplateExtensionTextField;
    private Settings originalSettings;

    public ConfigForm(Project project) { this.project = project; }

    private void loadSettingsToUI(Settings settings) {
        appDirectoryTextField.setText(settings.getAppDirectory());
        appNamespaceTextField.setText(settings.getAppNamespace());
//        cakeTemplateExtensionTextField.setText(settings.getCakeTemplateExtension());
    }

    private void copySettingsFromUI(Settings settings) {
        settings.setAppDirectory(appDirectoryTextField.getText());
        settings.setAppNamespace(appNamespaceTextField.getText());
//        settings.setCakeTemplateExtension(cakeTemplateExtensionTextField.getText());
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
        return null;
    }

    @Override
    @Nullable
    public JComponent createComponent() {
        Settings settings = Settings.getInstance(project);
        loadSettingsToUI(settings);
        originalSettings = new Settings(settings);
        return topPanel;
    }

    @Override
    public boolean isModified() {
        Settings newSettings = new Settings();
        copySettingsFromUI(newSettings);
        return !newSettings.equals(originalSettings);
    }

    public void apply() {
        Settings settings = Settings.getInstance(project);
        copySettingsFromUI(settings);
        modified = false;
        this.originalSettings = new Settings(settings);
    }

    private void createUIComponents() {
        ConfigFormInsertHandler insertHandler = new ConfigFormInsertHandler();
        PhpCompletionUtil.PhpFullyQualifiedNameTextFieldCompletionProvider completionProvider =
                new NamespaceCompletionProvider(project, insertHandler);
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

    //
    // Completion provider for the class text fields.
    //
    static class NamespaceCompletionProvider extends PhpCompletionUtil.PhpFullyQualifiedNameTextFieldCompletionProvider {
        private Project project;
        private ConfigFormInsertHandler handler;

        NamespaceCompletionProvider(Project project, ConfigFormInsertHandler handler) {
            this.project = project;
            this.handler = handler;
        }

        @Override
        protected void addCompletionVariants(@NotNull String namespaceName, @NotNull String prefix, @NotNull CompletionResultSet completionResultSet) {
            PhpIndex phpIndex = PhpIndex.getInstance(project);
            PhpCompletionUtil.addSubNamespaces(namespaceName + "\\", completionResultSet, phpIndex, handler);
            completionResultSet.stopHere();
        }
    }

    //
    // Insertion handler for the class text fields.
    //
    static class ConfigFormInsertHandler implements InsertHandler<LookupElement> {
        @Override
        public void handleInsert(@NotNull InsertionContext insertionContext, @NotNull LookupElement lookupElement) {
            Object object = lookupElement.getObject();
            if (object instanceof String) {
                PhpInsertHandlerUtil.insertQualifier(insertionContext, (String) object);
            }
        }
    }
}
