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
import com.jetbrains.php.lang.psi.elements.PhpExpression;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ViewHelperGotoDeclarationHandler implements GotoDeclarationHandler {
    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY;
        }
        if (!PlatformPatterns.psiElement().withParent(FieldReference.class).accepts(psiElement)) {
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
        PhpExpression classReference = fieldReference.getClassReference();
        if (classReference == null) {
            return null;
        }
        PhpType types = classReference.getType();
        String fieldReferenceName = fieldReference.getName();
        if (fieldReferenceName == null) {
            return null;
        }
        if (!Character.isUpperCase(fieldReferenceName.charAt(0))) {
            return null;
        }
        for (String type : types.getTypes()) {
            if (type.contains("#Vthis")) {
                Collection<PhpClass> classes = PsiUtil.getViewHelperClasses(fieldName, psiElement.getProject());
                return classes.toArray(new PsiElement[classes.size()]);
            }
        }
        return PsiElement.EMPTY_ARRAY;
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}
