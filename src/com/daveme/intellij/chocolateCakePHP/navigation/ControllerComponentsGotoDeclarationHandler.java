package com.daveme.intellij.chocolateCakePHP.navigation;

import com.daveme.intellij.chocolateCakePHP.util.PsiUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.Nullable;

public class ControllerComponentsGotoDeclarationHandler implements GotoDeclarationHandler {
    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        if (!PlatformPatterns
                .psiElement(StringLiteralExpression.class)
                .withLanguage(PhpLanguage.INSTANCE)
                .accepts(psiElement.getContext())
                ) {
            return PsiElement.EMPTY_ARRAY;
        }
        Field field = (Field)PsiUtil.findParent(psiElement, Field.class);
        if (field == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        String text = field.getText();
        if (text.contains("$components")) {
            return PsiUtil.getClassesAsArray(psiElement, psiElement.getText() + "Component");
        }
        if (text.contains("$helpers")) {
            return PsiUtil.getClassesAsArray(psiElement, psiElement.getText() + "Helper");
        }
        return PsiElement.EMPTY_ARRAY;
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}
