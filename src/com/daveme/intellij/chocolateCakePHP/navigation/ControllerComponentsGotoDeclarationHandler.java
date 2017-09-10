package com.daveme.intellij.chocolateCakePHP.navigation;

import com.daveme.intellij.chocolateCakePHP.util.PsiUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.php.lang.psi.elements.Variable;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Created by dmeybohm on 9/10/17.
 */
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
            String componentName = psiElement.getText() + "Component";
            PhpIndex phpIndex = PhpIndex.getInstance(psiElement.getProject());
            Collection<PhpClass> componentClasses = phpIndex.getClassesByFQN(componentName);
            return componentClasses.toArray(new PsiElement[componentClasses.size()]);
        }
        if (text.contains("$helpers")) {
            String helperName = psiElement.getText() + "Helper";
            PhpIndex phpIndex = PhpIndex.getInstance(psiElement.getProject());
            Collection<PhpClass> helperClasses = phpIndex.getClassesByFQN(helperName);
            return helperClasses.toArray(new PsiElement[helperClasses.size()]);
        }
        return PsiElement.EMPTY_ARRAY;
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}
