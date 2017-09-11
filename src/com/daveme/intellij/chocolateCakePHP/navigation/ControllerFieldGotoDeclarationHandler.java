package com.daveme.intellij.chocolateCakePHP.navigation;

import com.daveme.intellij.chocolateCakePHP.util.CakeUtil;
import com.daveme.intellij.chocolateCakePHP.util.PsiUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.FieldReference;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ControllerFieldGotoDeclarationHandler implements GotoDeclarationHandler {
    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        if (!PlatformPatterns.psiElement().withParent(FieldReference.class).accepts(psiElement)) {
            return PsiElement.EMPTY_ARRAY;
        }
        String filename = PsiUtil.getFileNameWithoutExtension(psiElement);
        if (filename == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        String controllerName = CakeUtil.controllerBaseNameFromControllerFileName(filename);
        if (controllerName == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        PsiElement parent = psiElement.getParent();
        if (parent == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        FieldReference fieldReference = (FieldReference)parent;
        String fieldName = fieldReference.getName();
        if (fieldName == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        Collection<PhpClass> classes = PsiUtil.getControllerFieldClasses(fieldName, psiElement.getProject());
        return classes.toArray(new PsiElement[classes.size()]);
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}
